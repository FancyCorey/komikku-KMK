package exh.recs.links

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.interactor.DeleteCrossSourceMangaLink
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.model.CrossSourceMangaLink

/**
 * KMK security-hardening pass: [LinkGroupManagementScreenModel.deleteGroup] (one of the two
 * `runCatching` sites explicitly named as confirmed examples in the audit) previously wrapped its
 * `deleteCrossSourceMangaLink.awaitByGroupId()` suspend call in
 * `runCatching { ... }.onFailure { e -> if (e is CancellationException) throw e; reload() } }` --
 * this is the safe onFailure-rethrow idiom and was NOT swallowing cancellation, but the fix pass
 * standardized every suspend boundary in this file to explicit try/catch for consistency. These tests
 * verify the corrected behavior directly (no Android Context needed for this screen model).
 */
class LinkGroupManagementScreenModelDeleteGroupCancellationTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun link(groupId: String, source: Long, url: String) = CrossSourceMangaLink(
        source = source,
        url = url,
        groupId = groupId,
        title = "Manga $url",
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun buildModel(
        links: List<CrossSourceMangaLink>,
        deleteCrossSourceMangaLink: DeleteCrossSourceMangaLink,
    ): LinkGroupManagementScreenModel {
        val getCrossSourceMangaLinks = mockk<GetCrossSourceMangaLinks>()
        coEvery { getCrossSourceMangaLinks.awaitByGroupId(any()) } returns links
        coEvery { getCrossSourceMangaLinks.awaitAll() } returns links
        return LinkGroupManagementScreenModel(
            focusedGroupId = null,
            getCrossSourceMangaLinks = getCrossSourceMangaLinks,
            deleteCrossSourceMangaLink = deleteCrossSourceMangaLink,
        )
    }

    @Test
    fun `a successful deleteGroup removes the group from state without reloading`() = runTest {
        val deleteCrossSourceMangaLink = mockk<DeleteCrossSourceMangaLink>(relaxed = true)
        val model = buildModel(listOf(link("g1", 1L, "/a")), deleteCrossSourceMangaLink)
        advanceUntilIdle()

        model.deleteGroup("g1")
        advanceUntilIdle()

        coVerify(exactly = 1) { deleteCrossSourceMangaLink.awaitByGroupId("g1") }
        assertTrue(
            model.state.value is LinkGroupManagementScreenModel.State.Empty,
            "expected Empty state after deleting the only group, got ${model.state.value}",
        )
    }

    @Test
    fun `an ordinary exception from the delete rolls back via reload, not a crash`() = runTest {
        val deleteCrossSourceMangaLink = mockk<DeleteCrossSourceMangaLink>()
        coEvery { deleteCrossSourceMangaLink.awaitByGroupId("g1") } throws IllegalStateException("db hiccup")
        val model = buildModel(listOf(link("g1", 1L, "/a")), deleteCrossSourceMangaLink)
        advanceUntilIdle()

        model.deleteGroup("g1")
        advanceUntilIdle()

        // The optimistic removal is rolled back by reload() re-fetching the still-present group.
        val state = model.state.value as? LinkGroupManagementScreenModel.State.Success
        assertTrue(state != null, "expected a rolled-back Success state, got ${model.state.value}")
        assertEquals(1, state!!.groups.size)
    }

    @Test
    fun `cancellation during the delete propagates instead of triggering a rollback reload`() = runTest {
        val deleteCrossSourceMangaLink = mockk<DeleteCrossSourceMangaLink>()
        coEvery { deleteCrossSourceMangaLink.awaitByGroupId("g1") } throws CancellationException("scope cancelled")
        val model = buildModel(listOf(link("g1", 1L, "/a")), deleteCrossSourceMangaLink)
        advanceUntilIdle()

        model.deleteGroup("g1")
        advanceUntilIdle()

        // Cancellation must never trigger the reload() rollback path -- the optimistic removal
        // (Empty state) stays as the last state the cancelled coroutine produced.
        assertTrue(
            model.state.value is LinkGroupManagementScreenModel.State.Empty,
            "cancellation must not trigger a rollback reload, got ${model.state.value}",
        )
    }
}
