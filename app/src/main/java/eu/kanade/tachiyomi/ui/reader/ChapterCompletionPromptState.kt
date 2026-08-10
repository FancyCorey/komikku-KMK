package eu.kanade.tachiyomi.ui.reader

// KMK v0.8.10 -->
/**
 * Pure state for the chapter-completion rating prompt's exit-deferred lifecycle.
 *
 * A genuine completion of the latest available chapter ([LatestChapterCompletionPolicy]) is
 * recorded but must NOT interrupt the reader immediately. The prompt is only shown when the user
 * actually leaves the reader afterward (back press / finish()). This reducer models exactly that
 * two-step lifecycle so the transition logic is directly unit-testable without any Android
 * dependencies (Activity, ViewModel, Compose).
 */
sealed interface ChapterCompletionPromptState {
    /** No eligible completion recorded since the last prompt resolution. */
    data object None : ChapterCompletionPromptState

    /** A genuine completion was recorded for [mangaId]; the prompt will show when the reader is left. */
    data class PendingOnExit(val mangaId: Long) : ChapterCompletionPromptState
}

object ChapterCompletionPromptReducer {

    /**
     * Records a genuine completion. Idempotent for the same manga id in the already-pending
     * state (does not restart or duplicate anything); a completion for a *different* manga id
     * (e.g. the user backed out and started reading something else without leaving the reader
     * activity, if that were ever possible) replaces the pending target rather than stacking.
     */
    fun onGenuineCompletion(current: ChapterCompletionPromptState, mangaId: Long): ChapterCompletionPromptState {
        return ChapterCompletionPromptState.PendingOnExit(mangaId)
    }

    /**
     * Called when the reader is actually being left. Returns the manga id to prompt for (null if
     * nothing is pending — the caller should proceed with a normal exit) and the state after this
     * call (always [ChapterCompletionPromptState.None] — consuming is destructive, so a second
     * call in the same lifecycle never re-shows the prompt).
     */
    fun takeOnExit(current: ChapterCompletionPromptState): Pair<Long?, ChapterCompletionPromptState> {
        val mangaId = (current as? ChapterCompletionPromptState.PendingOnExit)?.mangaId
        return mangaId to ChapterCompletionPromptState.None
    }
}
// KMK <--
