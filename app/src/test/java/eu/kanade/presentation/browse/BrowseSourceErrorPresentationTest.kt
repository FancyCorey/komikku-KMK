package eu.kanade.presentation.browse

import androidx.paging.LoadState
import eu.kanade.tachiyomi.source.DebugBrowseFixtureSource
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseDeterministicFixtureException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrowseSourceErrorPresentationTest {

    @Test
    fun `only deterministic Browse fixture failure receives fixture error treatment`() {
        assertTrue(isDeterministicBrowseFixtureError(BrowseDeterministicFixtureException()))
        assertFalse(isDeterministicBrowseFixtureError(IllegalStateException("source failure")))
        assertFalse(isDeterministicBrowseFixtureError(IllegalStateException()))
    }

    @Test
    fun `only deterministic Browse fixture source receives empty-state error treatment`() {
        assertTrue(isDeterministicBrowseFixtureSource(DebugBrowseFixtureSource()))
        assertTrue(isDeterministicBrowseFixtureSource(null, DebugBrowseFixtureSource.ID))
        assertFalse(isDeterministicBrowseFixtureSource(null))
    }

    @Test
    fun `empty-state resolver keeps fixture error and retry semantics explicit`() {
        val policy = resolveBrowseEmptyStatePolicy(
            sourceId = DebugBrowseFixtureSource.ID,
            sourceName = DebugBrowseFixtureSource().name,
            isDeterministicFixtureSource = true,
            isLocalSource = false,
            localSourceHelpAvailable = false,
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )

        assertTrue(policy.message == BrowseEmptyStateMessage.SOURCE_UNAVAILABLE)
        assertTrue(policy.showRetry)

        val explicitIdentity = resolveBrowseEmptyStatePolicy(
            sourceId = null,
            sourceName = null,
            isDeterministicFixtureSource = true,
            isLocalSource = false,
            localSourceHelpAvailable = false,
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )

        assertTrue(explicitIdentity.message == BrowseEmptyStateMessage.SOURCE_UNAVAILABLE)
        assertTrue(explicitIdentity.showRetry)

        val identityWithoutRouteState = resolveBrowseEmptyStatePolicy(
            sourceId = DebugBrowseFixtureSource.ID,
            sourceName = DebugBrowseFixtureSource().name,
            isLocalSource = false,
            localSourceHelpAvailable = false,
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )

        assertTrue(identityWithoutRouteState.message == BrowseEmptyStateMessage.NO_RESULTS)
    }

    @Test
    fun `empty-state resolver preserves ordinary and local-source branches`() {
        val ordinary = resolveBrowseEmptyStatePolicy(
            sourceId = 42L,
            sourceName = "Ordinary source",
            isLocalSource = false,
            localSourceHelpAvailable = false,
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )
        val local = resolveBrowseEmptyStatePolicy(
            sourceId = 42L,
            sourceName = "Local",
            isLocalSource = true,
            localSourceHelpAvailable = true,
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )

        assertTrue(ordinary.message == BrowseEmptyStateMessage.NO_RESULTS)
        assertTrue(ordinary.showRetry)
        assertTrue(local.message == BrowseEmptyStateMessage.NO_RESULTS)
        assertTrue(!local.showRetry)
    }

    @Test
    fun `empty-state resolver maps non-fixture errors without exposing source details`() {
        val policy = resolveBrowseEmptyStatePolicy(
            sourceId = 42L,
            sourceName = "Ordinary source",
            isLocalSource = false,
            localSourceHelpAvailable = false,
            refresh = LoadState.Error(IllegalStateException("controlled failure")),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )

        assertTrue(policy.message == BrowseEmptyStateMessage.ERROR)
        assertTrue(policy.error != null)
        assertTrue(policy.showRetry)
    }
}
