#!/usr/bin/env python3
"""Host-side policy coordinator for Evaluation Mode evidence capture.

Device interaction belongs to the AndroidX UI Automator test runner. This
module validates manifests, gates capture authorization, writes sidecars, and
separates capture facts from human review.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import struct
import sys
import uuid
import zlib
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from enum import Enum
from pathlib import Path
from typing import Any

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from tools.workspace_paths import find_workspace_root


PACKAGE = "app.komikku.dev"
STATUSES = {"planned", "entered", "verified", "captured", "blocked", "not-applicable"}
REVIEW_STATUSES = {"not-reviewed", "needs-review", "approved", "rejected"}
PRIVACY_REVIEW_STATUSES = {"pending", "passed", "failed"}
CLEANUP_STATUSES = {"pending", "passed", "failed", "blocked"}
CROP_STATUSES = {"not-cropped", "cropped-reviewed", "cropped-unreviewed"}
MAX_SESSION_TTL_SECONDS = 300
SHA256_HEX_LENGTH = 64
BLOCKER_TYPES = {
    "missing-fixture",
    "unsafe-mutation",
    "unavailable-fixture",
    "privacy-defect",
    "nonexistent",
    "retired",
    "code-audit-gap",
    "evidence-pending",
}
FIXTURE_BLOCKER_TYPES = {"missing-fixture", "unavailable-fixture", "unsafe-mutation"}


class CoordinatorError(ValueError):
    exit_code = 2


class PrivacyGateError(CoordinatorError):
    exit_code = 3


class ManifestError(CoordinatorError):
    exit_code = 4


class SidecarError(CoordinatorError):
    exit_code = 5


class SessionState(str, Enum):
    NOT_STARTED = "not-started"
    BLOCKED = "blocked"
    VERIFIED = "verified"
    EXPIRED = "expired"
    ENDED = "ended"


def configure_console_encoding() -> None:
    for name in ("stdout", "stderr"):
        stream = getattr(sys, name, None)
        reconfigure = getattr(stream, "reconfigure", None)
        if callable(reconfigure):
            reconfigure(encoding="utf-8", errors="replace")


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise CoordinatorError(f"Could not read JSON file {path}: {exc}") from exc


def validate_fixture_contract(route: dict[str, Any]) -> None:
    """Require catalog references without duplicating mutable fixture safety state."""
    blocker_type = route.get("blockerType")
    fixture_id = route.get("fixtureId")
    checkpoint_ids = route.get("checkpointIds")
    name = route.get("name", "<unnamed>")

    if blocker_type in FIXTURE_BLOCKER_TYPES:
        if not isinstance(fixture_id, str) or not fixture_id.strip():
            raise ManifestError(f"Route {name} requires a non-empty fixtureId.")
        if not isinstance(checkpoint_ids, list) or not checkpoint_ids or not all(
            isinstance(checkpoint_id, str) and checkpoint_id.strip() for checkpoint_id in checkpoint_ids
        ):
            raise ManifestError(f"Route {name} requires non-empty checkpointIds.")
        if len(checkpoint_ids) != len(set(checkpoint_ids)):
            raise ManifestError(f"Route {name} checkpointIds must be unique.")
        if "fixture" in route:
            raise ManifestError(f"Route {name} must not duplicate fixture safety fields.")
    elif blocker_type in {"nonexistent", "retired"} and (fixture_id is not None or checkpoint_ids is not None):
        raise ManifestError(f"Route {name} cannot define a fixture for {blocker_type} functionality.")


def validate_manifest(manifest: dict[str, Any]) -> None:
    routes = manifest.get("routes")
    if not isinstance(routes, list):
        raise ManifestError("Manifest requires a routes array.")
    names: set[str] = set()
    for route in routes:
        if not isinstance(route, dict):
            raise ManifestError("Every route must be an object.")
        name = route.get("name")
        status = route.get("status")
        if not isinstance(name, str) or not name:
            raise ManifestError("Every route requires a non-empty name.")
        if name in names:
            raise ManifestError(f"Duplicate route name: {name}")
        names.add(name)
        if status not in STATUSES:
            raise ManifestError(f"Route {name} has invalid status: {status!r}")
        if status in {"blocked", "not-applicable"} and not route.get("blockingReason"):
            raise ManifestError(f"Route {name} requires blockingReason for status {status}.")
        if status in {"blocked", "not-applicable"}:
            blocker_type = route.get("blockerType")
            if blocker_type not in BLOCKER_TYPES:
                raise ManifestError(
                    f"Route {name} requires a valid blockerType for status {status}."
                )
            for field in ("owner", "exitCondition", "cleanupMethod"):
                if not isinstance(route.get(field), str) or not route[field].strip():
                    raise ManifestError(f"Route {name} requires {field} for status {status}.")
            if route.get("capture", {}).get("allowed"):
                raise ManifestError(
                    f"Route {name} cannot allow capture while status is {status}."
                )
        validate_fixture_contract(route)
        if status == "verified" and not route.get("verification"):
            raise ManifestError(f"Route {name} cannot be verified without verification evidence.")
        if status == "captured" and not route.get("sidecarPath"):
            raise ManifestError(f"Route {name} cannot be captured without sidecarPath.")
        if route.get("capture", {}).get("allowed") and status not in {"planned", "entered", "verified", "captured", "blocked"}:
            raise ManifestError(f"Route {name} has an invalid capture/status combination.")


def validate_manifest_fixture_catalog(manifest: dict[str, Any], catalog_path: Path) -> dict[str, Any]:
    """Require every declared route fixture to match the private fixture catalog."""
    try:
        from tools.evidence_fixture_registry import FixtureRegistryError, load_fixtures

        fixture_ids = {route["fixtureId"] for route in manifest["routes"] if route.get("fixtureId")}
        fixtures = {
            fixture.id: fixture
            for fixture in load_fixtures(catalog_path, fixture_ids=fixture_ids)
        }
    except (FixtureRegistryError, OSError) as exc:
        raise ManifestError(f"Could not validate fixture catalog: {exc}") from exc

    for route in manifest["routes"]:
        fixture_id = route.get("fixtureId")
        if fixture_id is None:
            continue
        if fixture_id not in fixtures:
            raise ManifestError(
                f"Route {route['name']} declares fixture {fixture_id!r}, which is absent from the catalog."
            )
        fixture = fixtures[fixture_id]
        if fixture.route != route["name"]:
            raise ManifestError(
                f"Fixture {fixture_id} belongs to route {fixture.route}, not {route['name']}."
            )
        unknown_checkpoints = set(route["checkpointIds"]) - fixture.checkpoint_ids
        if unknown_checkpoints:
            raise ManifestError(
                f"Route {route['name']} references unknown fixture checkpoints: {sorted(unknown_checkpoints)}"
            )
        if route.get("blockerType") == "unsafe-mutation" and fixture.mutation_boundary != "isolated":
            raise ManifestError(f"Route {route['name']} unsafe-mutation fixture must be isolated.")
        if route.get("blockerType") == "unavailable-fixture" and fixture.kind != "deterministic-error":
            raise ManifestError(f"Route {route['name']} unavailable fixture must be deterministic-error.")
    return fixtures


@dataclass(frozen=True)
class CaptureExpectation:
    route_name: str
    fixture_id: str
    fixture_revision: str
    checkpoint_id: str
    source_commit: str
    app_id: str
    app_version: str
    apk_sha256: str
    signature_digest: str
    expected_package: str
    expected_activity: str


def build_capture_expectation(
    route: dict[str, Any],
    fixture: Any,
    checkpoint_id: str,
    build: dict[str, str],
) -> CaptureExpectation:
    """Bind a future capture session to a verified route, fixture revision, and build."""
    route_name = _required_text(route.get("name"), "route.name")
    if (
        route.get("status") != "verified"
        or not route.get("verification")
        or not route.get("capture", {}).get("allowed")
    ):
        raise PrivacyGateError(f"Route {route_name} is not capture-authorized.")
    if route.get("fixtureId") != fixture.id or fixture.route != route_name:
        raise PrivacyGateError("Route and fixture identity do not match.")
    fixture_root = find_workspace_root(Path.cwd())
    if (
        not fixture.device_ready
        or not fixture.app_fixture_available(fixture_root)
        or fixture.target == "host-only"
    ):
        raise PrivacyGateError("Fixture is not device-ready on an isolated target.")
    if checkpoint_id not in route.get("checkpointIds", ()):
        raise PrivacyGateError("Checkpoint is not authorized by the route.")
    checkpoint = next(
        (candidate for candidate in fixture.checkpoints if candidate["id"] == checkpoint_id),
        None,
    )
    if checkpoint is None:
        raise PrivacyGateError("Checkpoint is absent from the fixture revision.")
    build_fields = {
        "sourceCommit": "sourceCommit",
        "appId": "appId",
        "version": "version",
        "apkSha256": "apkSha256",
        "signatureDigest": "signatureDigest",
    }
    for build_field, provenance_field in build_fields.items():
        actual = _required_text(build.get(build_field), f"build.{build_field}")
        expected = _required_text(fixture.provenance.get(provenance_field), f"provenance.{provenance_field}")
        matches = (
            actual.upper() == expected.upper()
            if build_field in {"apkSha256", "signatureDigest"}
            else actual == expected
        )
        if not matches:
            raise PrivacyGateError(f"Build field {build_field} does not match the fixture provenance.")
    return CaptureExpectation(
        route_name=route_name,
        fixture_id=fixture.id,
        fixture_revision=fixture.revision,
        checkpoint_id=checkpoint_id,
        source_commit=build["sourceCommit"],
        app_id=build["appId"],
        app_version=build["version"],
        apk_sha256=_required_sha256(build["apkSha256"], "build.apkSha256"),
        signature_digest=_required_sha256(build["signatureDigest"], "build.signatureDigest"),
        expected_package=_required_text(checkpoint.get("package"), "checkpoint.package"),
        expected_activity=_required_text(checkpoint.get("activity"), "checkpoint.activity"),
    )


@dataclass
class Session:
    session_id: str
    device_serial: str
    route_name: str
    fixture_id: str
    fixture_revision: str
    checkpoint_id: str
    source_commit: str
    app_id: str
    app_version: str
    apk_sha256: str
    signature_digest: str
    package: str
    activity: str
    expected_package: str
    expected_activity: str
    evaluation_mode_verified: bool
    overlay_clear: bool
    known_state: str
    verified_at: str
    expires_at: str
    state: SessionState = SessionState.VERIFIED
    capture_authorized: bool = True

    def is_expired(self, now: datetime | None = None) -> bool:
        now = now or datetime.now(timezone.utc)
        return now >= datetime.fromisoformat(self.expires_at)

    def authorize_capture(self, now: datetime | None = None) -> None:
        if self.state != SessionState.VERIFIED:
            raise PrivacyGateError(f"Session is not verified: {self.state.value}")
        if self.is_expired(now):
            raise PrivacyGateError("Session verification expired.")
        if not self.evaluation_mode_verified:
            raise PrivacyGateError("Evaluation Mode marker was not confirmed.")
        if (
            not self.overlay_clear
            or self.package != self.expected_package
            or self.activity != self.expected_activity
        ):
            raise PrivacyGateError("Foreground package or overlay state is unsafe.")
        if not self.capture_authorized:
            raise PrivacyGateError("Capture authorization was already consumed.")

    def consume_capture(self) -> "Session":
        self.authorize_capture()
        self.capture_authorized = False
        return self


def _required_text(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise PrivacyGateError(f"Capture field {field} must be non-empty.")
    return value.strip()


def _required_sha256(value: Any, field: str) -> str:
    raw = _required_text(value, field).upper()
    if len(raw) != SHA256_HEX_LENGTH or any(character not in "0123456789ABCDEF" for character in raw):
        raise PrivacyGateError(f"Capture field {field} must be a SHA-256 digest.")
    return raw


def _expect_equal(result: dict[str, Any], result_field: str, expected: str) -> None:
    actual = _required_text(result.get(result_field), result_field)
    if actual != expected:
        raise PrivacyGateError(f"Capture field {result_field} does not match the verified expectation.")


def create_session(
    result: dict[str, Any],
    expectation: CaptureExpectation,
    ttl_seconds: int = MAX_SESSION_TTL_SECONDS,
    *,
    now: datetime | None = None,
) -> Session:
    if not isinstance(ttl_seconds, int) or not 0 < ttl_seconds <= MAX_SESSION_TTL_SECONDS:
        raise PrivacyGateError(f"Session TTL must be between 1 and {MAX_SESSION_TTL_SECONDS} seconds.")
    expected_package = _required_text(expectation.expected_package, "expectedPackage")
    expected_activity = _required_text(expectation.expected_activity, "expectedActivity")
    if expected_package != PACKAGE:
        raise PrivacyGateError(f"Expected package must be {PACKAGE}.")
    if expectation.app_id != PACKAGE:
        raise PrivacyGateError(f"Expected app id must be {PACKAGE}.")
    _required_sha256(expectation.apk_sha256, "apkSha256")
    _required_sha256(expectation.signature_digest, "signatureDigest")
    expected_fields = {
        "routeName": expectation.route_name,
        "fixtureId": expectation.fixture_id,
        "fixtureRevision": expectation.fixture_revision,
        "checkpointId": expectation.checkpoint_id,
        "sourceCommit": expectation.source_commit,
        "appId": expectation.app_id,
        "appVersion": expectation.app_version,
        "apkSha256": expectation.apk_sha256.upper(),
        "signatureDigest": expectation.signature_digest.upper(),
        "package": expected_package,
        "activity": expected_activity,
    }
    for field, expected in expected_fields.items():
        _expect_equal(result, field, _required_text(expected, field))
    if not result.get("deviceOnline") or not result.get("deviceUnambiguous"):
        raise PrivacyGateError("Device is offline or device selection is ambiguous.")
    if not result.get("activityVerified"):
        raise PrivacyGateError("Foreground activity is not verified.")
    if not result.get("evaluationModeVerified"):
        raise PrivacyGateError("Evaluation Mode marker was not confirmed.")
    if not result.get("overlayClear"):
        raise PrivacyGateError("An unrelated overlay is active.")
    now = now or datetime.now(timezone.utc)
    return Session(
        session_id=uuid.uuid4().hex,
        device_serial=_required_text(result.get("deviceSerial"), "deviceSerial"),
        route_name=expectation.route_name,
        fixture_id=expectation.fixture_id,
        fixture_revision=expectation.fixture_revision,
        checkpoint_id=expectation.checkpoint_id,
        source_commit=expectation.source_commit,
        app_id=expectation.app_id,
        app_version=expectation.app_version,
        apk_sha256=expectation.apk_sha256.upper(),
        signature_digest=expectation.signature_digest.upper(),
        package=expected_package,
        activity=expected_activity,
        expected_package=expected_package,
        expected_activity=expected_activity,
        evaluation_mode_verified=True,
        overlay_clear=True,
        known_state=str(result.get("knownState", "unknown")),
        verified_at=now.isoformat(),
        expires_at=(now + timedelta(seconds=ttl_seconds)).isoformat(),
    )


def inspect_png(path: Path) -> dict[str, Any]:
    try:
        data = path.read_bytes()
    except OSError as exc:
        raise SidecarError(f"Could not read screenshot {path}: {exc}") from exc
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise SidecarError("Screenshot is not a PNG.")
    offset = 8
    width = height = None
    idat = bytearray()
    saw_iend = False
    while offset + 12 <= len(data):
        length = struct.unpack(">I", data[offset : offset + 4])[0]
        chunk_type = data[offset + 4 : offset + 8]
        chunk_start = offset + 8
        chunk_end = chunk_start + length
        crc_end = chunk_end + 4
        if crc_end > len(data):
            raise SidecarError("PNG contains a truncated chunk.")
        chunk_data = data[chunk_start:chunk_end]
        expected_crc = struct.unpack(">I", data[chunk_end:crc_end])[0]
        actual_crc = zlib.crc32(chunk_type + chunk_data) & 0xFFFFFFFF
        if actual_crc != expected_crc:
            raise SidecarError("PNG chunk CRC validation failed.")
        if chunk_type == b"IHDR":
            if length != 13 or width is not None:
                raise SidecarError("PNG has an invalid IHDR chunk.")
            width, height = struct.unpack(">II", chunk_data[:8])
            if width <= 0 or height <= 0:
                raise SidecarError("PNG dimensions must be positive.")
        elif chunk_type == b"IDAT":
            idat.extend(chunk_data)
        elif chunk_type == b"IEND":
            saw_iend = True
            offset = crc_end
            break
        offset = crc_end
    if width is None or height is None or not idat or not saw_iend or offset != len(data):
        raise SidecarError("PNG is missing required chunks or has trailing data.")
    try:
        decoded = zlib.decompress(bytes(idat))
    except zlib.error as exc:
        raise SidecarError("PNG image data did not decode.") from exc
    if not decoded:
        raise SidecarError("PNG decoded to an empty image payload.")
    return {
        "sha256": hashlib.sha256(data).hexdigest().upper(),
        "width": width,
        "height": height,
        "decoderResult": "png-chunks-and-idat-decoded",
    }


def verify_capture_checkpoint(session: Session, result: dict[str, Any]) -> str:
    expected_fields = {
        "routeName": session.route_name,
        "fixtureId": session.fixture_id,
        "fixtureRevision": session.fixture_revision,
        "checkpointId": session.checkpoint_id,
        "package": session.expected_package,
        "activity": session.expected_activity,
    }
    for field, expected in expected_fields.items():
        _expect_equal(result, field, expected)
    if not result.get("evaluationModeVerified") or not result.get("overlayClear"):
        raise PrivacyGateError("Capture checkpoint lost Evaluation Mode or overlay safety.")
    if not result.get("markerVerified"):
        raise PrivacyGateError("Capture checkpoint marker was not verified.")
    return _required_text(result.get("knownState"), "knownState")


def build_sidecar(
    session: Session,
    screenshot_path: str,
    capture_result: dict[str, Any],
    *,
    crop_status: str = "not-cropped",
    timestamp: str | None = None,
) -> dict[str, Any]:
    if crop_status not in CROP_STATUSES:
        raise SidecarError(f"Invalid crop status: {crop_status}")
    known_state = verify_capture_checkpoint(session, capture_result)
    image = inspect_png(Path(screenshot_path))
    session.consume_capture()
    return {
        "schemaVersion": 2,
        "timestamp": timestamp or datetime.now(timezone.utc).isoformat(),
        "captureSession": {
            "id": session.session_id,
            "verifiedAt": session.verified_at,
            "expiresAt": session.expires_at,
            "consumed": True,
        },
        "route": {
            "name": session.route_name,
            "fixtureId": session.fixture_id,
            "fixtureRevision": session.fixture_revision,
            "checkpointId": session.checkpoint_id,
        },
        "build": {
            "sourceCommit": session.source_commit,
            "appId": session.app_id,
            "version": session.app_version,
            "apkSha256": session.apk_sha256,
            "signatureDigest": session.signature_digest,
        },
        "target": {
            "deviceSerial": session.device_serial,
            "package": session.package,
            "activity": session.activity,
            "expectedPackage": session.expected_package,
            "expectedActivity": session.expected_activity,
            "preflightKnownState": session.known_state,
            "captureKnownState": known_state,
        },
        "evaluationMode": {"verified": True, "marker": "runner-verified"},
        "image": {"path": screenshot_path, **image, "cropStatus": crop_status},
        "privacyReview": {
            "status": "pending", "canaryScan": "pending", "reviewer": None, "reviewedAt": None, "note": None,
        },
        "cleanup": {
            "status": "pending", "operation": None, "predicates": [], "reviewer": None, "verifiedAt": None,
        },
        "privateReview": {"status": "not-reviewed", "reviewer": None, "reviewedAt": None, "note": None},
        "publication": {"status": "human-gate-required", "approvedBy": None, "approvedAt": None, "note": None},
        "blockingReason": None,
    }


def _reviewer(value: Any) -> str:
    if not isinstance(value, str) or not value.strip():
        raise SidecarError("A non-empty reviewer is required.")
    return value.strip()


def update_privacy_review(
    sidecar: dict[str, Any],
    status: str,
    canary_scan: str,
    reviewer: str,
    note: str | None = None,
    *,
    now: str | None = None,
) -> dict[str, Any]:
    if status not in PRIVACY_REVIEW_STATUSES - {"pending"} or canary_scan not in {"passed", "failed"}:
        raise SidecarError("Privacy review and canary scan must be passed or failed.")
    if status == "passed" and canary_scan != "passed":
        raise SidecarError("Privacy review cannot pass when the canary scan failed.")
    result = copy.deepcopy(sidecar)
    result["privacyReview"] = {
        "status": status,
        "canaryScan": canary_scan,
        "reviewer": _reviewer(reviewer),
        "reviewedAt": now or datetime.now(timezone.utc).isoformat(),
        "note": note,
    }
    return result


def update_cleanup_result(
    sidecar: dict[str, Any],
    status: str,
    operation: str,
    predicates: list[dict[str, str]],
    reviewer: str,
    *,
    now: str | None = None,
) -> dict[str, Any]:
    if status not in CLEANUP_STATUSES - {"pending"}:
        raise SidecarError(f"Invalid cleanup status: {status}")
    if not isinstance(operation, str) or not operation.strip() or not isinstance(predicates, list) or not predicates:
        raise SidecarError("Cleanup requires an operation and predicate results.")
    for predicate in predicates:
        if not isinstance(predicate, dict) or not predicate.get("id") or predicate.get("status") not in {"passed", "failed"}:
            raise SidecarError("Every cleanup predicate requires an id and passed/failed status.")
    if status == "passed" and any(predicate["status"] != "passed" for predicate in predicates):
        raise SidecarError("Cleanup cannot pass while a predicate failed.")
    result = copy.deepcopy(sidecar)
    result["cleanup"] = {
        "status": status,
        "operation": operation.strip(),
        "predicates": copy.deepcopy(predicates),
        "reviewer": _reviewer(reviewer),
        "verifiedAt": now or datetime.now(timezone.utc).isoformat(),
    }
    return result


def update_review(
    sidecar: dict[str, Any],
    status: str,
    reviewer: str,
    note: str | None = None,
    *,
    now: str | None = None,
) -> dict[str, Any]:
    if status not in REVIEW_STATUSES:
        raise SidecarError(f"Invalid review status: {status}")
    if status == "approved":
        if sidecar.get("privacyReview", {}).get("status") != "passed":
            raise SidecarError("Private approval requires a passed privacy review.")
        if sidecar.get("cleanup", {}).get("status") != "passed":
            raise SidecarError("Private approval requires passed cleanup.")
        if sidecar.get("image", {}).get("cropStatus") == "cropped-unreviewed":
            raise SidecarError("Private approval requires crop review.")
    result = copy.deepcopy(sidecar)
    result["privateReview"] = {
        "status": status,
        "reviewer": _reviewer(reviewer),
        "reviewedAt": now or datetime.now(timezone.utc).isoformat(),
        "note": note,
    }
    return result


def authorize_publication(
    sidecar: dict[str, Any],
    approved_by: str,
    note: str,
    *,
    now: str | None = None,
) -> dict[str, Any]:
    if sidecar.get("privateReview", {}).get("status") != "approved":
        raise SidecarError("Publication authorization requires private approval.")
    if sidecar.get("privacyReview", {}).get("status") != "passed" or sidecar.get("cleanup", {}).get("status") != "passed":
        raise SidecarError("Publication authorization requires privacy and cleanup approval.")
    if not isinstance(note, str) or not note.strip():
        raise SidecarError("Publication authorization requires a human approval note.")
    result = copy.deepcopy(sidecar)
    result["publication"] = {
        "status": "authorized",
        "approvedBy": _reviewer(approved_by),
        "approvedAt": now or datetime.now(timezone.utc).isoformat(),
        "note": note.strip(),
    }
    return result


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    manifest = sub.add_parser("manifest")
    manifest.add_argument("action", choices=("validate", "list", "readiness"))
    manifest.add_argument("path", type=Path)
    manifest.add_argument("--fixtures", type=Path)
    review = sub.add_parser("review")
    review.add_argument("sidecar", type=Path)
    review.add_argument("status", choices=sorted(REVIEW_STATUSES))
    review.add_argument("--reviewer", required=True)
    review.add_argument("--note")
    return parser


def main(argv: list[str] | None = None) -> int:
    configure_console_encoding()
    args = build_parser().parse_args(argv)
    try:
        if args.command == "manifest":
            manifest = load_json(args.path)
            validate_manifest(manifest)
            catalog_fixtures = None
            if args.fixtures:
                catalog_fixtures = validate_manifest_fixture_catalog(manifest, args.fixtures)
            if args.action == "list":
                print("\n".join(route["name"] for route in manifest["routes"]))
            elif args.action == "readiness":
                catalog_root = None
                if args.fixtures:
                    from tools.workspace_paths import find_workspace_root

                    catalog_root = find_workspace_root(args.fixtures.resolve().parent.parent)
                for route in manifest["routes"]:
                    fixture_id = route.get("fixtureId")
                    if fixture_id:
                        catalog_fixture = catalog_fixtures.get(fixture_id) if catalog_fixtures else None
                        if catalog_fixture and catalog_root:
                            layers = catalog_fixture.readiness_layers(catalog_root)
                            status = ", ".join(
                                f"{name}={'ready' if ready else 'blocked'}" for name, ready in layers.items()
                            )
                        else:
                            status = "catalog-required"
                        print(f"{route['name']}: {status} ({fixture_id})")
            else:
                print(f"validated {len(manifest['routes'])} routes")
        else:
            sidecar = load_json(args.sidecar)
            print(
                json.dumps(
                    update_review(sidecar, args.status, args.reviewer, args.note),
                    ensure_ascii=False,
                    indent=2,
                )
            )
        return 0
    except CoordinatorError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return exc.exit_code


if __name__ == "__main__":
    raise SystemExit(main())
