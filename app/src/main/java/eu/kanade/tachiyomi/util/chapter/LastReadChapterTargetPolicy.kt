package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.ui.manga.ChapterList

// KMK -->
/**
 * Resolves which chapter "Jump to last read" should scroll to.
 *
 * ## The rule (derived only from already-persisted reading state)
 *
 * There is no read timestamp on `Chapter`. `lastModifiedAt` is **not** one -- any write (bookmark
 * toggle, download bookkeeping) moves it -- so it is deliberately not used, and no new column or
 * migration is introduced. The rule instead reuses the two genuine progress fields (`read`,
 * `lastPageRead`) plus the existing ordering contract (`manga.sortDescending()`), mirroring
 * [getNextUnread]'s shape so the two compose:
 *
 * 1. **In progress wins.** If any chapter has `lastPageRead > 0 && !read`, target the one furthest
 *    along in *reading* order. That is where the user actually stopped.
 * 2. **Otherwise the last completed chapter.** If any chapter has `read`, target the one furthest
 *    along in *reading* order.
 * 3. **Otherwise there is no target** ([resolve] returns `null`). The caller disables the action. This
 *    deliberately does **not** fall back to "the last item in the list" -- an unread manga has no
 *    last-read chapter, and inventing one would be a lie.
 *
 * "Furthest along in reading order" depends on how the list is displayed. With `sortDescending` the
 * newest chapter is first, so the most recently read sits nearer the top -> take the **first** match.
 * Ascending -> take the **last** match. This is the exact mirror of [getNextUnread]'s `find` /
 * `findLast` split.
 *
 * ## Why this operates on the displayed list
 *
 * [resolve] takes the already-filtered, already-sorted `List<ChapterList>` the screen renders. That
 * gives three properties for free:
 *
 * - **Stable identity, not a fragile position.** [Target.chapterId] is the real chapter id; the index
 *   is only a scroll hint that is recomputed against live layout info.
 * - **Merged manga and duplicate chapter urls are safe.** Two sources can expose the same url, but each
 *   [ChapterList.Item] carries its own distinct `chapter.id`.
 * - **Filters are never silently changed.** A chapter hidden by the current filter is not in this list,
 *   so no target is produced and the action is simply unavailable.
 *
 * [ChapterList.MissingCount] separators are never candidates but still occupy an index, so
 * [Target.indexInList] stays aligned with what is rendered.
 */
object LastReadChapterTargetPolicy {

    /**
     * @param chapterId stable identity of the resolved chapter.
     * @param indexInList index within the **displayed** chapter list (separators included), used only
     * as a scroll hint.
     */
    data class Target(val chapterId: Long, val indexInList: Int)

    /**
     * @param chapters the displayed chapter list, already filtered and sorted.
     * @param sortDescending `manga.sortDescending()` -- how [chapters] is ordered.
     * @return the jump target, or `null` when nothing has been read yet.
     */
    fun resolve(chapters: List<ChapterList>, sortDescending: Boolean): Target? {
        if (chapters.isEmpty()) return null

        // Step 1: an in-progress chapter is the most precise "where I stopped" signal available.
        resolveMatching(chapters, sortDescending) { item ->
            !item.chapter.read && item.chapter.lastPageRead > 0
        }?.let { return it }

        // Step 2: otherwise the furthest completed chapter.
        return resolveMatching(chapters, sortDescending) { item -> item.chapter.read }
    }

    private inline fun resolveMatching(
        chapters: List<ChapterList>,
        sortDescending: Boolean,
        predicate: (ChapterList.Item) -> Boolean,
    ): Target? {
        val indices = if (sortDescending) chapters.indices else chapters.indices.reversed()
        for (index in indices) {
            val item = chapters[index] as? ChapterList.Item ?: continue
            if (predicate(item)) return Target(item.chapter.id, index)
        }
        return null
    }

    /**
     * Re-locates [chapterId] in the current list. The index captured at resolve time can go stale if
     * the list changes (refresh, filter change, download completing) between resolution and the scroll,
     * so the scroll owner re-resolves by identity and treats a missing chapter as "no longer available"
     * rather than scrolling to a wrong position.
     *
     * @return the current index, or `null` if the chapter is no longer in the displayed list.
     */
    fun currentIndexOf(chapters: List<ChapterList>, chapterId: Long): Int? {
        val index = chapters.indexOfFirst { it is ChapterList.Item && it.chapter.id == chapterId }
        return index.takeIf { it >= 0 }
    }

    /**
     * Converts a chapter-list index into an absolute `LazyColumn` index.
     *
     * `sharedChapterItems(...)` is emitted **last** in both the portrait and tablet layouts, and the
     * number of header items before it is conditional (metadata, related manga, and info buttons are
     * all optional). Deriving the offset from live layout info instead of hardcoding it is therefore
     * both exact and layout-independent.
     *
     * @param totalItemsCount `LazyListState.layoutInfo.totalItemsCount`.
     * @param chapterCount size of the displayed chapter list.
     * @return the absolute index, or `null` if the numbers are inconsistent (layout mid-recomposition),
     * in which case the caller must not scroll.
     */
    fun toLazyListIndex(totalItemsCount: Int, chapterCount: Int, indexInList: Int): Int? {
        if (totalItemsCount <= 0 || chapterCount <= 0) return null
        if (indexInList !in 0 until chapterCount) return null
        val headerCount = totalItemsCount - chapterCount
        if (headerCount < 0) return null
        return headerCount + indexInList
    }
}
// KMK <--
