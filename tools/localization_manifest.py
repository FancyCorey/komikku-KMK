#!/usr/bin/env python3
"""Build a deterministic KMK locale coverage and fallback manifest."""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path
from typing import Iterable


SCHEMA_VERSION = 1
RESOURCE_REFERENCE = re.compile(r"\bKMR\.(strings|plurals)\.([A-Za-z_][A-Za-z0-9_]*)\b")
PLACEHOLDER = re.compile(
    r"%(?:(?P<index>\d+)\$)?[-#+ 0,(<]*(?:\d+)?(?:\.\d+)?(?P<type>[A-Za-z])",
)
FEATURE_PREFIXES = (
    "taste_",
    "rec_",
    "source_evaluation_",
    "shizuku_",
    "rated_manga_",
    "same_manga_",
    "best_version_",
    "kmk_recs_",
    "eval_",
    "action_history_",
    "reader_schedule_",
    "extension_",
    "pref_rec_",
    "pref_hide_for_you_tab",
    "manga_preference_",
    "not_interested_",
)
SOURCE_SUFFIXES = {".kt", ".kts", ".java", ".xml"}


class ManifestError(ValueError):
    pass


def _element_text(element: ET.Element) -> str:
    return "".join(element.itertext())


def placeholder_signature(value: str) -> list[str]:
    """Return a stable printf-style argument signature, preserving duplicates."""
    return sorted(
        f"{match.group('index') or 'auto'}:{match.group('type').lower()}"
        for match in PLACEHOLDER.finditer(value.replace("%%", ""))
    )


def _parse_xml(path: Path) -> ET.Element:
    try:
        return ET.parse(path).getroot()
    except (ET.ParseError, OSError) as error:
        raise ManifestError(f"Unable to parse {path}: {error}") from error


def parse_resources(directory: Path) -> tuple[dict[str, str], dict[str, dict[str, str]]]:
    strings: dict[str, str] = {}
    plurals: dict[str, dict[str, str]] = {}
    for path in sorted(directory.glob("*.xml")):
        root = _parse_xml(path)
        for element in root.findall("string"):
            name = element.attrib.get("name", "")
            if not name:
                raise ManifestError(f"Unnamed string in {path}")
            if name in strings:
                raise ManifestError(f"Duplicate string {name} in {directory}")
            strings[name] = _element_text(element)
        for element in root.findall("plurals"):
            name = element.attrib.get("name", "")
            if not name:
                raise ManifestError(f"Unnamed plurals in {path}")
            if name in plurals:
                raise ManifestError(f"Duplicate plurals {name} in {directory}")
            quantities: dict[str, str] = {}
            for item in element.findall("item"):
                quantity = item.attrib.get("quantity", "")
                if not quantity:
                    raise ManifestError(f"Plural {name} has an unnamed quantity in {path}")
                if quantity in quantities:
                    raise ManifestError(f"Plural {name} repeats {quantity} in {path}")
                quantities[quantity] = _element_text(item)
            plurals[name] = quantities
    return strings, plurals


def _source_files(root: Path) -> Iterable[Path]:
    if not root.exists():
        return ()
    return (
        path
        for path in root.rglob("*")
        if path.is_file() and path.suffix.lower() in SOURCE_SUFFIXES
    )


def scan_references(repo_root: Path, source_root: Path) -> dict[tuple[str, str], list[str]]:
    references: dict[tuple[str, str], set[str]] = defaultdict(set)
    for path in sorted(_source_files(source_root)):
        try:
            source = path.read_text(encoding="utf-8")
        except UnicodeDecodeError as error:
            raise ManifestError(f"Source is not UTF-8: {path}") from error
        relative = path.relative_to(repo_root).as_posix()
        for match in RESOURCE_REFERENCE.finditer(source):
            references[(match.group(1), match.group(2))].add(relative)
    return {key: sorted(paths) for key, paths in sorted(references.items())}


def _plural_signatures(items: dict[str, str]) -> dict[str, list[str]]:
    return {
        quantity: placeholder_signature(value)
        for quantity, value in sorted(items.items())
    }


def _is_deferred_numeric(value: str, production_paths: list[str]) -> bool:
    return not production_paths and any(token.endswith(":d") for token in placeholder_signature(value))


def build_manifest(repo_root: Path) -> dict[str, object]:
    repo_root = repo_root.resolve()
    resource_root = repo_root / "i18n-kmk" / "src" / "commonMain" / "moko-resources"
    base_root = resource_root / "base"
    base_strings, base_plurals = parse_resources(base_root)
    namespace_overlap = sorted(base_strings.keys() & base_plurals.keys())
    if namespace_overlap:
        raise ManifestError(f"Base string/plural namespace overlap: {namespace_overlap}")

    production_refs = scan_references(repo_root, repo_root / "app" / "src" / "main")
    test_refs = scan_references(repo_root, repo_root / "app" / "src" / "test")
    all_names = sorted(base_strings.keys() | base_plurals.keys())
    selected: dict[tuple[str, str], list[str]] = {}

    for name in all_names:
        kind = "strings" if name in base_strings else "plurals"
        reasons: list[str] = []
        if name.startswith(FEATURE_PREFIXES):
            reasons.append("feature-prefix")
        if production_refs.get((kind, name)):
            reasons.append("production-reference")
        if kind == "strings" and _is_deferred_numeric(base_strings[name], production_refs.get((kind, name), [])):
            reasons.append("deferred-numeric-candidate")
        if reasons:
            selected[(kind, name)] = sorted(reasons)

    selected_string_names = sorted(name for kind, name in selected if kind == "strings")
    selected_plural_names = sorted(name for kind, name in selected if kind == "plurals")
    feature_string_names = sorted(
        name for (kind, name), reasons in selected.items()
        if kind == "strings" and "feature-prefix" in reasons
    )
    feature_plural_names = sorted(
        name for (kind, name), reasons in selected.items()
        if kind == "plurals" and "feature-prefix" in reasons
    )
    contract_errors: list[dict[str, object]] = []
    resources: list[dict[str, object]] = []

    for kind, name in sorted(selected):
        production_paths = production_refs.get((kind, name), [])
        test_paths = test_refs.get((kind, name), [])
        if production_paths:
            static_status = "production-reference"
        elif test_paths:
            static_status = "test-only-candidate"
        else:
            static_status = "no-static-reference-candidate"
        record: dict[str, object] = {
            "kind": kind,
            "name": name,
            "selection_reasons": selected[(kind, name)],
            "static_reference_status": static_status,
            "dead_code_proven": False,
            "production_references": production_paths,
            "test_references": test_paths,
        }
        if kind == "strings":
            record["base_placeholder_signature"] = placeholder_signature(base_strings[name])
        else:
            categories = sorted(base_plurals[name])
            signatures = _plural_signatures(base_plurals[name])
            record["base_plural_categories"] = categories
            record["base_placeholder_signatures"] = signatures
            if "other" not in categories:
                contract_errors.append({"kind": "base-plural-missing-other", "resource": name})
            expected = signatures.get("other", [])
            for category, signature in signatures.items():
                if signature != expected:
                    contract_errors.append(
                        {
                            "kind": "base-plural-placeholder-mismatch",
                            "resource": name,
                            "category": category,
                            "expected": expected,
                            "actual": signature,
                        },
                    )
        resources.append(record)

    locales: list[dict[str, object]] = []
    for locale_root in sorted(path for path in resource_root.iterdir() if path.is_dir() and path.name != "base"):
        locale_strings, locale_plurals = parse_resources(locale_root)
        extra_strings = sorted(locale_strings.keys() - base_strings.keys())
        extra_plurals = sorted(locale_plurals.keys() - base_plurals.keys())
        overridden_strings = sorted(locale_strings.keys() & set(selected_string_names))
        overridden_plurals = sorted(locale_plurals.keys() & set(selected_plural_names))
        placeholder_mismatches: list[dict[str, object]] = []
        plural_contracts: list[dict[str, object]] = []

        for name in overridden_strings:
            expected = placeholder_signature(base_strings[name])
            actual = placeholder_signature(locale_strings[name])
            if actual != expected:
                placeholder_mismatches.append(
                    {"resource": name, "expected": expected, "actual": actual},
                )
        for name in overridden_plurals:
            categories = sorted(locale_plurals[name])
            signatures = _plural_signatures(locale_plurals[name])
            expected = _plural_signatures(base_plurals[name]).get("other", [])
            mismatched = {
                category: signature
                for category, signature in signatures.items()
                if signature != expected
            }
            plural_contracts.append(
                {
                    "resource": name,
                    "categories": categories,
                    "placeholder_signatures": signatures,
                    "missing_other": "other" not in categories,
                    "mismatched_signatures": mismatched,
                },
            )

        for extra in extra_strings:
            contract_errors.append({"kind": "locale-string-without-base", "locale": locale_root.name, "resource": extra})
        for extra in extra_plurals:
            contract_errors.append({"kind": "locale-plural-without-base", "locale": locale_root.name, "resource": extra})
        for mismatch in placeholder_mismatches:
            contract_errors.append({"kind": "locale-string-placeholder-mismatch", "locale": locale_root.name, **mismatch})
        for contract in plural_contracts:
            if contract["missing_other"]:
                contract_errors.append(
                    {"kind": "locale-plural-missing-other", "locale": locale_root.name, "resource": contract["resource"]},
                )
            if contract["mismatched_signatures"]:
                contract_errors.append(
                    {
                        "kind": "locale-plural-placeholder-mismatch",
                        "locale": locale_root.name,
                        "resource": contract["resource"],
                        "actual": contract["mismatched_signatures"],
                    },
                )

        locales.append(
            {
                "locale": locale_root.name,
                "all_override_string_count": len(locale_strings),
                "all_override_plural_count": len(locale_plurals),
                "overridden_strings": overridden_strings,
                "missing_strings": sorted(set(selected_string_names) - locale_strings.keys()),
                "overridden_plurals": overridden_plurals,
                "missing_plurals": sorted(set(selected_plural_names) - locale_plurals.keys()),
                "feature_prefix_overridden_strings": sorted(locale_strings.keys() & set(feature_string_names)),
                "feature_prefix_missing_strings": sorted(set(feature_string_names) - locale_strings.keys()),
                "feature_prefix_overridden_plurals": sorted(locale_plurals.keys() & set(feature_plural_names)),
                "feature_prefix_missing_plurals": sorted(set(feature_plural_names) - locale_plurals.keys()),
                "extra_strings_without_base": extra_strings,
                "extra_plurals_without_base": extra_plurals,
                "placeholder_mismatches": placeholder_mismatches,
                "plural_contracts": plural_contracts,
            },
        )

    deferred_numeric = sorted(
        record["name"]
        for record in resources
        if "deferred-numeric-candidate" in record["selection_reasons"]
    )
    return {
        "schema_version": SCHEMA_VERSION,
        "selection_contract": {
            "feature_prefixes": list(FEATURE_PREFIXES),
            "include_all_production_kmr_references": True,
            "include_unowned_integer_format_strings": True,
            "static_reference_absence_is_dead_code_proof": False,
        },
        "summary": {
            "base_string_count": len(base_strings),
            "base_plural_count": len(base_plurals),
            "locale_count": len(locales),
            "selected_string_count": len(selected_string_names),
            "selected_plural_count": len(selected_plural_names),
            "feature_prefix_string_count": len(feature_string_names),
            "feature_prefix_plural_count": len(feature_plural_names),
            "deferred_numeric_candidate_count": len(deferred_numeric),
            "contract_error_count": len(contract_errors),
        },
        "deferred_numeric_candidates": deferred_numeric,
        "resources": resources,
        "locales": locales,
        "contract_errors": sorted(contract_errors, key=lambda item: json.dumps(item, sort_keys=True)),
    }


def render_manifest(manifest: dict[str, object]) -> str:
    return json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output", type=Path)
    parser.add_argument("--check-output", action="store_true")
    parser.add_argument("--fail-on-contract-errors", action="store_true")
    args = parser.parse_args(argv)

    manifest = build_manifest(args.root)
    rendered = render_manifest(manifest)
    if args.output:
        if args.check_output:
            if not args.output.is_file() or args.output.read_text(encoding="utf-8") != rendered:
                print(f"Manifest differs from {args.output}", file=sys.stderr)
                return 1
        else:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(rendered, encoding="utf-8", newline="\n")
    else:
        sys.stdout.write(rendered)
    if args.fail_on_contract_errors and manifest["contract_errors"]:
        print(f"Found {len(manifest['contract_errors'])} resource contract errors", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
