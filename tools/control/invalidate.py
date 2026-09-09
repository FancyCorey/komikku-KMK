"""Recompute fingerprints and mark derived claims stale without deleting evidence."""

from __future__ import annotations

import argparse

from .control_common import STATE_ROOT, dependency_fingerprint_map, fingerprint_domain, git_snapshot, load_domains, read_json, utc_now, write_json


def void_stale_certificates(domain_id: str) -> None:
    for path in (STATE_ROOT.parent / "certificates").glob("CERT-*.cert.json"):
        certificate = read_json(path)
        if certificate.get("domain_id") == domain_id and certificate.get("voided_at") is None:
            certificate["voided_at"] = utc_now()
            write_json(path, certificate)


def invalidate() -> list[str]:
    stale: list[str] = []
    for domain_id, domain in load_domains().items():
        state_path = STATE_ROOT / f"{domain_id}.state.json"
        if not state_path.is_file():
            continue
        state = read_json(state_path)
        current, _ = fingerprint_domain(domain)
        dependencies = dependency_fingerprint_map(domain)
        previous = state.get("owned_fingerprint")
        previous_dependencies = state.get("dependency_fingerprints", {})
        state["owned_fingerprint"] = current
        state["dependency_fingerprints"] = dependencies
        state["git_snapshot"] = git_snapshot()
        state.setdefault("derived", {})["checked_at"] = utc_now()
        state["derived"]["stale"] = previous != current or previous_dependencies != dependencies
        if state["derived"]["stale"]:
            stale.append(domain_id)
            void_stale_certificates(domain_id)
        write_json(state_path, state)
    return stale


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.parse_args()
    stale = invalidate()
    print("stale domains: " + (", ".join(stale) if stale else "none"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
