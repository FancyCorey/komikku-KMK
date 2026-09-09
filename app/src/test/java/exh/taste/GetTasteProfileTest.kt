package exh.taste

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.taste.interactor.GetTasteProfile
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.model.TagAlias
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TagTaste
import tachiyomi.domain.taste.repository.TasteRepository

class GetTasteProfileTest {

    @Test
    fun `loved manga tags contribute strong positive weight`() = runBlocking {
        val tasteRepo = fakeTasteRepo(
            mangaTastes = listOf(taste(mangaId = 1, rating = MangaRating.LOVE)),
        )
        val mangaRepo = fakeMangaRepo(mapOf(1L to manga(1L, listOf("Action", "Adventure"))))
        val profile = GetTasteProfile(tasteRepo, mangaRepo).await()

        assertTrue((profile.learnedTagWeights["action"] ?: 0.0) > 0.0)
        assertTrue((profile.learnedTagWeights["adventure"] ?: 0.0) > 0.0)
    }

    @Test
    fun `liked manga tags contribute smaller positive weight than loved`() = runBlocking {
        val tasteRepoLike = fakeTasteRepo(listOf(taste(1, MangaRating.LIKE)))
        val tasteRepoLove = fakeTasteRepo(listOf(taste(1, MangaRating.LOVE)))
        val mangaRepo = fakeMangaRepo(mapOf(1L to manga(1L, listOf("Romance"))))

        val profileLike = GetTasteProfile(tasteRepoLike, mangaRepo).await()
        val profileLove = GetTasteProfile(tasteRepoLove, mangaRepo).await()

        val likeWeight = profileLike.learnedTagWeights["romance"] ?: 0.0
        val loveWeight = profileLove.learnedTagWeights["romance"] ?: 0.0
        assertTrue(loveWeight > likeWeight) { "Love weight ($loveWeight) should exceed like weight ($likeWeight)" }
    }

    @Test
    fun `disliked manga tags contribute negative weight`() = runBlocking {
        val tasteRepo = fakeTasteRepo(listOf(taste(1, MangaRating.DISLIKE)))
        val mangaRepo = fakeMangaRepo(mapOf(1L to manga(1L, listOf("Harem"))))
        val profile = GetTasteProfile(tasteRepo, mangaRepo).await()

        assertTrue((profile.learnedTagWeights["harem"] ?: 0.0) < 0.0)
    }

    @Test
    fun `blocked tag group appears in blockedGroups set`() = runBlocking {
        val tasteRepo = fakeTasteRepo(
            tagTastes = listOf(tagTaste("harem", TagPreference.BLOCK)),
        )
        val mangaRepo = fakeMangaRepo(emptyMap())
        val profile = GetTasteProfile(tasteRepo, mangaRepo).await()

        assertTrue("harem" in profile.blockedGroups) { "Blocked tag should appear in blockedGroups" }
    }

    @Test
    fun `alias resolves Yuri and Girls Love to the same group key`() = runBlocking {
        val tasteRepo = fakeTasteRepo(
            mangaTastes = listOf(
                taste(mangaId = 1, rating = MangaRating.LOVE),
                taste(mangaId = 2, rating = MangaRating.LOVE),
            ),
            tagAliases = listOf(
                TagAlias("Yuri", "yuri", "girls_love", "Girls Love"),
                TagAlias("Girls Love", "girls love", "girls_love", "Girls Love"),
            ),
        )
        val mangaRepo = fakeMangaRepo(
            mapOf(
                1L to manga(1L, listOf("Yuri")),
                2L to manga(2L, listOf("Girls Love")),
            ),
        )
        val profile = GetTasteProfile(tasteRepo, mangaRepo).await()

        // Both tags should have contributed to the same group_key "girls_love"
        val girlsLoveWeight = profile.learnedTagWeights["girls_love"] ?: 0.0
        assertTrue(girlsLoveWeight >= 4.0) { "girls_love group weight should be at least 4.0 (2 Love×2), got $girlsLoveWeight" }
        assertFalse("yuri" in profile.learnedTagWeights) { "Raw 'yuri' key should not appear when alias resolved" }
        assertFalse("girls love" in profile.learnedTagWeights) { "Raw 'girls love' key should not appear when alias resolved" }
    }

    @Test
    fun `explicit preferred tag appears in explicitTagPreferences`() = runBlocking {
        val tasteRepo = fakeTasteRepo(
            tagTastes = listOf(tagTaste("villainess", TagPreference.PREFER)),
        )
        val mangaRepo = fakeMangaRepo(emptyMap())
        val profile = GetTasteProfile(tasteRepo, mangaRepo).await()

        assertEquals(TagPreference.PREFER.value, profile.explicitTagPreferences["villainess"])
    }

    @Test
    fun `learned weights are capped at 10 even if many manga share the same tag`() = runBlocking {
        val tastes = (1..20L).map { taste(it, MangaRating.LOVE) }
        val mangaMap = (1..20L).associate { id ->
            id to manga(id, listOf("Action"))
        }
        val tasteRepo = fakeTasteRepo(mangaTastes = tastes)
        val mangaRepo = fakeMangaRepo(mangaMap)
        val profile = GetTasteProfile(tasteRepo, mangaRepo).await()

        val actionWeight = profile.learnedTagWeights["action"] ?: 0.0
        assertEquals(10.0, actionWeight, 0.001) { "Weight should be capped at 10.0" }
    }

    @Test
    fun `missing manga row does not crash profile building`() = runBlocking {
        val tasteRepo = fakeTasteRepo(
            mangaTastes = listOf(taste(mangaId = 9999, rating = MangaRating.LOVE)),
        )
        // manga 9999 not in the repo - getMangaById throws
        val mangaRepo = fakeMangaRepo(emptyMap())
        val profile = GetTasteProfile(tasteRepo, mangaRepo).await()

        // Should gracefully return an empty profile, not crash
        assertTrue(profile.learnedTagWeights.isEmpty())
    }

    // --- Helpers ---

    private fun taste(mangaId: Long, rating: MangaRating) = MangaTaste(
        mangaId = mangaId,
        source = 1L,
        url = "/$mangaId",
        title = "Manga $mangaId",
        rating = rating.value,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun tagTaste(normalizedTag: String, preference: TagPreference) = TagTaste(
        normalizedTag = normalizedTag,
        displayName = normalizedTag,
        preference = preference.value,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun manga(id: Long, genres: List<String>): Manga =
        Manga.create().copy(id = id, ogGenre = genres.ifEmpty { null })

    private fun fakeTasteRepo(
        mangaTastes: List<MangaTaste> = emptyList(),
        tagTastes: List<TagTaste> = emptyList(),
        tagAliases: List<TagAlias> = emptyList(),
    ): TasteRepository = object : TasteRepository {
        override suspend fun getMangaTaste(mangaId: Long) = mangaTastes.find { it.mangaId == mangaId }
        override fun getMangaTasteAsFlow(mangaId: Long): Flow<MangaTaste?> = flowOf(mangaTastes.find { it.mangaId == mangaId })
        override suspend fun getAllMangaTastes() = mangaTastes
        // KMK --> v0.7.29
        override fun getAllMangaTastesAsFlow(): Flow<List<MangaTaste>> = flowOf(mangaTastes)
        // KMK <--
        override suspend fun upsertMangaTaste(taste: MangaTaste) {}
        override suspend fun deleteMangaTaste(mangaId: Long) {}
        override suspend fun deleteAllMangaTastes() {}
        override suspend fun getTagTaste(normalizedTag: String) = tagTastes.find { it.normalizedTag == normalizedTag }
        override suspend fun getAllTagTastes() = tagTastes
        override fun getAllTagTastesAsFlow(): Flow<List<TagTaste>> = flowOf(tagTastes)
        override suspend fun upsertTagTaste(tagTaste: TagTaste) {}
        override suspend fun deleteTagTaste(normalizedTag: String) {}
        override suspend fun getAllTagAliases() = tagAliases
        override suspend fun getTagAliasByNormalized(normalizedAlias: String) = tagAliases.find { it.normalizedAlias == normalizedAlias }
        override suspend fun upsertTagAlias(alias: TagAlias) {}
        override suspend fun deleteTagAlias(alias: String) {}
        override suspend fun getAllDisabledSourceIds() = emptyList<Long>()
        override fun getAllDisabledSourceIdsAsFlow(): Flow<List<Long>> = flowOf(emptyList())
        override suspend fun disableSource(sourceId: Long) {}
        override suspend fun enableSource(sourceId: Long) {}
        // KMK -->
        override suspend fun getMangaTaste(source: Long, url: String): MangaTaste? = mangaTastes.find { it.source == source && it.url == url }
        override fun getMangaTasteAsFlow(source: Long, url: String): Flow<MangaTaste?> = flowOf(mangaTastes.find { it.source == source && it.url == url })
        override suspend fun deleteMangaTaste(source: Long, url: String) {}
        // KMK --> v0.7.0: cross-source manga links
        override suspend fun getCrossSourceMangaLinksByGroupId(groupId: String): List<CrossSourceMangaLink> = emptyList()
        override suspend fun getCrossSourceMangaLinkBySourceUrl(source: Long, url: String): CrossSourceMangaLink? = null
        override suspend fun getAllCrossSourceMangaLinks(): List<CrossSourceMangaLink> = emptyList()
        override suspend fun upsertCrossSourceMangaLinks(links: List<CrossSourceMangaLink>) {}
        override suspend fun deleteCrossSourceMangaLink(source: Long, url: String) {}
        override suspend fun deleteCrossSourceMangaLinksByGroupId(groupId: String) {}
        override suspend fun deleteAllCrossSourceMangaLinks() {}
        // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: this local
        // fake predates TasteRepository's deleteCrossSourceGroupCompletely/
        // restoreCrossSourceGroupState (added for the Undo Journal's atomic group-restore
        // contract); this test never exercises either method, so no-op stubs are sufficient --
        // unlike app/src/test/java/exh/util/FakeTasteRepository.kt, which is the shared fake that
        // actually needs real semantics for GroupUndoService's own tests.
        override suspend fun deleteCrossSourceGroupCompletely(groupId: String) {}
        override suspend fun restoreCrossSourceGroupState(
            linkUpserts: List<CrossSourceMangaLink>,
            linkDeletes: List<Pair<Long, String>>,
            primaryUpserts: List<tachiyomi.domain.taste.model.CrossSourceGroupPrimary>,
            primaryDeletes: List<String>,
        ) {}
        // KMK <--
        // KMK --> v0.8.0
        override suspend fun getCrossSourceGroupPrimary(groupId: String): tachiyomi.domain.taste.model.CrossSourceGroupPrimary? = null
        override suspend fun getAllCrossSourceGroupPrimaries(): List<tachiyomi.domain.taste.model.CrossSourceGroupPrimary> = emptyList()
        override suspend fun upsertCrossSourceGroupPrimary(primary: tachiyomi.domain.taste.model.CrossSourceGroupPrimary) {}
        override suspend fun deleteCrossSourceGroupPrimary(groupId: String) {}
        override suspend fun deleteAllCrossSourceGroupPrimaries() {}
        // KMK <--
        override suspend fun getCrossSourceIdentityDecision(
            pair: tachiyomi.domain.taste.model.CrossSourceIdentityPair,
        ): tachiyomi.domain.taste.model.CrossSourceIdentityDecision? = null
        override suspend fun getAllCrossSourceIdentityDecisions(): List<tachiyomi.domain.taste.model.CrossSourceIdentityDecision> = emptyList()
        override suspend fun upsertCrossSourceIdentityDecisions(
            decisions: List<tachiyomi.domain.taste.model.CrossSourceIdentityDecision>,
        ) {}
        override suspend fun replaceCrossSourceIdentityDecision(
            expected: tachiyomi.domain.taste.model.CrossSourceIdentityDecision?,
            replacement: tachiyomi.domain.taste.model.CrossSourceIdentityDecision?,
        ): Boolean = false
        override suspend fun replaceCrossSourceIdentityDecisions(
            replacements: List<tachiyomi.domain.taste.model.CrossSourceIdentityReplacement>,
        ): Boolean = false
        override suspend fun tombstoneAllCrossSourceIdentityDecisions(updatedAt: Long) {}
        // KMK <-- (v0.7.3 source/url getMangaTaste)
    }

    private fun fakeMangaRepo(mangaById: Map<Long, Manga>): MangaRepository =
        object : StubMangaRepository() {
            override suspend fun getMangaById(id: Long): Manga =
                mangaById[id] ?: throw NoSuchElementException("Manga $id not found")
        }
}
