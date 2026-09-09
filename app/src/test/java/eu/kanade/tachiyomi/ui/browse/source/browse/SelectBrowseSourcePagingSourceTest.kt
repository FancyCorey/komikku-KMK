package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.paging.PagingSource
import androidx.paging.PagingState
import eu.kanade.tachiyomi.source.DebugBrowseFixtureSource
import exh.metadata.metadata.RaisedSearchMetadata
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

// KMK -->
// Direct coverage for
// the release-gating decision consumed by BrowseSourceScreenModel.createSourcePagingSource(). Same
// contract as SelectSourceEvaluationRunnerTest -- the fixture path only activates when both
// isDebugBuild is true and the (private, debug-build-only) fixture toggle is enabled.
class SelectBrowseSourcePagingSourceTest {

    @Test
    fun `debug build activates fixture only for the registered fixture source`() {
        assertSame(true, shouldUseBrowseFixture(true, DebugBrowseFixtureSource.ID))
        assertSame(false, shouldUseBrowseFixture(true, 1234L))
        assertSame(false, shouldUseBrowseFixture(false, DebugBrowseFixtureSource.ID))
    }

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
            sourceId = DebugBrowseFixtureSource.ID,
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
            sourceId = DebugBrowseFixtureSource.ID,
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
            sourceId = DebugBrowseFixtureSource.ID,
            fixtureModeEnabled = true,
            realPagingSourceProvider = { real },
            fixturePagingSourceProvider = { fixture },
        )

        assertSame(real, selected)
    }

    @Test
    fun `enabled mode for an unrelated route still selects the real paging source`() {
        val real = FakePagingSource()
        val fixture = FakePagingSource()

        val selected = selectBrowseSourcePagingSource(
            isDebugBuild = true,
            sourceId = 1234L,
            fixtureModeEnabled = true,
            realPagingSourceProvider = { real },
            fixturePagingSourceProvider = { fixture },
        )

        assertSame(real, selected)
    }
}
// KMK <--
