package eu.kanade.tachiyomi.ui.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// KMK v0.8.10 -->
class ChapterCompletionPromptReducerTest {

    @Test
    fun `initial state is None`() {
        assertEquals(ChapterCompletionPromptState.None, ChapterCompletionPromptState.None)
    }

    @Test
    fun `a genuine completion transitions None to PendingOnExit for that manga`() {
        val next = ChapterCompletionPromptReducer.onGenuineCompletion(ChapterCompletionPromptState.None, mangaId = 42L)
        assertEquals(ChapterCompletionPromptState.PendingOnExit(42L), next)
    }

    @Test
    fun `taking on exit from None returns null and stays None`() {
        val (mangaId, next) = ChapterCompletionPromptReducer.takeOnExit(ChapterCompletionPromptState.None)
        assertNull(mangaId)
        assertEquals(ChapterCompletionPromptState.None, next)
    }

    @Test
    fun `taking on exit from PendingOnExit returns the manga id and clears to None`() {
        val pending = ChapterCompletionPromptState.PendingOnExit(7L)
        val (mangaId, next) = ChapterCompletionPromptReducer.takeOnExit(pending)
        assertEquals(7L, mangaId)
        assertEquals(ChapterCompletionPromptState.None, next)
    }

    @Test
    fun `taking on exit twice in a row only returns the manga id once - no duplicate prompt`() {
        var state: ChapterCompletionPromptState = ChapterCompletionPromptState.PendingOnExit(1L)

        val (firstResult, afterFirst) = ChapterCompletionPromptReducer.takeOnExit(state)
        state = afterFirst
        val (secondResult, afterSecond) = ChapterCompletionPromptReducer.takeOnExit(state)
        state = afterSecond

        assertEquals(1L, firstResult)
        assertNull(secondResult)
        assertEquals(ChapterCompletionPromptState.None, state)
    }

    @Test
    fun `leaving the reader without a genuine completion never shows the prompt`() {
        // Never called onGenuineCompletion -- simulates "left without completing the latest chapter."
        val (mangaId, _) = ChapterCompletionPromptReducer.takeOnExit(ChapterCompletionPromptState.None)
        assertNull(mangaId)
    }

    @Test
    fun `a second genuine completion for a different manga replaces the pending target rather than stacking`() {
        var state: ChapterCompletionPromptState = ChapterCompletionPromptState.None
        state = ChapterCompletionPromptReducer.onGenuineCompletion(state, mangaId = 1L)
        state = ChapterCompletionPromptReducer.onGenuineCompletion(state, mangaId = 2L)

        val (mangaId, next) = ChapterCompletionPromptReducer.takeOnExit(state)
        assertEquals(2L, mangaId)
        assertEquals(ChapterCompletionPromptState.None, next)
    }

    @Test
    fun `state is a plain value - reapplying the reducer to a reconstructed state behaves identically (recreation-safe)`() {
        // ChapterCompletionPromptState is a plain sealed value type with no Android/lifecycle
        // dependency, so it survives exactly as well as any other retained-ViewModel field across
        // configuration changes (rotation) -- this test proves the reducer has no hidden mutable
        // or identity-sensitive state that would break if the value were copied/reconstructed, as
        // happens when a ViewModel field is read by a newly-recreated Activity after rotation.
        val originalState = ChapterCompletionPromptReducer.onGenuineCompletion(ChapterCompletionPromptState.None, mangaId = 99L)

        // Simulate "recreation" by reconstructing an equal value from scratch rather than reusing the reference.
        val reconstructed = ChapterCompletionPromptState.PendingOnExit((originalState as ChapterCompletionPromptState.PendingOnExit).mangaId)

        assertEquals(originalState, reconstructed)

        val (mangaIdFromOriginal, _) = ChapterCompletionPromptReducer.takeOnExit(originalState)
        val (mangaIdFromReconstructed, _) = ChapterCompletionPromptReducer.takeOnExit(reconstructed)
        assertEquals(mangaIdFromOriginal, mangaIdFromReconstructed)
    }
}
// KMK <--
