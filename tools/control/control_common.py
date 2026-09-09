"""Shared, dependency-free primitives for the completion control plane.

The controller is intentionally conservative: it fingerprints files, but it
never treats prose, an old APK, or a prior status claim as current evidence.
"""

from __future__ import annotations

import hashlib
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

from tools.workspace_paths import find_workspace_root


SOURCE_ROOT = Path(__file__).resolve().parents[2]
WORKSPACE_ROOT = find_workspace_root(SOURCE_ROOT)
CONTROL_ROOT = WORKSPACE_ROOT / "private" / "control"
DOMAINS_ROOT = CONTROL_ROOT / "domains"
STATE_ROOT = CONTROL_ROOT / "state"


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"Expected an object in {path}")
    return value


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def canonical_json(value: Any) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode("utf-8")


def path_root(pattern: str) -> Path:
    normalized = pattern.replace("\\", "/")
    if normalized.startswith("/") or ":" in normalized or ".." in Path(normalized).parts:
        raise ValueError(f"control path pattern must be workspace-relative: {pattern}")
    if pattern.startswith("private/") or pattern.startswith("komikku-source/"):
        return WORKSPACE_ROOT
    return SOURCE_ROOT


def workspace_relative(path: Path) -> str:
    return path.resolve().relative_to(WORKSPACE_ROOT.resolve()).as_posix()


def iter_files(patterns: Iterable[str]) -> list[Path]:
    files: dict[str, Path] = {}
    for pattern in patterns:
        root = path_root(pattern)
        normalized = pattern.replace("\\", "/")
        glob_pattern = normalized[:-3] + "/**/*" if normalized.endswith("/**") else normalized
        for path in root.glob(glob_pattern):
            if path.is_file():
                files[workspace_relative(path)] = path
    return [files[key] for key in sorted(files)]


def fingerprint_files(files: Iterable[Path]) -> str:
    digest = hashlib.sha256()
    for path in sorted(files, key=workspace_relative):
        digest.update(workspace_relative(path).encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def fingerprint_patterns(patterns: Iterable[str]) -> tuple[str, list[str]]:
    files = iter_files(patterns)
    return fingerprint_files(files), [workspace_relative(path) for path in files]


def domain_files(domain: dict[str, Any]) -> list[Path]:
    included = {workspace_relative(path): path for path in iter_files(domain.get("owned_globs", []))}
    excluded = {workspace_relative(path) for path in iter_files(domain.get("exclude_globs", []))}
    return [included[key] for key in sorted(included.keys() - excluded)]


def fingerprint_domain(domain: dict[str, Any]) -> tuple[str, list[str]]:
    files = domain_files(domain)
    return fingerprint_files(files), [workspace_relative(path) for path in files]


def fingerprint_map(patterns: Iterable[str]) -> dict[str, str]:
    return {pattern: fingerprint_patterns((pattern,))[0] for pattern in patterns}


def graph_dependency_map(domain_id: str) -> dict[str, str]:
    graph = read_json(CONTROL_ROOT / "graph.json")
    domains = load_domains()
    result: dict[str, str] = {}
    for edge in graph.get("edges", []):
        if isinstance(edge, dict) and edge.get("to") == domain_id:
            dependency_id = edge.get("from")
            if dependency_id in domains:
                result[f"domain:{dependency_id}"] = fingerprint_domain(domains[dependency_id])[0]
    return result


def dependency_fingerprint_map(domain: dict[str, Any]) -> dict[str, str]:
    result = {f"path:{key}": value for key, value in fingerprint_map(domain.get("read_only_dependencies", [])).items()}
    result.update(graph_dependency_map(domain["id"]))
    return result


def git_snapshot() -> dict[str, Any]:
    def run(*args: str) -> str:
        return subprocess.check_output(("git", *args), cwd=SOURCE_ROOT, text=True, stderr=subprocess.STDOUT).strip()

    status = run("status", "--porcelain", "--untracked-files=all")
    return {
        "head": run("rev-parse", "HEAD"),
        "dirty": bool(status),
        "status_hash": hashlib.sha256(status.encode("utf-8")).hexdigest(),
    }


def domain_paths(domain_id: str) -> tuple[Path, Path]:
    return DOMAINS_ROOT / f"{domain_id}.json", STATE_ROOT / f"{domain_id}.state.json"


def load_domain(domain_id: str) -> dict[str, Any]:
    path, _ = domain_paths(domain_id)
    return read_json(path)


def load_domains() -> dict[str, dict[str, Any]]:
    domains: dict[str, dict[str, Any]] = {}
    for path in sorted(DOMAINS_ROOT.glob("DOM-*.json")):
        domain = read_json(path)
        domain_id = domain.get("id")
        if not isinstance(domain_id, str) or not domain_id:
            raise ValueError(f"Domain {path} has no non-empty id")
        if domain_id in domains:
            raise ValueError(f"Duplicate domain id {domain_id}")
        domains[domain_id] = domain
    return domains
