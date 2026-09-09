package exh.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class KmkComposedPluralResourceContractTest {
    @Test
    fun `multi quantity templates use independently inflected plural fragments`() {
        val resources = resources()
        val productionSource = File("src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        compositionSpecs.forEach { spec ->
            val template = resources.strings[spec.templateName]
            assertNotNull(template, "${spec.templateName} must remain a string composition template")
            assertEquals(
                (1..spec.arguments.size).map { "%${it}\$s" },
                placeholders(template!!),
                "${spec.templateName} must receive only localized string fragments",
            )
            assertTrue(
                productionSource.contains("KMR.strings.${spec.templateName}"),
                "${spec.templateName} has no production caller",
            )

            spec.fragmentNames.forEach { fragmentName ->
                val quantities = resources.plurals[fragmentName]
                assertNotNull(quantities, "$fragmentName must be a plurals resource")
                assertEquals(setOf("one", "other"), quantities!!.keys)
                quantities.values.forEach { value ->
                    assertEquals(listOf("%1\$d"), placeholders(value))
                }
                assertTrue(
                    productionSource.contains("KMR.plurals.$fragmentName"),
                    "$fragmentName has no production caller",
                )
            }
        }
    }

    @Test
    fun `every singular and plural dimension composes without raw format arguments`() {
        val resources = resources()

        compositionSpecs.forEach { spec ->
            val combinations = 1 shl spec.fragmentNames.size
            repeat(combinations) { mask ->
                val fragments = spec.fragmentNames.mapIndexed { index, name ->
                    val count = if (mask and (1 shl index) == 0) 1 else 2
                    val quantity = if (count == 1) "one" else "other"
                    String.format(Locale.ROOT, resources.plurals.getValue(name).getValue(quantity), count)
                }
                val arguments = spec.arguments.map { argument ->
                    when (argument) {
                        is Argument.Fragment -> fragments[argument.index]
                        is Argument.Literal -> argument.value
                    }
                }.toTypedArray()
                val result = String.format(
                    Locale.ROOT,
                    resources.strings.getValue(spec.templateName),
                    *arguments,
                )

                assertFalse(
                    formatArgument.containsMatchIn(result),
                    "${spec.templateName} left a raw format argument for mask $mask",
                )
                fragments.forEach { fragment ->
                    assertTrue(
                        result.contains(fragment),
                        "${spec.templateName} omitted '$fragment' for mask $mask",
                    )
                }
            }
        }
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
            plurals = (0 until plurals.length)
                .map { plurals.item(it) as Element }
                .associate { plural ->
                    val items = plural.getElementsByTagName("item")
                    plural.getAttribute("name") to (0 until items.length)
                        .map { items.item(it) as Element }
                        .associate { it.getAttribute("quantity") to it.textContent }
                },
        )
    }

    private fun placeholders(value: String): List<String> =
        formatArgument.findAll(value).map { it.value }.toList()

    private data class Resources(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
    )

    private sealed interface Argument {
        data class Fragment(val index: Int) : Argument
        data class Literal(val value: String) : Argument
    }

    private data class CompositionSpec(
        val templateName: String,
        val fragmentNames: List<String>,
        val arguments: List<Argument> = fragmentNames.indices.map(Argument::Fragment),
    )

    private companion object {
        val formatArgument = Regex("%\\d+\\\$[a-zA-Z]")

        val compositionSpecs = listOf(
            CompositionSpec(
                "eval_undo_restored_partial_group",
                listOf("eval_undo_restored_manga_fragment", "eval_undo_unresolved_item_fragment"),
            ),
            CompositionSpec(
                "extension_export_multi_partial",
                listOf("extension_export_exported_extension_fragment", "extension_export_missing_file_fragment"),
            ),
            CompositionSpec(
                "rec_bulk_action_clear_rating_partial",
                listOf("rec_bulk_action_cleared_rating_fragment", "rec_bulk_action_failed_item_fragment"),
            ),
            CompositionSpec(
                "rec_bulk_action_not_interested_partial",
                listOf("rec_bulk_action_not_interested_manga_fragment", "rec_bulk_action_failed_item_fragment"),
            ),
            CompositionSpec(
                "rec_bulk_action_rated_partial",
                listOf("rec_bulk_action_rated_manga_fragment", "rec_bulk_action_failed_item_fragment"),
                listOf(Argument.Fragment(0), Argument.Literal("Love"), Argument.Fragment(1)),
            ),
            CompositionSpec(
                "rec_bundle_import_bundle_info",
                listOf("rec_bundle_item_fragment", "rec_bundle_source_fragment"),
                listOf(Argument.Literal("2026-08-14"), Argument.Fragment(0), Argument.Fragment(1)),
            ),
            CompositionSpec(
                "rec_settings_summary_tags",
                listOf("rec_settings_preferred_tag_fragment", "rec_settings_blocked_tag_fragment"),
            ),
            CompositionSpec(
                "rec_source_metadata_tag_diagnostics_row_summary",
                listOf(
                    "rec_source_diagnostics_sample_fragment",
                    "rec_source_diagnostics_metadata_fragment",
                    "rec_source_diagnostics_positive_match_fragment",
                    "rec_source_diagnostics_blocked_candidate_fragment",
                ),
            ),
            CompositionSpec(
                "rec_source_metadata_tag_diagnostics_tags",
                listOf(
                    "rec_source_diagnostics_preferred_match_fragment",
                    "rec_source_diagnostics_blocked_match_fragment",
                    "rec_source_diagnostics_positive_candidate_fragment",
                    "rec_source_diagnostics_negative_candidate_fragment",
                ),
            ),
            CompositionSpec(
                "source_evaluation_positive_negative_summary",
                listOf(
                    "source_evaluation_liked_source_fragment",
                    "source_evaluation_disliked_source_fragment",
                    "source_evaluation_blocked_source_fragment",
                    "source_evaluation_adult_risk_source_fragment",
                ),
            ),
            CompositionSpec(
                "taste_diagnostics_confidence",
                listOf(
                    "taste_diagnostics_positive_signal_fragment",
                    "taste_diagnostics_negative_signal_fragment",
                ),
            ),
        )
    }
}
