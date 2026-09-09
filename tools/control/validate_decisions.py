"""Consume decision recheck triggers instead of treating them as prose."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from .control_common import CONTROL_ROOT, fingerprint_domain, load_domains, read_json


def decision_review(path: Path) -> list[str]:
    decision = read_json(path)
    baseline = decision.get("created_against")
    if not isinstance(baseline, dict):
        return [f"{path.name}: needs review because created_against is missing"]
    controller = read_json(CONTROL_ROOT / "controller.json")
    issues: list[str] = []
    if baseline.get("schema_version") != controller.get("schema_version"):
        issues.append(f"{path.name}: controller schema changed")
    if baseline.get("topology_version") != controller.get("topology_version"):
        issues.append(f"{path.name}: topology changed")
    expected = baseline.get("domain_fingerprints", {})
    for domain_id, domain in load_domains().items():
        current, _ = fingerprint_domain(domain)
        if expected.get(domain_id) != current:
            issues.append(f"{path.name}: domain fingerprint changed: {domain_id}")
    return issues


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=CONTROL_ROOT / "decisions")
    args = parser.parse_args()
    issues: list[str] = []
    for path in sorted(args.root.glob("DR-*.json")):
        try:
            issues.extend(decision_review(path))
        except (OSError, ValueError) as exc:
            issues.append(f"{path.name}: {exc}")
    if issues:
        print("\n".join(f"review: {issue}" for issue in issues), file=sys.stderr)
        return 4
    print("decision triggers clear")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
