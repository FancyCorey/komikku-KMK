"""Executable wrapper for the corrected hardened helper entry point."""

from __future__ import annotations

import sys
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from tools.android_ui_hardened_entry import main


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
