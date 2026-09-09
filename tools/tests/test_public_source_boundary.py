from __future__ import annotations

import re
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE_SUFFIXES = {
    ".gradle",
    ".java",
    ".kt",
    ".kts",
    ".properties",
    ".py",
    ".sq",
    ".sqm",
    ".toml",
    ".xml",
    ".yaml",
    ".yml",
}
ROOT_BUILD_FILES = {"build.gradle.kts", "settings.gradle.kts", "gradle.properties"}

PROHIBITED_COMMENT_PATTERNS = {
    "internal-doc-tree": re.compile(r"docs[\\/](?:community|recommendations)[\\/]", re.IGNORECASE),
    "private-tree": re.compile(r"(?:^|[\\/])private[\\/]", re.IGNORECASE),
    "internal-report-name": re.compile(
        r"\bKMK_[A-Z0-9_]*(?:PLAN|IMPLEMENTATION|AUDIT|REPORT)[A-Z0-9_]*(?:\.md)?\b",
    ),
    "assistant-provenance": re.compile(
        r"(?:^|[^A-Za-z0-9])(?:Claude|Codex)(?:$|[^A-Za-z0-9])",
        re.IGNORECASE,
    ),
    "device-audit-provenance": re.compile(r"\bADB\b|UI_AUDIT_NOTES\.md"),
    "host-user-path": re.compile(r"(?:[A-Za-z]:[\\/]+Users[\\/]|/home/)", re.IGNORECASE),
}


def comment_segments(line: str, in_block: str | None) -> tuple[list[str], str | None]:
    """Return comment text while ignoring comment-like tokens inside quoted strings."""
    segments: list[str] = []
    index = 0
    quote: str | None = None
    escaped = False

    while index < len(line):
        if in_block:
            end_token = "-->" if in_block == "xml" else "*/"
            end = line.find(end_token, index)
            if end == -1:
                segments.append(line[index:])
                return segments, in_block
            segments.append(line[index:end])
            index = end + len(end_token)
            in_block = None
            continue

        char = line[index]
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            index += 1
            continue

        if char in {'"', "'"}:
            quote = char
            index += 1
            continue
        if line.startswith("//", index):
            segments.append(line[index + 2 :])
            break
        if line.startswith("/*", index):
            in_block = "code"
            index += 2
            continue
        if line.startswith("<!--", index):
            in_block = "xml"
            index += 4
            continue
        if line.startswith("--", index):
            segments.append(line[index + 2 :])
            break
        if char == "#" and not line[:index].strip():
            segments.append(line[index + 1 :])
            break
        index += 1

    return segments, in_block


def boundary_violations(path: Path) -> list[str]:
    violations: list[str] = []
    in_block: str | None = None
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        segments, in_block = comment_segments(line, in_block)
        comment = " ".join(segments)
        for marker, pattern in PROHIBITED_COMMENT_PATTERNS.items():
            if pattern.search(comment):
                violations.append(f"{path.relative_to(ROOT)}:{line_number}:{marker}")
    return violations


def retained_source_paths() -> list[Path]:
    tracked = subprocess.check_output(
        ["git", "-C", str(ROOT), "ls-files", "-z"],
    ).decode("utf-8").split("\0")
    paths = []
    for relative in tracked:
        if not relative:
            continue
        normalized = relative.replace("\\", "/")
        path = ROOT / relative
        if path.suffix.lower() not in SOURCE_SUFFIXES:
            continue
        is_public_source = (
            "/src/" in f"/{normalized}"
            or path.name in ROOT_BUILD_FILES
            or normalized.startswith("tools/")
        )
        if is_public_source and path.is_file():
            paths.append(path)
    return sorted(paths)


class PublicSourceBoundaryTest(unittest.TestCase):
    def test_comment_parser_flags_comments_but_ignores_strings(self) -> None:
        segments, block = comment_segments('val raw = "ADB /private/url" // Codex audit', None)
        self.assertEqual([" Codex audit"], segments)
        self.assertIsNone(block)

        segments, block = comment_segments("/** private/report */", None)
        self.assertEqual(["* private/report "], segments)
        self.assertIsNone(block)

        segments, block = comment_segments('<!-- docs/community/plan.md -->', None)
        self.assertEqual([" docs/community/plan.md "], segments)
        self.assertIsNone(block)

        segments, block = comment_segments("-- KMK_" + "CLAUDE_INTERNAL_PLAN", None)
        self.assertEqual([" KMK_" + "CLAUDE_INTERNAL_PLAN"], segments)
        self.assertIsNone(block)

        segments, block = comment_segments("# Codex audit", None)
        self.assertEqual([" Codex audit"], segments)
        self.assertIsNone(block)

    def test_patterns_cover_underscore_names_and_extensionless_artifacts(self) -> None:
        assistant_marker = "KMK_" + "CLAUDE_INTERNAL_PHASE"
        report_marker = "KMK_INTERNAL_IMPLEMENTATION_PLAN"
        self.assertIsNotNone(PROHIBITED_COMMENT_PATTERNS["assistant-provenance"].search(assistant_marker))
        self.assertIsNotNone(PROHIBITED_COMMENT_PATTERNS["internal-report-name"].search(report_marker))

        catalogue_value = 'val artist = "artist:jean-claude pertuze"'
        segments, _ = comment_segments(catalogue_value, None)
        self.assertEqual([], segments)

    def test_retained_public_source_has_no_internal_provenance_comments(self) -> None:
        violations: list[str] = []
        for path in retained_source_paths():
            violations.extend(boundary_violations(path))

        self.assertEqual([], violations, "\n" + "\n".join(violations))


if __name__ == "__main__":
    unittest.main()
