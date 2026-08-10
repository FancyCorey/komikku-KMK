package exh.recs.loved

// KMK --> v0.8.0
/**
 * Pure reducer for Rated Manga bulk-selection state. No Android/DB dependencies —
 * fully unit-testable. Extracted so the long-press/tap/select-all selection behavior required by
 * the v0.8.0 plan is directly testable without instantiating [LovedMangaScreenModel].
 */
object RatedSelectionReducer {

    data class Selection(val selectionMode: Boolean, val selectedKeys: Set<RatedMangaKey>)

    private val EMPTY = Selection(selectionMode = false, selectedKeys = emptySet())

    /** Long-press: enters selection mode (if not already) and selects [key]. */
    fun enter(current: Selection, key: RatedMangaKey): Selection =
        current.copy(selectionMode = true, selectedKeys = current.selectedKeys + key)

    // KMK --> v0.8.1-fix1: the app-bar "Select" action must enter selection mode without silently
    // selecting anything — only long-press (enter(key)) or a subsequent tap-toggle should populate
    // the selection. Existing selected keys are preserved (matches enter()'s additive behavior).
    /** App-bar "Select" action: enters selection mode without selecting any item. */
    fun enterEmpty(current: Selection): Selection = current.copy(selectionMode = true)
    // KMK <--

    /** Tap while in selection mode: toggles [key]. No-op outside selection mode. */
    fun toggle(current: Selection, key: RatedMangaKey): Selection {
        if (!current.selectionMode) return current
        val updated = if (key in current.selectedKeys) current.selectedKeys - key else current.selectedKeys + key
        return current.copy(selectedKeys = updated)
    }

    fun clear(): Selection = EMPTY

    /** Adds every key in [memberKeys] to the selection and enters selection mode. */
    fun selectAll(current: Selection, memberKeys: Collection<RatedMangaKey>): Selection =
        current.copy(selectionMode = true, selectedKeys = current.selectedKeys + memberKeys)
}
// KMK <--
