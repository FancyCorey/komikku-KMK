#!/usr/bin/env python3
"""Validate evidence fixtures without promoting host proof to device readiness."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from tools.workspace_paths import find_workspace_root


FIXTURE_KINDS = {"isolated-device", "deterministic-error", "read-only-local"}
BOUNDARIES = {"read-only", "isolated", "forbidden"}
TARGETS = {"emulator", "secondary-device", "host-only"}
PREDICATE_TYPES = {"semantic", "package", "activity", "absence", "restoration", "hash-equals"}
TERMINAL_OUTCOMES = {"success", "partial", "empty", "error", "cancel", "unavailable"}
SHA256_LENGTH = 64
PROVISIONING_MECHANISMS = {
    "apk-staging-and-supported-package-ui",
    "local-cbz-and-supported-ui",
    "loopback-extension-store-and-user-facing-debug-preference",
    "navigation-only-read-only",
    "user-facing-debug-preference",
}


class FixtureRegistryError(ValueError):
    pass


def _non_empty(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise FixtureRegistryError(f"Fixture field {field} must be non-empty.")
    return value.strip()


def _private_relative(value: Any, field: str) -> str:
    path = _non_empty(value, field).replace("\\", "/")
    if path.startswith("/") or ":" in path or ".." in Path(path).parts:
        raise FixtureRegistryError(f"Fixture field {field} must be a private relative path.")
    if not path.startswith("private/"):
        raise FixtureRegistryError(f"Fixture field {field} must remain under private/.")
    return path


def _workspace_relative(value: Any, field: str) -> str:
    path = _non_empty(value, field).replace("\\", "/")
    if path.startswith("/") or ":" in path or ".." in Path(path).parts:
        raise FixtureRegistryError(f"Fixture field {field} must be a workspace-relative path.")
    return path


def _object(value: Any, field: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise FixtureRegistryError(f"Fixture field {field} must be an object.")
    return value


def _list(value: Any, field: str) -> list[Any]:
    if not isinstance(value, list) or not value:
        raise FixtureRegistryError(f"Fixture field {field} must be a non-empty array.")
    return value


def _iso(value: Any, field: str, *, optional: bool = False) -> datetime | None:
    if value is None and optional:
        return None
    raw = _non_empty(value, field).replace("Z", "+00:00")
    try:
        parsed = datetime.fromisoformat(raw)
    except ValueError as exc:
        raise FixtureRegistryError(f"Fixture field {field} must be ISO-8601.") from exc
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed


def _validate_predicates(value: Any, field: str) -> tuple[dict[str, str], ...]:
    rows = _list(value, field)
    parsed: list[dict[str, str]] = []
    for index, row in enumerate(rows):
        item = _object(row, f"{field}[{index}]")
        kind = _non_empty(item.get("type"), f"{field}[{index}].type")
        expected = _non_empty(item.get("expected"), f"{field}[{index}].expected")
        if kind not in PREDICATE_TYPES:
            raise FixtureRegistryError(f"Fixture field {field}[{index}] has an invalid predicate type.")
        parsed.append({"type": kind, "expected": expected})
    return tuple(parsed)


@dataclass(frozen=True)
class Fixture:
    id: str
    route: str
    revision: str
    owner: str
    kind: str
    target: str
    mutation_boundary: str
    artifact_path: str
    verification_path: str
    provenance: dict[str, Any]
    seed: dict[str, Any]
    start_state: dict[str, Any]
    checkpoints: tuple[dict[str, Any], ...]
    terminal_states: dict[str, tuple[dict[str, str], ...]]
    privacy: dict[str, Any]
    cleanup: dict[str, Any]
    rollback_evidence: dict[str, Any]
    lifecycle: dict[str, Any]
    source_root: Path | None = None

    @property
    def host_available(self) -> bool:
        return bool(self.lifecycle["hostAvailable"])

    @property
    def device_ready(self) -> bool:
        return bool(self.lifecycle["deviceReady"])

    @property
    def checkpoint_ids(self) -> set[str]:
        return {str(checkpoint["id"]) for checkpoint in self.checkpoints}

    def host_contract_available(self, root: Path) -> bool:
        if not self.host_available:
            return False
        artifact = root / self.artifact_path
        verification = root / self.verification_path
        if not artifact.is_file() or not verification.is_file():
            return False
        try:
            artifact_data = json.loads(artifact.read_text(encoding="utf-8"))
            verification_data = json.loads(verification.read_text(encoding="utf-8"))
        except (OSError, UnicodeDecodeError, json.JSONDecodeError):
            return False
        digest = hashlib.sha256(artifact.read_bytes()).hexdigest().upper()
        base_contract_valid = (
            artifact_data.get("fixtureId") == self.id
            and artifact_data.get("route") == self.route
            and verification_data.get("fixtureId") == self.id
            and isinstance(verification_data.get("checks"), list)
            and bool(verification_data["checks"])
            and digest == self.provenance["fixtureInputSha256"]
        )
        if not base_contract_valid:
            return False

        runtime_validation = artifact_data.get("runtimeValidation")
        if runtime_validation is None:
            return True
        if not isinstance(runtime_validation, dict) or runtime_validation.get("schemaVersion") != 1:
            return False
        if (
            artifact_data.get("fixtureRevision") != self.revision
            or verification_data.get("fixtureRevision") != self.revision
            or str(verification_data.get("fixtureInputSha256", "")).upper()
            != self.provenance["fixtureInputSha256"].upper()
        ):
            return False
        inputs = runtime_validation.get("inputs")
        if not isinstance(inputs, list) or not inputs:
            return False
        observed_paths: set[str] = set()
        for index, row in enumerate(inputs):
            if not isinstance(row, dict):
                return False
            try:
                relative = _workspace_relative(row.get("path"), f"runtimeValidation.inputs[{index}].path")
            except FixtureRegistryError:
                return False
            expected = row.get("sha256")
            if (
                relative in observed_paths
                or not isinstance(expected, str)
                or len(expected) != SHA256_LENGTH
                or any(character not in "0123456789abcdefABCDEF" for character in expected)
            ):
                return False
            observed_paths.add(relative)
            runtime_input = (self.source_root or root) / relative
            if not runtime_input.is_file():
                return False
            if hashlib.sha256(runtime_input.read_bytes()).hexdigest().upper() != expected.upper():
                return False
        return True

    def app_fixture_available(self, root: Path) -> bool:
        """Require a revision-bound provisioning packet, not a catalog boolean alone."""
        if not self.seed["supported"] or not self.host_contract_available(root):
            return False
        artifact = root / self.artifact_path
        try:
            artifact_data = json.loads(artifact.read_text(encoding="utf-8"))
        except (OSError, UnicodeDecodeError, json.JSONDecodeError):
            return False
        provisioning = artifact_data.get("provisioning")
        if artifact_data.get("schemaVersion") != 2 or not isinstance(provisioning, dict):
            return False
        if (
            provisioning.get("fixtureRevision") != self.revision
            or provisioning.get("seedType") != self.seed["type"]
            or provisioning.get("mechanism") not in PROVISIONING_MECHANISMS
            or not isinstance(provisioning.get("deviceMutation"), bool)
        ):
            return False
        for field in ("activationSteps", "cleanupSteps"):
            rows = provisioning.get(field)
            if not isinstance(rows, list) or not rows or not all(isinstance(row, str) and row.strip() for row in rows):
                return False
        predicates = provisioning.get("verificationPredicates")
        if not isinstance(predicates, list) or not predicates:
            return False
        try:
            _validate_predicates(predicates, "provisioning.verificationPredicates")
        except FixtureRegistryError:
            return False
        if provisioning["mechanism"] == "user-facing-debug-preference":
            if provisioning.get("releaseGuard") != "BuildConfig.DEBUG and explicit user opt-in":
                return False
        if provisioning["mechanism"] == "navigation-only-read-only":
            if provisioning["deviceMutation"] or self.mutation_boundary != "read-only":
                return False
        if provisioning["mechanism"] == "apk-staging-and-supported-package-ui":
            if not provisioning["deviceMutation"] or self.mutation_boundary != "isolated":
                return False
        if provisioning["mechanism"] == "loopback-extension-store-and-user-facing-debug-preference":
            if (
                not provisioning["deviceMutation"]
                or self.mutation_boundary != "isolated"
                or provisioning.get("releaseGuard") != "BuildConfig.DEBUG and explicit user opt-in"
            ):
                return False
            manifest_path = provisioning.get("fixtureManifestPath")
            manifest_sha256 = provisioning.get("fixtureManifestSha256")
            if not isinstance(manifest_path, str) or not isinstance(manifest_sha256, str):
                return False
            try:
                manifest_relative = _private_relative(manifest_path, "provisioning.fixtureManifestPath")
                manifest_file = root / manifest_relative
                manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
            except (FixtureRegistryError, OSError, UnicodeDecodeError, json.JSONDecodeError):
                return False
            if hashlib.sha256(manifest_file.read_bytes()).hexdigest().upper() != manifest_sha256.upper():
                return False
            artifacts = manifest.get("artifacts")
            expected_packages = {
                "app.komikku.fixture.sources.alpha": 910000000000000001,
                "app.komikku.fixture.sources.beta": 910000000000000002,
            }
            if (
                manifest.get("schema_version") != 1
                or manifest.get("server_profile") != "success"
                or not isinstance(artifacts, list)
                or len(artifacts) != len(expected_packages)
            ):
                return False
            signer_digests: set[str] = set()
            for row in artifacts:
                if not isinstance(row, dict):
                    return False
                package = row.get("package_name")
                artifact_relative = row.get("apk_path")
                if package not in expected_packages or row.get("source_id") != expected_packages[package]:
                    return False
                if not isinstance(artifact_relative, str) or Path(artifact_relative).is_absolute():
                    return False
                artifact_file = (manifest_file.parent / artifact_relative).resolve()
                if manifest_file.parent.resolve() not in artifact_file.parents:
                    return False
                if (
                    not artifact_file.is_file()
                    or artifact_file.stat().st_size != row.get("byte_count")
                    or hashlib.sha256(artifact_file.read_bytes()).hexdigest().lower() != row.get("sha256")
                ):
                    return False
                signer = row.get("signer_sha256")
                if not isinstance(signer, str) or len(signer) != SHA256_LENGTH:
                    return False
                signer_digests.add(signer.lower())
            if len(signer_digests) != 1 or next(iter(signer_digests)) != str(self.provenance.get("signatureDigest", "")).lower():
                return False
        return True

    def is_available(self, root: Path) -> bool:
        """Compatibility alias: availability means host contract only, never device readiness."""
        return self.host_contract_available(root)

    def readiness_layers(self, root: Path) -> dict[str, bool]:
        app_fixture = self.app_fixture_available(root)
        return {
            "hostContract": self.host_contract_available(root),
            "appFixture": app_fixture,
            "deviceFixture": self.device_ready and app_fixture,
            "cleanup": bool(self.cleanup["predicates"]),
            "privacy": bool(self.privacy["forbiddenCanaries"]),
            "captureAuthorization": False,
        }


def parse_fixture(raw: dict[str, Any], *, now: datetime | None = None) -> Fixture:
    if not isinstance(raw, dict):
        raise FixtureRegistryError("Every fixture must be an object.")
    now = now or datetime.now(timezone.utc)
    provenance = _object(raw.get("provenance"), "provenance")
    seed = _object(raw.get("seed"), "seed")
    start_state = _object(raw.get("startState"), "startState")
    privacy = _object(raw.get("privacy"), "privacy")
    cleanup = _object(raw.get("cleanup"), "cleanup")
    rollback = _object(raw.get("rollbackEvidence"), "rollbackEvidence")
    lifecycle = _object(raw.get("lifecycle"), "lifecycle")

    for field in ("sourceCommit", "appId", "version", "fixtureInputSha256"):
        _non_empty(provenance.get(field), f"provenance.{field}")
    if len(provenance["fixtureInputSha256"]) != SHA256_LENGTH:
        raise FixtureRegistryError("Fixture provenance.fixtureInputSha256 must be SHA-256.")
    if not isinstance(seed.get("supported"), bool):
        raise FixtureRegistryError("Fixture seed.supported must be boolean.")
    _non_empty(seed.get("type"), "seed.type")
    _list(seed.get("inputs"), "seed.inputs")
    _list(seed.get("preconditions"), "seed.preconditions")
    _non_empty(seed.get("idempotencyRule"), "seed.idempotencyRule")
    _validate_predicates(start_state.get("predicates"), "startState.predicates")
    _non_empty(start_state.get("package"), "startState.package")
    _non_empty(start_state.get("activity"), "startState.activity")

    checkpoints_raw = _list(raw.get("checkpoints"), "checkpoints")
    checkpoints: list[dict[str, Any]] = []
    checkpoint_ids: set[str] = set()
    for index, checkpoint_raw in enumerate(checkpoints_raw):
        checkpoint = _object(checkpoint_raw, f"checkpoints[{index}]")
        checkpoint_id = _non_empty(checkpoint.get("id"), f"checkpoints[{index}].id")
        if checkpoint_id in checkpoint_ids:
            raise FixtureRegistryError(f"Duplicate checkpoint id: {checkpoint_id}")
        checkpoint_ids.add(checkpoint_id)
        _validate_predicates(checkpoint.get("predicates"), f"checkpoints[{index}].predicates")
        _list(checkpoint.get("allowedTransientStates"), f"checkpoints[{index}].allowedTransientStates")
        if not isinstance(checkpoint.get("timeoutSeconds"), int) or checkpoint["timeoutSeconds"] <= 0:
            raise FixtureRegistryError(f"Fixture checkpoints[{index}].timeoutSeconds must be positive.")
        _non_empty(checkpoint.get("package"), f"checkpoints[{index}].package")
        _non_empty(checkpoint.get("activity"), f"checkpoints[{index}].activity")
        checkpoints.append(checkpoint)

    terminal_raw = _object(raw.get("terminalStates"), "terminalStates")
    if not terminal_raw:
        raise FixtureRegistryError("Fixture terminalStates must contain at least one outcome.")
    terminal_states: dict[str, tuple[dict[str, str], ...]] = {}
    for outcome, predicates in terminal_raw.items():
        if outcome not in TERMINAL_OUTCOMES:
            raise FixtureRegistryError(f"Fixture terminalStates has invalid outcome {outcome!r}.")
        terminal_states[outcome] = _validate_predicates(predicates, f"terminalStates.{outcome}")

    if not isinstance(privacy.get("evaluationModeRequired"), bool):
        raise FixtureRegistryError("Fixture privacy.evaluationModeRequired must be boolean.")
    _list(privacy.get("forbiddenCanaries"), "privacy.forbiddenCanaries")
    for field in ("cropPolicy", "systemChromePolicy"):
        _non_empty(privacy.get(field), f"privacy.{field}")
    _non_empty(cleanup.get("operation"), "cleanup.operation")
    _non_empty(cleanup.get("target"), "cleanup.target")
    cleanup["predicates"] = _validate_predicates(cleanup.get("predicates"), "cleanup.predicates")
    if not isinstance(cleanup.get("timeoutSeconds"), int) or cleanup["timeoutSeconds"] <= 0:
        raise FixtureRegistryError("Fixture cleanup.timeoutSeconds must be positive.")

    external_effect = _non_empty(rollback.get("externalEffect"), "rollbackEvidence.externalEffect")
    if not isinstance(rollback.get("claimsFullRollback"), bool):
        raise FixtureRegistryError("Fixture rollbackEvidence.claimsFullRollback must be boolean.")
    _list(rollback.get("preStateEvidence"), "rollbackEvidence.preStateEvidence")
    _list(rollback.get("postCleanupComparison"), "rollbackEvidence.postCleanupComparison")
    if external_effect != "none" and rollback["claimsFullRollback"]:
        raise FixtureRegistryError("A fixture with external effects cannot claim full rollback.")

    for field in ("hostAvailable", "deviceReady"):
        if not isinstance(lifecycle.get(field), bool):
            raise FixtureRegistryError(f"Fixture lifecycle.{field} must be boolean.")
    _iso(lifecycle.get("validatedAt"), "lifecycle.validatedAt")
    expires_at = _iso(lifecycle.get("expiresAt"), "lifecycle.expiresAt", optional=True)
    invalidating = _list(lifecycle.get("invalidatingFingerprints"), "lifecycle.invalidatingFingerprints")
    validated = _object(lifecycle.get("validatedFingerprints"), "lifecycle.validatedFingerprints")

    target = _non_empty(raw.get("target"), "target")
    device_ready = lifecycle["deviceReady"]
    if target == "host-only" and device_ready:
        raise FixtureRegistryError("A host-only fixture cannot be promoted to device readiness.")
    if device_ready:
        if not lifecycle["hostAvailable"] or not seed["supported"]:
            raise FixtureRegistryError("Device readiness requires host and app-fixture readiness.")
        if expires_at is None or expires_at <= now:
            raise FixtureRegistryError("Device-ready fixture validation is expired or has no expiry.")
        for fingerprint in invalidating:
            if provenance.get(fingerprint) in (None, "") or validated.get(fingerprint) != provenance.get(fingerprint):
                raise FixtureRegistryError(f"Device-ready fixture has stale fingerprint {fingerprint}.")

    fixture = Fixture(
        id=_non_empty(raw.get("id"), "id"),
        route=_non_empty(raw.get("route"), "route"),
        revision=_non_empty(raw.get("revision"), "revision"),
        owner=_non_empty(raw.get("owner"), "owner"),
        kind=_non_empty(raw.get("kind"), "kind"),
        target=target,
        mutation_boundary=_non_empty(raw.get("mutationBoundary"), "mutationBoundary"),
        artifact_path=_private_relative(raw.get("artifactPath"), "artifactPath"),
        verification_path=_private_relative(raw.get("verificationPath"), "verificationPath"),
        provenance=provenance,
        seed=seed,
        start_state=start_state,
        checkpoints=tuple(checkpoints),
        terminal_states=terminal_states,
        privacy=privacy,
        cleanup=cleanup,
        rollback_evidence=rollback,
        lifecycle=lifecycle,
    )
    if fixture.kind not in FIXTURE_KINDS or fixture.mutation_boundary not in BOUNDARIES or fixture.target not in TARGETS:
        raise FixtureRegistryError(f"Fixture {fixture.id} has an invalid kind, boundary, or target.")
    if fixture.kind == "deterministic-error" and fixture.mutation_boundary != "read-only":
        raise FixtureRegistryError(f"Fixture {fixture.id} error fixtures must be read-only.")
    if fixture.kind == "isolated-device" and fixture.mutation_boundary != "isolated":
        raise FixtureRegistryError(f"Fixture {fixture.id} must use an isolated boundary.")
    if fixture.mutation_boundary == "forbidden":
        raise FixtureRegistryError(f"Fixture {fixture.id} cannot be capture-ready with a forbidden boundary.")
    return fixture


def load_fixtures(
    path: Path,
    *,
    now: datetime | None = None,
    root: Path | None = None,
    source_root: Path | None = None,
    fixture_id: str | None = None,
    fixture_ids: set[str] | None = None,
) -> list[Fixture]:
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise FixtureRegistryError(f"Could not read fixture catalog {path}: {exc}") from exc
    if not isinstance(raw, dict) or raw.get("schemaVersion") != 2:
        raise FixtureRegistryError("Fixture catalog requires schemaVersion 2.")
    rows = raw.get("fixtures")
    if not isinstance(rows, list):
        raise FixtureRegistryError("Fixture catalog requires a fixtures array.")
    if fixture_id is not None and fixture_ids is not None:
        raise FixtureRegistryError("Select fixture_id or fixture_ids, not both.")
    selected_ids = {fixture_id} if fixture_id is not None else fixture_ids
    if selected_ids is not None:
        # A lifecycle action is scoped to one fixture, not the readiness of every
        # unrelated device route. Full catalog validation remains the default.
        rows = [row for row in rows if isinstance(row, dict) and row.get("id") in selected_ids]
        missing = selected_ids - {row["id"] for row in rows}
        if missing:
            raise FixtureRegistryError(f"Unknown fixture id: {', '.join(sorted(missing))}")
    source_root = source_root or path.resolve().parent.parent
    fixtures = [parse_fixture(row, now=now) for row in rows]
    for fixture in fixtures:
        object.__setattr__(fixture, "source_root", source_root)
    ids = [fixture.id for fixture in fixtures]
    if len(ids) != len(set(ids)):
        raise FixtureRegistryError("Fixture ids must be unique.")
    fixture_root = root or find_workspace_root(path)
    for fixture in fixtures:
        if fixture.device_ready and not fixture.app_fixture_available(fixture_root):
            raise FixtureRegistryError(
                f"Device-ready fixture {fixture.id} lacks a valid route-specific provisioning packet."
            )
    return fixtures


def readiness(path: Path, root: Path) -> list[str]:
    return [
        f"{fixture.id}: " + ", ".join(
            f"{layer}={'ready' if ready else 'blocked'}"
            for layer, ready in fixture.readiness_layers(root).items()
        )
        for fixture in load_fixtures(path)
    ]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("validate", "readiness"))
    parser.add_argument("path", type=Path)
    parser.add_argument("--root", type=Path, default=find_workspace_root(Path.cwd()))
    args = parser.parse_args(argv)
    try:
        fixtures = load_fixtures(args.path)
        print(f"validated {len(fixtures)} fixtures" if args.action == "validate" else "\n".join(readiness(args.path, args.root)))
        return 0
    except FixtureRegistryError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
