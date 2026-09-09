"""Issue a closure certificate only for a fully validated current domain."""

from __future__ import annotations

import argparse
from pathlib import Path

from .control_common import CONTROL_ROOT, STATE_ROOT, dependency_fingerprint_map, fingerprint_domain, git_snapshot, load_domain, read_json, utc_now, write_json
from .validate_state import validate_state


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("domain")
    args = parser.parse_args(argv)
    domain = load_domain(args.domain)
    state = read_json(STATE_ROOT / f"{args.domain}.state.json")
    errors = validate_state(STATE_ROOT / f"{args.domain}.state.json", domain)
    if git_snapshot().get("dirty"):
        errors.append("cannot issue a certificate for a dirty git worktree")
    if not state["statuses"]["release_ready"]["value"]:
        errors.append("release_ready must be true before certificate issuance")
    if errors:
        for error in errors:
            print(f"error: {error}")
        return 2
    fingerprint, _ = fingerprint_domain(domain)
    certificate_id = f"CERT-{args.domain}"
    write_json(
        CONTROL_ROOT / "certificates" / f"{certificate_id}.cert.json",
        {
            "certificate_id": certificate_id,
            "schema_version": "1.0.0",
            "domain_id": args.domain,
            "issued_for_fingerprint": fingerprint,
            "dependency_fingerprints_at_issuance": dependency_fingerprint_map(domain),
            "status_snapshot": state["statuses"],
            "evidence": sorted({reference for status in state["statuses"].values() for reference in status["evidence"]}),
            "issued_at": utc_now(),
            "issued_by": "tools.control.issue_certificate",
            "voided_at": None,
        },
    )
    print(certificate_id)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
