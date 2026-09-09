"""Print the current source fingerprint for one declared domain."""

from __future__ import annotations

import argparse
import json

from .control_common import fingerprint_domain, git_snapshot, load_domain


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("domain")
    args = parser.parse_args(argv)
    domain = load_domain(args.domain)
    fingerprint, files = fingerprint_domain(domain)
    print(json.dumps({"domain_id": args.domain, "fingerprint": fingerprint, "files": files, "git_snapshot": git_snapshot()}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
