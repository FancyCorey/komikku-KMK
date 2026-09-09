package eu.kanade.tachiyomi.ui.manga.track

import tachiyomi.domain.tracker.model.LocalTrackedWork

// KMK v0.8.21-fix2 -->
/**
 * A shared presentation-level row for [eu.kanade.presentation.track.TrackInfoDialogHome]: either an
 * [External] network tracker (AniList, MAL, ...) or the single [Local] entry. Deliberately not built
 * on [eu.kanade.tachiyomi.data.track.Tracker] -- that interface carries real network/auth/search
 * semantics ([eu.kanade.tachiyomi.data.track.Tracker.login], `search`, score/date support flags)
 * that would be false for a purely local, offline concept. [Local] and [External] rows are
 * independently selectable and neither affects the other's state.
 */
sealed interface TrackerEntry {
    data class External(val item: TrackItem) : TrackerEntry
    data class Local(val work: LocalTrackedWork?) : TrackerEntry

    // KMK v0.8.21-fix5: R5 correction -- the entries list used to be built inline at
    // TrackInfoDialog.kt's single call site, which a coexistence test could only prove by copying
    // the same expression into a test-local helper (tautological -- it would still pass if
    // production drifted). Exposing the construction here, as the one production owner, lets a test
    // invoke the real function instead of a copy, and is the single place any future caller must
    // route through to add another entry kind.
    companion object {
        /**
         * Builds the ordered, bounded list of rows [eu.kanade.presentation.track.TrackInfoDialogHome]
         * displays: every [External] tracker in [trackItems]' order, followed by exactly one [Local]
         * row (present even when [localWork] is `null`, so the local-tracking action is always
         * offered). [Local] and [External] entries are structurally independent -- constructing this
         * list never reads or mutates either side based on the other.
         */
        fun build(trackItems: List<TrackItem>, localWork: LocalTrackedWork?): List<TrackerEntry> =
            trackItems.map(::External) + Local(localWork)
    }
}
// KMK <--
