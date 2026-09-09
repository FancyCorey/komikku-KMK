package eu.kanade.tachiyomi.ui.browse.source.globalsearch

/** Alternate-source returns always require an explicit manga-row choice. */
internal object GlobalSearchSelectionPolicy {
    fun shouldAutoSelect(returnSelection: Boolean, resultCount: Int): Boolean =
        !returnSelection && resultCount == 1
}
