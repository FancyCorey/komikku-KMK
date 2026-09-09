package eu.kanade.tachiyomi.ui.reader.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlternateSourceReaderPresentationPolicyTest {

    @Test
    fun `alternate exposes contextual actions while resolving exposes none`() {
        val alternate = AlternateSourceReaderPresentationPolicy.contextActions(
            AlternateSourceReaderMachineState(
                phase = AlternateSourceReaderPhase.ALTERNATE,
                session = readerSession(),
            ),
        )
        assertTrue(alternate.returnToPrimary)
        assertTrue(alternate.correctMapping)
        assertTrue(alternate.skipChapter)
        assertFalse(alternate.resolving)

        listOf(
            AlternateSourceReaderPhase.RESOLVING_ENTRY,
            AlternateSourceReaderPhase.RESOLVING_CORRECTION,
            AlternateSourceReaderPhase.RETURNING,
        ).forEach { phase ->
            val actions = AlternateSourceReaderPresentationPolicy.contextActions(
                AlternateSourceReaderMachineState(phase = phase, session = readerSession()),
            )
            assertTrue(actions.resolving)
            assertFalse(actions.returnToPrimary)
            assertFalse(actions.correctMapping)
            assertFalse(actions.skipChapter)
        }
    }

    @Test
    fun `degraded session retains only manual return and closed reason`() {
        val actions = AlternateSourceReaderPresentationPolicy.contextActions(
            AlternateSourceReaderMachineState(
                phase = AlternateSourceReaderPhase.DEGRADED,
                session = readerSession(),
                failureReason = AlternateSourceReaderFailureReason.RETRY_EXHAUSTED,
            ),
        )
        assertTrue(actions.returnToPrimary)
        assertFalse(actions.correctMapping)
        assertFalse(actions.skipChapter)
        assertEquals(AlternateSourceReaderRecoveryReason.RETRIES_EXHAUSTED, actions.degradedReason)
    }

    @Test
    fun `terminal and primary phases expose no contextual actions`() {
        listOf(
            AlternateSourceReaderPhase.PRIMARY,
            AlternateSourceReaderPhase.ENDED,
            AlternateSourceReaderPhase.DEGRADED,
        ).forEach { phase ->
            assertEquals(
                AlternateSourceReaderContextActions(),
                AlternateSourceReaderPresentationPolicy.contextActions(
                    AlternateSourceReaderMachineState(phase = phase),
                ),
            )
        }
    }

    @Test
    fun `all command results map to closed presentation outcomes`() {
        val results = listOf(
            AlternateSourceReaderCommandResult.Entered(READER_TARGET_ID),
            AlternateSourceReaderCommandResult.RequiresProvisionalConfirmation(READER_TARGET_ID),
            AlternateSourceReaderCommandResult.RequiresPairConfirmation,
            AlternateSourceReaderCommandResult.PairConfirmed,
            AlternateSourceReaderCommandResult.Corrected,
            AlternateSourceReaderCommandResult.Skipped,
            AlternateSourceReaderCommandResult.Continued,
            AlternateSourceReaderCommandResult.Returned,
            AlternateSourceReaderCommandResult.Restored,
            AlternateSourceReaderCommandResult.NoSession,
            AlternateSourceReaderCommandResult.Unavailable,
            AlternateSourceReaderCommandResult.Conflict,
            AlternateSourceReaderCommandResult.Stale,
            AlternateSourceReaderCommandResult.NotAtBoundary,
            AlternateSourceReaderCommandResult.LoopRejected,
            AlternateSourceReaderCommandResult.Invalid,
            AlternateSourceReaderCommandResult.Failed,
        )
        assertEquals(17, results.distinct().size)
        results.forEach {
            AlternateSourceReaderPresentationPolicy.result(it, explicitUserCommand = true)
        }
    }

    @Test
    fun `automatic no-session and boundary outcomes are silent`() {
        assertEquals(
            AlternateSourceReaderResultPresentation.Silent,
            AlternateSourceReaderPresentationPolicy.result(
                AlternateSourceReaderCommandResult.NoSession,
                explicitUserCommand = false,
            ),
        )
        assertEquals(
            AlternateSourceReaderResultPresentation.Silent,
            AlternateSourceReaderPresentationPolicy.result(
                AlternateSourceReaderCommandResult.NotAtBoundary,
                explicitUserCommand = false,
            ),
        )
    }

    @Test
    fun `stale and conflict invalidate private selection tokens`() {
        listOf(
            AlternateSourceReaderCommandResult.Stale,
            AlternateSourceReaderCommandResult.Conflict,
            AlternateSourceReaderCommandResult.Invalid,
        ).forEach { result ->
            val presentation = AlternateSourceReaderPresentationPolicy.result(result, true)
                as AlternateSourceReaderResultPresentation.Recoverable
            assertTrue(presentation.invalidateTokens)
        }
    }

    @Test
    fun `generic evaluation labels carry only kind and positive ordinal`() {
        assertEquals(
            AlternateSourceReaderGenericLabel(AlternateSourceReaderGenericLabelKind.SOURCE, 2),
            AlternateSourceReaderGenericLabel(AlternateSourceReaderGenericLabelKind.SOURCE, 2),
        )
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            AlternateSourceReaderGenericLabel(AlternateSourceReaderGenericLabelKind.SOURCE, 0)
        }
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            AlternateSourceReaderGenericLabel(AlternateSourceReaderGenericLabelKind.CHAPTER, -1)
        }
    }

    @Test
    fun `evaluation source rows preserve manga identity while obfuscating only the source`() {
        val row = AlternateSourceReaderPresentationPolicy.mangaCandidateRow(
            token = AlternateSourceReaderOpaqueToken("candidate"),
            title = "The Actual Manga",
            sourceLabel = "Private Source",
            evaluationSourceLabel = "Source A",
            evaluationModeEnabled = true,
        )

        assertEquals("The Actual Manga", row.primaryLabel)
        assertEquals("Source A", row.secondaryLabel)
        assertEquals(null, row.genericLabel)
    }

    @Test
    fun `chapter rows preserve chapter identity in every presentation mode`() {
        val row = AlternateSourceReaderPresentationPolicy.chapterCandidateRow(
            token = AlternateSourceReaderOpaqueToken("chapter"),
            name = "Chapter 12: The Return",
            chapterNumber = 12f,
        )

        assertEquals("Chapter 12: The Return", row.primaryLabel)
        assertEquals("12.0", row.secondaryLabel)
        assertEquals(null, row.genericLabel)
    }

    @Test
    fun `public presentation rows contain no exact identity fields`() {
        val fieldNames = AlternateSourceReaderCandidateRow::class.java.declaredFields.map { it.name }.toSet()
        listOf("source", "sourceId", "mangaId", "chapterId", "url", "key", "error", "throwable").forEach {
            assertFalse(it in fieldNames)
        }
    }
}
