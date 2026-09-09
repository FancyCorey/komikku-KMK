"""Resolve the shared KMK workspace from a source checkout or Git worktree."""

from __future__ import annotations

from pathlib import Path


def find_workspace_root(source_root: Path) -> Path:
    """Return the nearest ancestor that owns the private control directory.

    The main checkout is normally a direct child of the workspace, while
    isolated worktrees live under ``workspace/worktrees/<name>``. Falling back
    to the source parent preserves standalone-checkout behavior when the
    private control plane is intentionally absent.
    """

    source_root = source_root.resolve()
    for candidate in source_root.parents:
        if (candidate / "private" / "control").is_dir():
            return candidate
    return source_root.parent
