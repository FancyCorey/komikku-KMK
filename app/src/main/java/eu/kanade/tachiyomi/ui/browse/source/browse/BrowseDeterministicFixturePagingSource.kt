package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.paging.PagingSource
import androidx.paging.PagingState
import exh.metadata.metadata.RaisedSearchMetadata
import tachiyomi.domain.manga.model.Manga

// KMK -->
// KMK: named modes for the Browse debug-only fixture, separate from
// SourceEvaluationDebugFixtureMode. "Off" is the default/production value and must never activate
// the fixture path -- see selectBrowseSourcePagingSource, the only call site that reads this value
// and only does so behind `BuildConfig.DEBUG`.
enum class BrowseDebugFixtureMode(val prefValue: String) {
    OFF("off"),
    SOURCE_UNAVAILABLE("source_unavailable"),
    ;

    companion object {
        fun fromPrefValue(value: String): BrowseDebugFixtureMode =
            entries.find { it.prefValue == value } ?: OFF
    }
}

// Deterministic,
// source-generic failure matching the terminal state declared by the
// `browse-deterministic-source-failure` host fixture (network-unavailable, retryable, no library
// mutation). Never touches a real [eu.kanade.tachiyomi.source.Source], the network, or any real
// source/package/repository identity. Constructed only from
// `BrowseSourceScreenModel.createSourcePagingSource` behind `BuildConfig.DEBUG` and the private
// `browseFixtureFailureMode()` opt-in (KMK: previously and incorrectly read
// evaluationFixtureFailureMode(), coupling this to the unrelated Source Evaluation fixture) -- see
// selectBrowseSourcePagingSource for the gating.
class BrowseDeterministicFixturePagingSource : PagingSource<Long, Pair<Manga, RaisedSearchMetadata?>>() {
    override suspend fun load(
        params: LoadParams<Long>,
    ): LoadResult<Long, Pair<Manga, RaisedSearchMetadata?>> {
        return LoadResult.Error(BrowseDeterministicFixtureException())
    }

    override fun getRefreshKey(state: PagingState<Long, Pair<Manga, RaisedSearchMetadata?>>): Long? = null
}

class BrowseDeterministicFixtureException : Exception("fixture-source-unavailable")
// KMK <--
