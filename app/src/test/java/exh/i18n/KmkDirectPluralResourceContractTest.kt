package exh.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class KmkDirectPluralResourceContractTest {
    @Test
    fun `direct grammatical quantities use complete plural families`() {
        val document = DocumentBuilderFactory.newInstance()
            .apply { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            .newDocumentBuilder()
            .parse(File("../i18n-kmk/src/commonMain/moko-resources/base/plurals.xml"))
        val plurals = document.getElementsByTagName("plurals")
        val pluralByName = (0 until plurals.length)
            .map { plurals.item(it) as Element }
            .associateBy { it.getAttribute("name") }

        migratedPluralNames.forEach { name ->
            val plural = pluralByName[name]
            assertNotNull(plural, "$name must be a plurals resource")
            val items = plural!!.getElementsByTagName("item")
            val byQuantity = (0 until items.length)
                .map { items.item(it) as Element }
                .associateBy { it.getAttribute("quantity") }
            val one = byQuantity["one"]
            val other = byQuantity["other"]
            assertNotNull(one, "$name must define quantity=one")
            assertNotNull(other, "$name must define quantity=other")
            assertEquals(
                placeholders(one!!.textContent),
                placeholders(other!!.textContent),
                "$name must preserve the same format arguments in one and other",
            )
        }
    }

    @Test
    fun `migrated plural families have no string references or manual pair`() {
        val productionSource = File("src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        val resourceRoot = File("../i18n-kmk/src/commonMain/moko-resources/base")
        val strings = File(resourceRoot, "strings.xml").readText()
        val plurals = File(resourceRoot, "plurals.xml").readText()

        migratedPluralNames.forEach { name ->
            assertFalse(
                Regex("KMR\\.strings\\.${Regex.escape(name)}\\b").containsMatchIn(productionSource),
                "$name still has a string reference",
            )
            assertTrue(
                Regex("KMR\\.plurals\\.${Regex.escape(name)}\\b").containsMatchIn(productionSource),
                "$name has no plural reference",
            )
        }
        assertFalse(strings.contains("<plurals"), "plural families must live in plurals.xml")
        assertFalse(strings.contains("name=\"rec_bulk_action_clear_rating_success_one\""))
        assertFalse(strings.contains("name=\"rec_bulk_action_clear_rating_success_other\""))
        assertTrue(plurals.contains("name=\"rec_bulk_action_clear_rating_success\""))
    }

    private fun placeholders(value: String): List<String> =
        formatArgument.findAll(value).map { it.value }.toList()

    private companion object {
        val formatArgument = Regex("%\\d+\\$[a-zA-Z]")

        val migratedPluralNames = setOf(
            "best_version_pages_loaded",
            "eval_undo_conflict_group",
            "eval_undo_restored_group",
            "eval_undo_restored_partial_single",
            "eval_undo_summary_cleared",
            "eval_undo_summary_group_merge",
            "eval_undo_summary_group_remove",
            "eval_undo_summary_group_ungroup",
            "eval_undo_summary_library_categories",
            "eval_undo_summary_library_favorite",
            "eval_undo_summary_library_unfavorite",
            "eval_undo_summary_not_interested",
            "eval_undo_summary_rated",
            "extension_export_multi_success",
            "extension_uninstall_selected_message",
            "link_group_management_source_count",
            "loved_manga_versions",
            "ocr_index_status_old_rows",
            "ocr_index_status_v2",
            "ocr_indexing_progress_v2",
            "ocr_match_partial",
            "rated_manga_confirm_clear_rating",
            "rated_manga_confirm_merge",
            "rated_manga_confirm_not_interested",
            "rated_manga_confirm_remove_from_group",
            "rated_manga_merge_success",
            "rated_manga_remove_from_group_success",
            "rec_bulk_action_clear_rating_all_failed",
            "rec_bulk_action_clear_rating_success",
            "rec_bulk_action_not_interested_all_failed",
            "rec_bulk_action_not_interested_success",
            "rec_bulk_action_rated_all_failed",
            "rec_bulk_action_rated_success",
            "rec_bundle_load_error_too_many_items",
            "rec_bundle_load_error_too_many_sources",
            "rec_exposure_window_days",
            "rec_match_apply_dislike",
            "rec_match_apply_favorite",
            "rec_match_apply_like",
            "rec_match_apply_love",
            "rec_match_apply_seen",
            "rec_settings_summary_languages",
            "rec_settings_summary_ratings_known_manga",
            "rec_settings_summary_sources_to_try",
            "rec_source_metadata_tag_diagnostics_enrichment",
            "rec_source_metadata_tag_diagnostics_metadata",
            "rec_source_metadata_tag_diagnostics_summary",
            "rec_source_status_shown",
            "rec_suggestions_show_more",
            "reading_schedule_window_count",
            "reading_timer_warning_minutes_option",
            "reading_timer_warning_toast",
            "source_evaluation_blocked_packages_count",
            "source_evaluation_candidates_available",
            "source_evaluation_candidates_evaluated_hidden",
            "source_evaluation_candidates_explicit_hidden",
            "source_evaluation_candidates_unassessed_remaining",
            "source_evaluation_candidates_unsafe_hidden",
            "source_evaluation_cleanup_failed_warning",
            "source_evaluation_cleanup_prompt_required_warning",
            "source_evaluation_clear_disliked_suggestion_sources",
            "source_evaluation_clear_dismissed_suggestions",
            "source_evaluation_complete_cleanup",
            "source_evaluation_continue_next_batch",
            "source_evaluation_detail_enriched_count",
            "source_evaluation_detail_enrichment_failed_count",
            "source_evaluation_ecchi_count",
            "source_evaluation_error_count",
            "source_evaluation_explicit_count",
            "source_evaluation_last_evaluated_days",
            "source_evaluation_metadata_sample_count",
            "source_evaluation_reassessment_recommended",
            "source_evaluation_rec_quality_missing",
            "source_evaluation_rec_quality_outdated_count",
            "source_evaluation_rec_quality_private_required_message",
            "source_evaluation_reconciliation_failed_warning",
            "source_evaluation_runtime_health_count",
            "source_evaluation_stale_reassess_complete_partial",
            "source_evaluation_stale_reassess_continue",
            "source_evaluation_stale_reassess_start",
            "source_evaluation_start",
            "source_evaluation_strong_fit_count",
            "source_evaluation_unsafe_sources_count",
            "source_evaluation_updated_extensions_notice",
            "source_evaluation_worth_trying_count",
            "source_quality_hidden_mark_count",
            "source_recommendation_quality_job_checked_count",
            "taste_diagnostics_blocked_tags_header",
            "taste_diagnostics_preferred_tags_header",
            "taste_diagnostics_tag_evidence_row",
            "taste_settings_tag_group_show_n_more",
            "taste_suggestions_evidence_count",
        )
    }
}
