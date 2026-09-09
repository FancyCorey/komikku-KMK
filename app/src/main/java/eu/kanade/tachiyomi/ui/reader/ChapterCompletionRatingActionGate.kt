package eu.kanade.tachiyomi.ui.reader

// KMK AUG-06 duplicate-action fencing -->
/**
 * Pure predicates fencing [ReaderViewModel.rateFromChapterCompletionPrompt] and
 * [ReaderViewModel.markNotInterestedFromChapterCompletionPrompt] against duplicate taps and
 * stale-dismiss races, directly unit-testable without any Android/DI dependency (mirrors
 * [ChapterCompletionPromptReducer]'s pattern: pure state-transition logic, only the actual
 * `mutableState.update` call lives on the ViewModel).
 *
 * Both prompt actions are asynchronous; without this gate a second tap on the same or a
 * different action button before the first write resolves would fire a second concurrent
 * mutation and could produce two Action History entries for what the user experienced as one
 * choice. A delayed write could also resurrect a dialog the user already dismissed.
 */
object ChapterCompletionRatingActionGate {

    /**
     * True only when [dialog] is the exact pending, not-yet-processing prompt for [mangaId] --
     * the one state from which an action may legally begin. A duplicate tap (dialog already
     * `isProcessing`), a tap for a different manga id, or any other dialog value is fenced.
     */
    fun canBegin(dialog: ReaderViewModel.Dialog?, mangaId: Long): Boolean =
        dialog is ReaderViewModel.Dialog.ChapterCompletionRating && dialog.mangaId == mangaId && !dialog.isProcessing

    /**
     * True only when [dialog] is still the exact in-flight prompt this action started against --
     * i.e. the user has not dismissed it and no other action has already resolved it. A write
     * whose result arrives after the dialog moved on must apply its already-durable persistence
     * effect but must never resurrect or overwrite an unrelated later dialog state.
     */
    fun isStillPending(dialog: ReaderViewModel.Dialog?, mangaId: Long): Boolean =
        dialog is ReaderViewModel.Dialog.ChapterCompletionRating && dialog.mangaId == mangaId && dialog.isProcessing
}
// KMK <--
