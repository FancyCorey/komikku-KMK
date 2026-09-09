"""Validate immutable evidence record shape and provenance fields."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

from .attempts import attempt_key, existing_keys
from .control_common import CONTROL_ROOT, canonical_json, load_domains, read_json


HEX64 = re.compile(r"^[0-9a-f]{64}$")
EVIDENCE_CLASSES = {"host_tested", "device_tested", "release", "compatibility", "privacy_review", "fixture_validation", "route_validation", "rollback", "failure", "cancellation", "integration", "action_history_matrix"}


def _record_hash(record: dict[str, Any]) -> str:
    content = dict(record)
    content.pop("content_sha256", None)
    return hashlib.sha256(canonical_json(content)).hexdigest()


def validate_record(
    path: Path,
    domain_ids: set[str],
    attempts_root: Path = CONTROL_ROOT / "attempts",
    index_path: Path = CONTROL_ROOT / "evidence" / "index.json",
) -> list[str]:
    errors: list[str] = []
    record = read_json(path)
    record_id = record.get("id")
    if not isinstance(record_id, str) or not record_id.startswith("EVID-"):
        errors.append(f"{path.name}: id must start with EVID-")
    if path.stem != record_id:
        errors.append(f"{path.name}: filename must match id")
    if record.get("domain_id") not in domain_ids:
        errors.append(f"{path.name}: unknown domain_id")
    if record.get("evidence_class") not in EVIDENCE_CLASSES:
        errors.append(f"{path.name}: invalid evidence_class")
    if not isinstance(record.get("command"), str) or not record["command"].strip():
        errors.append(f"{path.name}: command is required")
    if not isinstance(record.get("source_fingerprint"), str) or not HEX64.fullmatch(record["source_fingerprint"]):
        errors.append(f"{path.name}: source_fingerprint must be sha256 hex")
    if not isinstance(record.get("dependency_fingerprints"), dict):
        errors.append(f"{path.name}: dependency_fingerprints must be an object")
    redaction = record.get("redaction_review")
    if not isinstance(redaction, dict) or redaction.get("secrets_redacted") is not True:
        errors.append(f"{path.name}: redaction_review.secrets_redacted must be true")
    if record.get("result") not in {"pass", "fail", "blocked"}:
        errors.append(f"{path.name}: result must be pass, fail, or blocked")
    required = ("tool", "tool_version", "diagnostic_hash", "attempt_key", "recorded_at", "git_snapshot", "content_sha256")
    for field in required:
        if field not in record:
            errors.append(f"{path.name}: missing {field}")
    git_state = record.get("git_snapshot")
    if record.get("result") == "pass" and (not isinstance(git_state, dict) or git_state.get("dirty") is not False):
        errors.append(f"{path.name}: passing evidence requires a clean git worktree")
    if "content_sha256" in record and record.get("content_sha256") != _record_hash(record):
        errors.append(f"{path.name}: content_sha256 does not match record contents")
    if all(field in record for field in ("domain_id", "evidence_class", "tool", "tool_version", "source_fingerprint", "diagnostic_hash")):
        expected_attempt = attempt_key(
            {
                "domain": record["domain_id"],
                "evidence_class": record["evidence_class"],
                "tool": record["tool"],
                "tool_version": record["tool_version"],
                "fingerprint": record["source_fingerprint"],
                "diagnostic_hash": record["diagnostic_hash"],
            }
        )
        if record.get("attempt_key") != expected_attempt:
            errors.append(f"{path.name}: attempt_key does not match evidence identity")
        attempt_file = attempts_root / f"{record['domain_id']}.jsonl"
        if expected_attempt not in existing_keys(attempt_file):
            errors.append(f"{path.name}: attempt_key has no recorded attempt")
    if index_path.is_file() and isinstance(record_id, str):
        index = read_json(index_path).get("records", {})
        if not isinstance(index, dict) or index.get(record_id) != record.get("content_sha256"):
            errors.append(f"{path.name}: evidence index does not match content hash")
    superseded = record.get("superseded_by")
    if superseded is not None and (not isinstance(superseded, str) or not superseded.startswith("EVID-")):
        errors.append(f"{path.name}: superseded_by must reference another evidence record")
    return errors


def validate_all(root: Path = CONTROL_ROOT / "evidence") -> list[str]:
    domains = set(load_domains())
    errors: list[str] = []
    for path in sorted(root.glob("EVID-*.json")):
        try:
            errors.extend(validate_record(path, domains))
        except (OSError, ValueError) as exc:
            errors.append(f"{path.name}: {exc}")
    return errors


def resolve_positive_evidence(
    reference: str,
    domain_id: str,
    fingerprint: str,
    evidence_root: Path = CONTROL_ROOT / "evidence",
    attempts_root: Path = CONTROL_ROOT / "attempts",
    index_path: Path | None = None,
) -> tuple[dict[str, Any] | None, list[str]]:
    if not isinstance(reference, str) or not reference.startswith("EVID-") or Path(reference).name != reference:
        return None, [f"{domain_id}: invalid evidence reference {reference!r}"]
    path = evidence_root / f"{reference}.json"
    if not path.is_file():
        return None, [f"{domain_id}: evidence reference does not resolve: {reference}"]
    try:
        record = read_json(path)
    except (OSError, ValueError) as exc:
        return None, [f"{domain_id}: cannot read evidence {reference}: {exc}"]
    errors = validate_record(path, {domain_id}, attempts_root, index_path or evidence_root / "index.json")
    if record.get("domain_id") != domain_id:
        errors.append(f"{domain_id}: evidence {reference} belongs to another domain")
    if record.get("source_fingerprint") != fingerprint:
        errors.append(f"{domain_id}: evidence {reference} has a stale source fingerprint")
    if record.get("result") != "pass":
        errors.append(f"{domain_id}: positive claim cites non-passing evidence {reference}")
    return record, errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=CONTROL_ROOT / "evidence")
    args = parser.parse_args()
    errors = validate_all(args.root)
    if errors:
        print("\n".join(f"error: {error}" for error in errors), file=sys.stderr)
        return 2
    print("evidence records valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
