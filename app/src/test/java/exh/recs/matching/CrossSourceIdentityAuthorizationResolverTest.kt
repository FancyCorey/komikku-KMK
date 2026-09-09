package exh.recs.matching

import exh.util.CrossSourceIdentityUndoJournal
import exh.util.FakeTasteRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.ReplaceCrossSourceIdentityDecisions
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionValue
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.CrossSourceRecordKey

class CrossSourceIdentityAuthorizationResolverTest {
    @Test
    fun `legacy membership is not authorization but explicit confirmation is`() = runTest {
        val repository = FakeTasteRepository()
        val get = GetCrossSourceIdentityDecisions(repository)
        val resolver = CrossSourceIdentityAuthorizationResolver(get)
        val members = listOf(link(1, "/a"), link(2, "/b"))
        assertEquals(listOf(members.first()), resolver.confirmedGroupMembers(1, "/a", members))
        assertFalse(resolver.isConfirmed(1, "/a", 2, "/b"))

        val controller = CrossSourceIdentityDecisionController(get, ReplaceCrossSourceIdentityDecisions(repository)) { 10_000 }
        controller.mutate(
            CrossSourceIdentityDecisionPolicy.canonicalPair(CrossSourceRecordKey(1, "/a"), CrossSourceRecordKey(2, "/b")),
            CrossSourceIdentityMutation.CONFIRM,
        )
        assertTrue(resolver.isConfirmed(1, "/a", 2, "/b"))
        assertEquals(members, resolver.confirmedGroupMembers(1, "/a", members))
        CrossSourceIdentityUndoJournal.clear()
    }

    @Test
    fun `only confirmed endpoints in the same legacy group become authorized presentation members`() {
        val links = listOf(
            CrossSourceMangaLink(1, "/a", "group", "A", 1, 1),
            CrossSourceMangaLink(2, "/b", "group", "B", 1, 1),
            CrossSourceMangaLink(3, "/c", "group", "C", 1, 1),
            CrossSourceMangaLink(4, "/d", "other", "D", 1, 1),
        )
        val confirmed = CrossSourceIdentityDecisionPolicy.userDecision(
            pair(1, "/a", 2, "/b"),
            CrossSourceIdentityDecisionValue.USER_CONFIRMED,
            previous = null,
            timestamp = 2,
        )
        val crossGroup = CrossSourceIdentityDecisionPolicy.userDecision(
            pair(1, "/a", 4, "/d"),
            CrossSourceIdentityDecisionValue.USER_CONFIRMED,
            previous = null,
            timestamp = 2,
        )

        val result = CrossSourceIdentityAuthorizationResolver.confirmedLinkGroupByKey(
            links,
            listOf(confirmed, crossGroup),
        )

        assertEquals(mapOf("1|/a" to "group", "2|/b" to "group"), result)
        assertFalse("3|/c" in result)
        assertFalse("4|/d" in result)
    }

    private fun link(source: Long, url: String) = CrossSourceMangaLink(source, url, "legacy", "Title", 1, 1)

    private fun pair(firstSource: Long, firstUrl: String, secondSource: Long, secondUrl: String) =
        CrossSourceIdentityDecisionPolicy.canonicalPair(
            CrossSourceRecordKey(firstSource, firstUrl),
            CrossSourceRecordKey(secondSource, secondUrl),
        )
}
