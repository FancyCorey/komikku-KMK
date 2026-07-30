package exh.recs

import exh.recs.matching.MangaIdentityKey

// KMK v0.8.16 -->
/**
 * Pure selection-mode reducer for For You manga cards. Generic over the value stored per key so it
 * can be unit tested without constructing a real [tachiyomi.domain.manga.model.Manga] -- the screen
 * uses `Map<MangaIdentityKey, Manga>`.
 *
 * Rules: a long-press on an unselected card enters selection mode and selects that card. Any tap
 * while in selection mode toggles the tapped card (including the last one, which exits selection
 * mode by leaving the map empty). Normal tap-to-open behavior is the caller's responsibility when
 * the map is empty -- this policy only ever describes the selection map itself.
 */
object ForYouSelectionPolicy {

    fun <T> longPress(current: Map<MangaIdentityKey, T>, key: MangaIdentityKey, value: T): Map<MangaIdentityKey, T> =
        if (key in current) current else current + (key to value)

    fun <T> toggle(current: Map<MangaIdentityKey, T>, key: MangaIdentityKey, value: T): Map<MangaIdentityKey, T> =
        if (key in current) current - key else current + (key to value)

    fun <T> clear(): Map<MangaIdentityKey, T> = emptyMap()

    fun <T> isSelectionMode(current: Map<MangaIdentityKey, T>): Boolean = current.isNotEmpty()

    /** Actions such as Find best version / Open are only valid with exactly one item selected. */
    fun <T> isSingleSelection(current: Map<MangaIdentityKey, T>): Boolean = current.size == 1
}
// KMK <--
