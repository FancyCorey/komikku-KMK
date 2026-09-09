"""Validate fingerprint-bound domain state without granting new claims."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any

from .control_common import CONTROL_ROOT, STATE_ROOT, dependency_fingerprint_map, fingerprint_domain, git_snapshot, load_domains, read_json
from .validate_evidence import resolve_positive_evidence


STATUSES = ("implemented", "host_tested", "device_tested", "release_ready")
BLOCKED_CLASSES = {
    "external_dependency",
    "human_decision_required",
    "tooling_unavailable",
    "ambiguous_requirement",
}


def _status_error(
    domain_id: str,
    status_name: str,
    status: dict[str, Any],
    fingerprint: str,
    evidence_root: Path,
    attempts_root: Path,
    index_path: Path | None,
) -> tuple[list[str], list[dict[str, Any]]]:
    errors: list[str] = []
    records: list[dict[str, Any]] = []
    if not isinstance(status, dict):
        return [f"{domain_id}.{status_name}: status must be an object"], records
    if not isinstance(status.get("value"), bool):
        errors.append(f"{domain_id}.{status_name}: value must be boolean")
    evidence = status.get("evidence")
    if not isinstance(evidence, list):
        errors.append(f"{domain_id}.{status_name}: evidence must be an array")
    if status.get("value"):
        if not isinstance(status.get("as_of"), str) or status["as_of"] != fingerprint:
            errors.append(f"{domain_id}.{status_name}: positive claim is not bound to current fingerprint")
        if not evidence:
            errors.append(f"{domain_id}.{status_name}: positive claim requires evidence references")
        for reference in evidence:
            record, resolution_errors = resolve_positive_evidence(
                reference, domain_id, status.get("as_of"), evidence_root, attempts_root, index_path
            )
            errors.extend(resolution_errors)
            if record is not None:
                records.append(record)
    return errors, records


def validate_state(
    state_path: Path,
    domain: dict[str, Any],
    evidence_root: Path = Path(CONTROL_ROOT / "evidence"),
    attempts_root: Path = Path(CONTROL_ROOT / "attempts"),
    index_path: Path | None = None,
) -> list[str]:
    domain_id = domain["id"]
    state = read_json(state_path)
    errors: list[str] = []
    fingerprint, _ = fingerprint_domain(domain)
    dependency_fingerprints = dependency_fingerprint_map(domain)
    if state.get("schema_version") != "1.0.0":
        errors.append(f"{domain_id}: unsupported state schema_version")
    if state.get("domain_id") != domain_id:
        errors.append(f"{domain_id}: state domain_id mismatch")
    if state.get("owned_fingerprint") != fingerprint:
        errors.append(f"{domain_id}: state owned_fingerprint is stale")
    if state.get("dependency_fingerprints") != dependency_fingerprints:
        errors.append(f"{domain_id}: dependency_fingerprints are stale")
    try:
        current_git = git_snapshot()
        if state.get("git_snapshot") != current_git:
            errors.append(f"{domain_id}: git_snapshot is stale")
    except (OSError, ValueError) as exc:
        errors.append(f"{domain_id}: cannot read git snapshot: {exc}")
    statuses = state.get("statuses")
    if not isinstance(statuses, dict):
        return errors + [f"{domain_id}: statuses must be an object"]
    all_records: list[dict[str, Any]] = []
    for status_name in STATUSES:
        status_errors, records = _status_error(
            domain_id,
            status_name,
            statuses.get(status_name),
            fingerprint,
            evidence_root,
            attempts_root,
            index_path,
        )
        errors.extend(status_errors)
        all_records.extend(records)
    if any(isinstance(statuses.get(name), dict) and statuses[name].get("value") for name in STATUSES):
        if state.get("git_snapshot", {}).get("dirty") is not False:
            errors.append(f"{domain_id}: positive claims require a clean git worktree")
    values = {name: bool(statuses.get(name, {}).get("value")) for name in STATUSES}
    if values["host_tested"] and not values["implemented"]:
        errors.append(f"{domain_id}: host_tested requires implemented")
    if values["device_tested"] and not values["host_tested"]:
        errors.append(f"{domain_id}: device_tested requires host_tested")
    if values["release_ready"] and not values["device_tested"]:
        errors.append(f"{domain_id}: release_ready requires device_tested")
    if values["release_ready"]:
        evidence_classes = {record.get("evidence_class") for record in all_records}
        missing = set(domain.get("required_evidence", [])) - evidence_classes
        if missing:
            errors.append(f"{domain_id}: release_ready is missing required evidence classes: {sorted(missing)}")
    blocked = state.get("blocked")
    if blocked is not None:
        if not isinstance(blocked, dict) or blocked.get("class") not in BLOCKED_CLASSES:
            errors.append(f"{domain_id}: blocked.class is not an allowed blocker class")
        elif not isinstance(blocked.get("needs"), list) or not blocked["needs"]:
            errors.append(f"{domain_id}: blocked.needs must be non-empty")
    return errors


def validate_all() -> list[str]:
    errors: list[str] = []
    for domain_id, domain in load_domains().items():
        state_path = STATE_ROOT / f"{domain_id}.state.json"
        if not state_path.is_file():
            errors.append(f"{domain_id}: missing state file")
            continue
        try:
            errors.extend(validate_state(state_path, domain))
        except (OSError, ValueError) as exc:
            errors.append(f"{domain_id}: {exc}")
    return errors


def main() -> int:
    errors = validate_all()
    if errors:
        print("\n".join(f"error: {error}" for error in errors), file=sys.stderr)
        return 2
    print("control state valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
