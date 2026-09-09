import json
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from tools.localization_manifest import build_manifest, render_manifest


SOURCE_ROOT = Path(__file__).parents[2]
DEFERRED_NUMERIC = {
    "best_version_sample_size_label",
    "ocr_index_status",
    "ocr_indexing_progress",
    "ocr_notification_complete",
    "ocr_pages_failed",
    "ocr_pages_indexed",
    "quality_signal_history_record_count",
    "rated_manga_undo_cleared",
    "rated_manga_undo_not_interested",
    "rec_for_you_action_all_failed",
    "rec_for_you_action_partial_failure",
    "rec_for_you_action_success",
    "rec_settings_summary_same_manga_matching",
    "source_quality_avoided_for_you_count",
}
KNOWN_TRANSLATION_CONTRACT_MISMATCHES = set()


class LocalizationManifestTests(unittest.TestCase):
    def _fixture(self, root: Path, locale_string: str = "Count %1$s") -> None:
        base = root / "i18n-kmk" / "src" / "commonMain" / "moko-resources" / "base"
        locale = base.parent / "fr"
        main = root / "app" / "src" / "main" / "java"
        test = root / "app" / "src" / "test" / "java"
        base.mkdir(parents=True)
        locale.mkdir()
        main.mkdir(parents=True)
        test.mkdir(parents=True)
        (base / "strings.xml").write_text(
            """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="rec_live">Count %1$d</string>
    <string name="numeric_orphan">Orphan %1$d</string>
    <string name="unselected">Plain</string>
</resources>
""",
            encoding="utf-8",
        )
        (base / "plurals.xml").write_text(
            """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <plurals name="rec_items">
        <item quantity="one">%1$d item</item>
        <item quantity="other">%1$d items</item>
    </plurals>
</resources>
""",
            encoding="utf-8",
        )
        (locale / "strings.xml").write_text(
            f"""<?xml version="1.0" encoding="utf-8"?>
<resources><string name="rec_live">{locale_string}</string></resources>
""",
            encoding="utf-8",
        )
        (main / "Owner.kt").write_text("val x = KMR.strings.rec_live\nval y = KMR.plurals.rec_items\n", encoding="utf-8")
        (test / "OnlyTest.kt").write_text("val x = KMR.strings.numeric_orphan\n", encoding="utf-8")

    def test_manifest_is_byte_deterministic(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            self._fixture(root, "Count %1$d")

            first = render_manifest(build_manifest(root))
            second = render_manifest(build_manifest(root))

            self.assertEqual(first, second)
            json.loads(first)

    def test_selection_unions_prefix_direct_reference_and_deferred_numeric(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            self._fixture(root, "Count %1$d")
            manifest = build_manifest(root)
            records = {item["name"]: item for item in manifest["resources"]}

            self.assertEqual({"rec_live", "rec_items", "numeric_orphan"}, set(records))
            self.assertEqual("production-reference", records["rec_live"]["static_reference_status"])
            self.assertEqual("test-only-candidate", records["numeric_orphan"]["static_reference_status"])
            self.assertFalse(records["numeric_orphan"]["dead_code_proven"])

    def test_placeholder_mismatch_is_reported(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            self._fixture(root, "Count %1$s")

            manifest = build_manifest(root)

            self.assertEqual(1, manifest["summary"]["contract_error_count"])
            self.assertEqual("locale-string-placeholder-mismatch", manifest["contract_errors"][0]["kind"])

    def test_plural_categories_and_missing_overrides_are_explicit(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            self._fixture(root, "Count %1$d")
            manifest = build_manifest(root)
            plural = next(item for item in manifest["resources"] if item["name"] == "rec_items")
            locale = manifest["locales"][0]

            self.assertEqual(["one", "other"], plural["base_plural_categories"])
            self.assertEqual(["rec_items"], locale["missing_plurals"])
            self.assertEqual([], locale["plural_contracts"])

    def test_current_repository_reconciles_a15a2c_and_locale_inventory(self):
        manifest = build_manifest(SOURCE_ROOT)

        self.assertEqual(33, manifest["summary"]["locale_count"])
        self.assertEqual(DEFERRED_NUMERIC, set(manifest["deferred_numeric_candidates"]))
        self.assertEqual(14, manifest["summary"]["deferred_numeric_candidate_count"])
        # The selected staged baseline has 708 feature-prefix strings. The current
        # working source also includes the accepted v0.8.21 For You focus and
        # source-metadata diagnostics strings, bringing the live count to 725.
        self.assertEqual(725, manifest["summary"]["feature_prefix_string_count"])
        self.assertEqual(110, manifest["summary"]["feature_prefix_plural_count"])
        self.assertTrue(
            all(
                not locale["feature_prefix_overridden_strings"]
                and not locale["feature_prefix_overridden_plurals"]
                for locale in manifest["locales"]
            ),
        )
        mismatches = {
            (error["locale"], error["resource"])
            for error in manifest["contract_errors"]
            if error["kind"] == "locale-string-placeholder-mismatch"
        }
        self.assertEqual(KNOWN_TRANSLATION_CONTRACT_MISMATCHES, mismatches)
        self.assertEqual(len(KNOWN_TRANSLATION_CONTRACT_MISMATCHES), manifest["summary"]["contract_error_count"])


if __name__ == "__main__":
    unittest.main()
