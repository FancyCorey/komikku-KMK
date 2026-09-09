"""Enforce controller terminal and no-progress rules."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from .control_common import CONTROL_ROOT, STATE_ROOT, load_domains, read_json, utc_now, write_json
from .validate_evidence import validate_all as validate_evidence
from .validate_certificate import validate_certificate_id
from .validate_state import validate_all


TERMINAL_STATES = {"OPEN", "REOPENED", "BLOCKED", "COMPLETE"}


def validate_controller(controller_path: Path = CONTROL_ROOT / "controller.json") -> list[str]:
    controller = read_json(controller_path)
    errors: list[str] = []
    terminal_state = controller.get("terminal_state")
    if terminal_state not in TERMINAL_STATES:
        errors.append(f"invalid terminal_state: {terminal_state}")
    no_op_passes, no_op_limit = controller.get("no_op_passes"), controller.get("no_op_limit")
    if not isinstance(no_op_passes, int) or no_op_passes < 0 or not isinstance(no_op_limit, int) or no_op_limit < 1:
        errors.append("no-op counters require integer values and a positive limit")
    elif no_op_passes >= no_op_limit:
        errors.append("no-op budget exceeded")
    if terminal_state == "COMPLETE":
        if not controller.get("certificates"):
            errors.append("COMPLETE requires at least one closure certificate")
        else:
            domains = load_domains()
            certificate_domains: set[str] = set()
            for certificate in controller["certificates"]:
                errors.extend(validate_certificate_id(certificate, domains))
                if certificate.startswith("CERT-"):
                    certificate_domains.add(certificate.removeprefix("CERT-"))
            errors.extend(f"COMPLETE is missing a certificate for {domain_id}" for domain_id in set(domains) - certificate_domains)
        errors.extend(validate_all())
        errors.extend(validate_evidence())
        for domain_id in load_domains():
            state = read_json(STATE_ROOT / f"{domain_id}.state.json")
            if not state["statuses"]["release_ready"]["value"]:
                errors.append(f"COMPLETE requires release_ready for {domain_id}")
    if terminal_state == "BLOCKED":
        blockers = controller.get("blockers")
        if not isinstance(blockers, list) or not blockers:
            errors.append("BLOCKED requires explicit blocker records")
    return errors


def record_no_op(controller_path: Path = CONTROL_ROOT / "controller.json") -> int:
    controller = read_json(controller_path)
    controller["no_op_passes"] = int(controller.get("no_op_passes", 0)) + 1
    controller["last_progress"] = controller.get("last_progress") or utc_now()
    write_json(controller_path, controller)
    return controller["no_op_passes"]


def record_progress(controller_path: Path = CONTROL_ROOT / "controller.json") -> None:
    controller = read_json(controller_path)
    controller["no_op_passes"] = 0
    controller["last_progress"] = utc_now()
    write_json(controller_path, controller)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("validate", "record-no-op", "record-progress"))
    args = parser.parse_args(argv)
    if args.action == "record-no-op":
        count = record_no_op()
        controller = read_json(CONTROL_ROOT / "controller.json")
        print(f"no-op passes: {count}/{controller['no_op_limit']}")
        return 4 if count >= controller["no_op_limit"] else 0
    if args.action == "record-progress":
        record_progress()
        print("progress recorded")
        return 0
    errors = validate_controller()
    if errors:
        print("\n".join(f"error: {error}" for error in errors), file=sys.stderr)
        return 2
    print("controller gate valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
