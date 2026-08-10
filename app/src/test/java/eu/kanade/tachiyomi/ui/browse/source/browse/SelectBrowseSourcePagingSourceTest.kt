package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.paging.PagingSource
import androidx.paging.PagingState
import exh.metadata.metadata.RaisedSearchMetadata
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

// KMK -->
// KMK_CLAUDE_REMAINING_FIXTURE_BLOCKER_IMPLEMENTATION_PLAN_2026-08-03 Phase 1: direct coverage for
// the release-gating decision consumed by BrowseSourceScreenModel.createSourcePagingSource(). Same
// contract as SelectSourceEvaluationRunnerTest -- the fixture path only activates when both
// isDebugBuild is true and the (private, debug-build-only) fixture toggle is enabled.
class SelectBrowseSourcePagingSourceTest {

    private class FakePagingSource : PagingSource<Long, Pair<Manga, RaisedSearchMetadata?>>() {
        override suspend fun load(params: LoadParams<Long>): LoadResult<Long, Pair<Manga, RaisedSearchMetadata?>> =
            LoadResult.Error(IllegalStateException("unused"))

        override fun getRefreshKey(state: PagingState<Long, Pair<Manga, RaisedSearchMetadata?>>): Long? = null
    }

    @Test
    fun `debug build with fixture mode enabled selects the fixture paging source`() {
        val real = FakePagingSource()
        val fixture = FakePagingSource()

        val selected = selectBrowseSourcePagingSource(
            isDebugBuild = true,
            fixtureModeEnabled = true,
            realPagingSourceProvider = { real },
            fixturePagingSourceProvider = { fixture },
        )

        assertSame(fixture, selected)
    }

    @Test
    fun `debug build with fixture mode disabled selects the real paging source`() {
        val real = FakePagingSource()
        val fixture = FakePagingSource()

        val selected = selectBrowseSourcePagingSource(
            isDebugBuild = true,
            fixtureModeEnabled = false,
            realPagingSourceProvider = { real },
            fixturePagingSourceProvider = { fixture },
        )

        assertSame(real, selected)
    }

    @Test
    fun `non-debug build with fixture mode enabled still selects the real paging source`() {
        val real = FakePagingSource()
        val fixture = FakePagingSource()

        val selected = selectBrowseSourcePagingSource(
            isDebugBuild = false,
            fixtureModeEnabled = true,
            realPagingSourceProvider = { real },
            fixturePagingSourceProvider = { fixture },
        )

        assertSame(real, selected)
    }
}
// KMK <--
