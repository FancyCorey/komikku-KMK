package exh.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class KmkNumericPluralReconciliationTest {
    @Test
    fun `all live numeric strings are intentional fixed formatting contracts`() {
        val resources = resources()
        val productionSource = productionSource()
        val liveNumericStrings = resources.strings
            .filterValues { numericArgument.containsMatchIn(it) }
            .keys
            .filterTo(sortedSetOf()) { productionSource.hasStringReference(it) }

        assertEquals(fixedNumericStringNames, liveNumericStrings)
        fixedNumericStringNames.forEach { name ->
            assertTrue(name in resources.strings, "$name must remain a string resource")
            assertTrue(name !in resources.pluralNames, "$name must not become a plural resource")
        }
    }

    @Test
    fun `all unowned numeric strings remain explicitly deferred`() {
        val resources = resources()
        val productionSource = productionSource()
        val unownedNumericStrings = resources.strings
            .filterValues { numericArgument.containsMatchIn(it) }
            .keys
            .filterTo(sortedSetOf()) { !productionSource.hasStringReference(it) }

        assertEquals(deferredNumericStringNames, unownedNumericStrings)
        deferredNumericStringNames.forEach { name ->
            assertTrue(name in resources.strings, "$name must remain available until A15A.5")
            assertTrue(name !in resources.pluralNames, "$name must not be converted without a caller")
        }
    }

    @Test
    fun `no live string resource uses parenthesized plural shorthand`() {
        val resources = resources()
        val productionSource = productionSource()
        val liveParenthesizedPluralStrings = resources.strings
            .filterValues { "(s)" in it }
            .keys
            .filterTo(sortedSetOf()) { productionSource.hasStringReference(it) }

        assertTrue(
            liveParenthesizedPluralStrings.isEmpty(),
            "Live resources still use (s): $liveParenthesizedPluralStrings",
        )
    }

    @Test
    fun `string and plural namespaces do not overlap`() {
        val resources = resources()
        assertTrue(
            resources.strings.keys.intersect(resources.pluralNames).isEmpty(),
            "A resource name cannot be owned by both strings.xml and plurals.xml",
        )
    }

    private fun resources(): Resources {
        val factory = DocumentBuilderFactory.newInstance()
            .apply { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        val root = File("../i18n-kmk/src/commonMain/moko-resources/base")
        val stringsDocument = factory.newDocumentBuilder().parse(File(root, "strings.xml"))
        val pluralsDocument = factory.newDocumentBuilder().parse(File(root, "plurals.xml"))
        val strings = stringsDocument.getElementsByTagName("string")
        val plurals = pluralsDocument.getElementsByTagName("plurals")

        return Resources(
            strings = (0 until strings.length)
                .map { strings.item(it) as Element }
                .associate { it.getAttribute("name") to it.textContent },
            pluralNames = (0 until plurals.length)
                .map { (plurals.item(it) as Element).getAttribute("name") }
                .toSet(),
        )
    }

    private fun productionSource(): String = File("src/main/java")
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .joinToString("\n") { it.readText() }

    private fun String.hasStringReference(name: String): Boolean =
        Regex("KMR\\.strings\\.${Regex.escape(name)}\\b").containsMatchIn(this)

    private data class Resources(
        val strings: Map<String, String>,
        val pluralNames: Set<String>,
    )

    private companion object {
        val numericArgument = Regex("%\\d+\\\$d")

        val fixedNumericStringNames = sortedSetOf(
            "alternate_source_reader_generic_chapter",
            "alternate_source_reader_generic_source",
            "best_version_preview_page_content_description",
            "best_version_preview_page_indicator",
            "eval_undo_diagnostic_counts",
            "eval_undo_time_hours_ago",
            "eval_undo_time_minutes_ago",
            "evaluation_mode_blocked_tag_label",
            "evaluation_mode_liked_tag_label",
            "evaluation_mode_repo_label",
            "extension_uninstall_selected",
            "local_tracking_export_partial",
            "migration_list_result_partial_message",
            "migration_list_result_partial_message_with_skipped",
            "ocr_notification_complete_v2",
            "ocr_result_page",
            "rated_manga_selected_count",
            "reading_timer_preset_minutes",
            "rec_bulk_action_undo_partial_failure",
            "rec_bundle_import_add_selected",
            "rec_bundle_import_complete_body",
            "rec_bundle_load_error_unsupported_version",
            "rec_for_you_selected_count",
            "rec_for_you_source_evaluated_candidates",
            "rec_latest_exploration_percent",
            "rec_match_selection_count",
            "rec_numeric_value_range",
            "rec_settings_summary_source_priority",
            "rec_source_fit_with_top_picks_count",
            "rec_suggestion_install_selected",
            "rec_suggestion_install_visible",
            "source_evaluation_catalogue_row_subtitle",
            "source_evaluation_catalogue_sample_sources",
            "source_evaluation_hidden_installed",
            "source_evaluation_rec_quality_diagnostics",
            "source_evaluation_rec_quality_running",
            "source_evaluation_safety_blocked_count",
            "source_evaluation_safety_quarantined_count",
            "taste_diagnostics_rating_counts",
            "taste_settings_tag_group_show_all",
        )

        val deferredNumericStringNames = sortedSetOf(
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
        )
    }
}
