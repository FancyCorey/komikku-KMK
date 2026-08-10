package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.paging.PagingSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
// KMK_CLAUDE_REMAINING_FIXTURE_BLOCKER_IMPLEMENTATION_PLAN_2026-08-03 Phase 1: proves the Browse
// debug fixture always resolves to the deterministic error terminal state declared by the
// `browse-deterministic-source-failure` host fixture (network-unavailable, retryable), regardless
// of the requested page key, and never touches a real source/package/repository identity.
class BrowseDeterministicFixturePagingSourceTest {

    @Test
    fun `load always returns a deterministic Error result`() = runTest {
        val pagingSource = BrowseDeterministicFixturePagingSource()

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 25, placeholdersEnabled = false),
        )

        assertTrue(result is PagingSource.LoadResult.Error)
        val error = (result as PagingSource.LoadResult.Error).throwable
        assertTrue(error is BrowseDeterministicFixtureException)
    }

    @Test
    fun `the fixture exception message never contains a real identity placeholder`() {
        val message = BrowseDeterministicFixtureException().message.orEmpty()

        // The fixture must be source-generic: no real source/extension/package/repository name is
        // ever embedded, since none is ever read by this class in the first place.
        assertFalse(message.contains("http"))
        assertFalse(message.contains("pkg."))
        assertTrue(message.isNotBlank())
    }

    @Test
    fun `getRefreshKey is always null (no meaningful position to resume from)`() {
        val pagingSource = BrowseDeterministicFixturePagingSource()
        assertTrue(
            pagingSource.getRefreshKey(
                androidx.paging.PagingState(
                    pages = emptyList(),
                    anchorPosition = null,
                    config = androidx.paging.PagingConfig(pageSize = 25),
                    leadingPlaceholderCount = 0,
                ),
            ) == null,
        )
    }
}
// KMK <--
