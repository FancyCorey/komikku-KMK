"""Validate the completion dependency graph and domain contracts."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from .control_common import CONTROL_ROOT, domain_files, load_domains, read_json, workspace_relative


def topological_order(graph: dict[str, Any], domain_ids: set[str]) -> list[str]:
    edges = graph.get("edges")
    if not isinstance(edges, list):
        raise ValueError("graph requires an edges array")
    outgoing = {domain_id: set() for domain_id in domain_ids}
    incoming = {domain_id: set() for domain_id in domain_ids}
    for edge in edges:
        if not isinstance(edge, dict):
            raise ValueError("every graph edge must be an object")
        source, target = edge.get("from"), edge.get("to")
        if source not in domain_ids or target not in domain_ids:
            raise ValueError(f"edge references an unknown domain: {source} -> {target}")
        outgoing[source].add(target)
        incoming[target].add(source)
    ready = sorted(domain_id for domain_id, dependencies in incoming.items() if not dependencies)
    result: list[str] = []
    while ready:
        current = ready.pop(0)
        result.append(current)
        for target in sorted(outgoing[current]):
            incoming[target].remove(current)
            if not incoming[target]:
                ready.append(target)
                ready.sort()
    if len(result) != len(domain_ids):
        raise ValueError("completion graph contains a cycle")
    return result


def validate(graph_path: Path = CONTROL_ROOT / "graph.json") -> list[str]:
    errors: list[str] = []
    try:
        domains = load_domains()
    except (OSError, ValueError) as exc:
        return [str(exc)]
    ownership: dict[str, str] = {}
    for domain_id, domain in domains.items():
        if domain.get("schema_version") != "1.0.0":
            errors.append(f"{domain_id}: unsupported schema_version")
        if not isinstance(domain.get("owned_globs"), list) or not domain["owned_globs"]:
            errors.append(f"{domain_id}: owned_globs must be non-empty")
        if not isinstance(domain.get("contract_surface"), list) or not domain["contract_surface"]:
            errors.append(f"{domain_id}: contract_surface must be non-empty")
        if not isinstance(domain.get("required_evidence"), list) or not domain["required_evidence"]:
            errors.append(f"{domain_id}: required_evidence must be non-empty")
        try:
            files = domain_files(domain)
            if not files:
                errors.append(f"{domain_id}: owned_globs resolve to no files")
            for path in files:
                relative = workspace_relative(path)
                previous = ownership.get(relative)
                if previous and previous != domain_id:
                    errors.append(f"ownership collision: {relative} is owned by {previous} and {domain_id}")
                ownership[relative] = domain_id
        except (OSError, ValueError) as exc:
            errors.append(f"{domain_id}: invalid path pattern: {exc}")
    try:
        graph = read_json(graph_path)
        topological_order(graph, set(domains))
    except (OSError, json.JSONDecodeError, ValueError) as exc:
        errors.append(str(exc))
    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--graph", type=Path, default=CONTROL_ROOT / "graph.json")
    args = parser.parse_args(argv)
    errors = validate(args.graph)
    if errors:
        print("\n".join(f"error: {error}" for error in errors), file=sys.stderr)
        return 2
    print("control graph valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
