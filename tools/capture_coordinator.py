#!/usr/bin/env python3
"""Host-side policy coordinator for Evaluation Mode evidence capture.

Device interaction belongs to the AndroidX UI Automator test runner. This
module validates manifests, gates capture authorization, writes sidecars, and
separates capture facts from human review.
"""

from __future__ import annotations

import argparse
import json
import sys
import uuid
from dataclasses import asdict, dataclass
from datetime import datetime, timedelta, timezone
from enum import Enum
from pathlib import Path
from typing import Any


PACKAGE = "app.komikku.dev"
STATUSES = {"planned", "entered", "verified", "captured", "blocked", "not-applicable"}
REVIEW_STATUSES = {"not-reviewed", "needs-review", "approved", "rejected"}


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
        if status == "verified" and not route.get("verification"):
            raise ManifestError(f"Route {name} cannot be verified without verification evidence.")
        if status == "captured" and not route.get("sidecarPath"):
            raise ManifestError(f"Route {name} cannot be captured without sidecarPath.")
        if route.get("capture", {}).get("allowed") and status not in {"planned", "entered", "verified", "captured", "blocked"}:
            raise ManifestError(f"Route {name} has an invalid capture/status combination.")


@dataclass(frozen=True)
class Session:
    session_id: str
    device_serial: str
    package: str
    activity: str
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
        if not self.overlay_clear or self.package != PACKAGE:
            raise PrivacyGateError("Foreground package or overlay state is unsafe.")
        if not self.capture_authorized:
            raise PrivacyGateError("Capture authorization was already consumed.")

    def consume_capture(self) -> "Session":
        self.authorize_capture()
        return Session(**{**asdict(self), "capture_authorized": False})


def create_session(result: dict[str, Any], ttl_seconds: int = 300) -> Session:
    if result.get("package") != PACKAGE:
        raise PrivacyGateError("Foreground package is not app.komikku.dev.")
    if not result.get("deviceOnline") or not result.get("deviceUnambiguous"):
        raise PrivacyGateError("Device is offline or device selection is ambiguous.")
    if not result.get("activityVerified"):
        raise PrivacyGateError("Foreground activity is not verified.")
    if not result.get("evaluationModeVerified"):
        raise PrivacyGateError("Evaluation Mode marker was not confirmed.")
    if not result.get("overlayClear"):
        raise PrivacyGateError("An unrelated overlay is active.")
    now = datetime.now(timezone.utc)
    return Session(
        session_id=uuid.uuid4().hex,
        device_serial=str(result["deviceSerial"]),
        package=PACKAGE,
        activity=str(result["activity"]),
        evaluation_mode_verified=True,
        overlay_clear=True,
        known_state=str(result.get("knownState", "unknown")),
        verified_at=now.isoformat(),
        expires_at=(now + timedelta(seconds=ttl_seconds)).isoformat(),
    )


def build_sidecar(session: Session, route: str, screenshot_path: str, timestamp: str | None = None) -> dict[str, Any]:
    session.authorize_capture()
    return {
        "routeName": route,
        "timestamp": timestamp or datetime.now(timezone.utc).isoformat(),
        "deviceSerial": session.device_serial,
        "package": session.package,
        "activity": session.activity,
        "evaluationMode": {"verified": True, "marker": "runner-verified"},
        "knownState": session.known_state,
        "screenshotPath": screenshot_path,
        "reviewStatus": "not-reviewed",
        "blockingReason": None,
    }


def update_review(sidecar: dict[str, Any], status: str, note: str | None = None) -> dict[str, Any]:
    if status not in REVIEW_STATUSES:
        raise SidecarError(f"Invalid review status: {status}")
    result = dict(sidecar)
    result["reviewStatus"] = status
    if note:
        result["reviewNote"] = note
    return result


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    manifest = sub.add_parser("manifest")
    manifest.add_argument("action", choices=("validate", "list"))
    manifest.add_argument("path", type=Path)
    review = sub.add_parser("review")
    review.add_argument("sidecar", type=Path)
    review.add_argument("status", choices=sorted(REVIEW_STATUSES))
    review.add_argument("--note")
    return parser


def main(argv: list[str] | None = None) -> int:
    configure_console_encoding()
    args = build_parser().parse_args(argv)
    try:
        if args.command == "manifest":
            manifest = load_json(args.path)
            validate_manifest(manifest)
            if args.action == "list":
                print("\n".join(route["name"] for route in manifest["routes"]))
            else:
                print(f"validated {len(manifest['routes'])} routes")
        else:
            sidecar = load_json(args.sidecar)
            print(json.dumps(update_review(sidecar, args.status, args.note), ensure_ascii=False, indent=2))
        return 0
    except CoordinatorError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return exc.exit_code


if __name__ == "__main__":
    raise SystemExit(main())
