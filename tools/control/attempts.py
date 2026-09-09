"""Prevent identical terminal attempts from consuming the completion loop."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

from .control_common import CONTROL_ROOT, canonical_json, utc_now


ATTEMPTS_ROOT = CONTROL_ROOT / "attempts"


def attempt_key(values: dict[str, Any]) -> str:
    return hashlib.sha256(canonical_json(values)).hexdigest()


def existing_keys(path: Path) -> set[str]:
    if not path.exists():
        return set()
    keys: set[str] = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        if isinstance(row, dict) and isinstance(row.get("attempt_key"), str):
            keys.add(row["attempt_key"])
    return keys


def attempt_count(path: Path, key: str) -> int:
    if not path.exists():
        return 0
    count = 0
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip() and json.loads(line).get("attempt_key") == key:
            count += 1
    return count


def record_attempt(values: dict[str, Any], path: Path = ATTEMPTS_ROOT / "manual.jsonl") -> tuple[bool, str]:
    key = attempt_key(values)
    if attempt_count(path, key):
        return False, key
    path.parent.mkdir(parents=True, exist_ok=True)
    row = {"attempt_key": key, "terminal": False, "recorded_at": utc_now(), **values}
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(row, sort_keys=True) + "\n")
    return True, key


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("check", "record"))
    parser.add_argument("--domain", required=True)
    parser.add_argument("--evidence-class", required=True)
    parser.add_argument("--tool", required=True)
    parser.add_argument("--tool-version", required=True)
    parser.add_argument("--fingerprint", required=True)
    parser.add_argument("--diagnostic-hash", required=True)
    parser.add_argument("--terminal", action="store_true")
    args = parser.parse_args(argv)
    values = {
        "domain": args.domain,
        "evidence_class": args.evidence_class,
        "tool": args.tool,
        "tool_version": args.tool_version,
        "fingerprint": args.fingerprint,
        "diagnostic_hash": args.diagnostic_hash,
    }
    key = attempt_key(values)
    path = ATTEMPTS_ROOT / f"{args.domain}.jsonl"
    if attempt_count(path, key):
        print("identical attempt already recorded", file=sys.stderr)
        return 4
    if args.action == "record":
        recorded, _ = record_attempt(values, path)
        if not recorded:
            print("identical attempt already recorded", file=sys.stderr)
            return 4
    print(key)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
