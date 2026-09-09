package exh.util

import android.content.Context
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class ActionHistoryRoutePrivacySweepTest {

    private val context = mockk<Context>(relaxed = true)

    private val canaries = listOf(
        "Private Source",
        "Secret Manga",
        "https://private.example/title",
        "C:\\Users\\Private\\artifact",
        "content://private.extension/package.apk",
        "IllegalStateException: secret",
        "diagnostic-private-value",
    )

    @Test
    fun `Evaluation Mode uses only declared safe summaries across representative row families`() {
        val rows = listOf(
            row("taste", canaries[0], "Rating changed"),
            row("preference", canaries[1], "Recommendation setting changed"),
            row("nonundoable", canaries[2], "Extension installed"),
            row("library", canaries.drop(3).joinToString(" "), "Library state changed", contextMangaId = 42L),
        )

        val normal = rows.map { present(it, evaluationModeEnabled = false) }
        val evaluation = rows.map { present(it, evaluationModeEnabled = true) }

        canaries.take(3).forEach { canary ->
            assertTrue(normal.any { canary in it }, "sanity: ordinary presentation should use its supplied summary")
        }
        canaries.forEach { canary ->
            assertFalse(evaluation.any { canary in it }, "Evaluation Mode leaked canary: $canary")
        }
        assertEquals(
            listOf("Rating changed", "Recommendation setting changed", "Extension installed", "Library state changed"),
            evaluation,
        )
    }

    @Test
    fun `missing Evaluation Mode summary falls back without evaluating the ordinary summary`() {
        val normalReads = AtomicInteger()
        val summary = ActionHistoryRowPresentationPolicy.summary(
            evaluationModeEnabled = true,
            standardSummary = {
                normalReads.incrementAndGet()
                "Secret Manga"
            },
            evaluationModeSummary = null,
            redactedFallback = { "Recorded action" },
        )

        assertEquals("Recorded action", summary)
        assertEquals(0, normalReads.get())
    }

    @Test
    fun `diagnostics are hidden throughout Evaluation Mode regardless of developer setting`() {
        assertFalse(
            ActionHistoryRowPresentationPolicy.canExposeDiagnostics(
                developerOptionsEnabled = true,
                evaluationModeEnabled = true,
            ),
        )
        assertFalse(
            ActionHistoryRowPresentationPolicy.canExposeDiagnostics(
                developerOptionsEnabled = false,
                evaluationModeEnabled = false,
            ),
        )
        assertTrue(
            ActionHistoryRowPresentationPolicy.canExposeDiagnostics(
                developerOptionsEnabled = true,
                evaluationModeEnabled = false,
            ),
        )
    }

    private fun row(
        id: String,
        normalSummary: String,
        safeSummary: String,
        contextMangaId: Long? = null,
    ) = ActionHistoryEntryDescriptor(
        id = id,
        timestamp = 1L,
        summary = { normalSummary },
        evaluationModeSummary = { safeSummary },
        undo = null,
        contextMangaId = contextMangaId,
        diagnosticKey = "diagnostic-private-value",
    )

    private fun present(
        row: ActionHistoryEntryDescriptor,
        evaluationModeEnabled: Boolean,
    ): String = ActionHistoryRowPresentationPolicy.summary(
        evaluationModeEnabled = evaluationModeEnabled,
        standardSummary = { row.summary(context) },
        evaluationModeSummary = row.evaluationModeSummary?.let { safe ->
            { safe(context) }
        },
        redactedFallback = { "Recorded action" },
    )
}
