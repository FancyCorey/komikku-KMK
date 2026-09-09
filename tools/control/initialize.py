"""Create conservative false/unclaimed state for every declared domain."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from .control_common import CONTROL_ROOT, STATE_ROOT, dependency_fingerprint_map, fingerprint_domain, git_snapshot, load_domains, utc_now, write_json


def initial_state(domain: dict) -> dict:
    fingerprint, _ = fingerprint_domain(domain)
    statuses = {
        name: {"value": False, "as_of": None, "evidence": []}
        for name in ("implemented", "host_tested", "device_tested", "release_ready")
    }
    return {
        "schema_version": "1.0.0",
        "domain_id": domain["id"],
        "owned_fingerprint": fingerprint,
        "dependency_fingerprints": dependency_fingerprint_map(domain),
        "git_snapshot": git_snapshot(),
        "statuses": statuses,
        "blocked": None,
        "derived": {"stale": False, "checked_at": utc_now()},
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--force", action="store_true", help="replace only controller-generated state files")
    args = parser.parse_args()
    for domain_id, domain in load_domains().items():
        path = STATE_ROOT / f"{domain_id}.state.json"
        if path.exists() and not args.force:
            continue
        write_json(path, initial_state(domain))
    controller = CONTROL_ROOT / "controller.json"
    if not controller.exists() or args.force:
        write_json(
            controller,
            {
                "schema_version": "1.0.0",
                "topology_version": "1.0.0",
                "terminal_state": "OPEN",
                "active_domain": None,
                "last_progress": utc_now(),
                "no_op_passes": 0,
                "no_op_limit": 2,
                "certificates": [],
            },
        )
    print(f"initialized {len(load_domains())} conservative domain states")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
