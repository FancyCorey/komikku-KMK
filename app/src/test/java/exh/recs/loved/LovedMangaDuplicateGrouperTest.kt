package exh.recs.loved

// KMK -->
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste

class LovedMangaDuplicateGrouperTest {

    // Helper: create a GroupInput with only the fields relevant to the test.
    private fun input(
        key: String,
        title: String,
        description: String = "",
        author: String? = null,
        artist: String? = null,
        linkGroupId: String? = null,
    ) = LovedMangaDuplicateGrouper.GroupInput(
        key = key,
        title = title,
        description = description,
        author = author,
        artist = artist,
        linkGroupId = linkGroupId,
    )

    // Descriptions used in tests. longDesc >= MIN_DESCRIPTION_LENGTH (50).
    // longDescSim1/2 are 80+ chars and share most tokens (Jaccard >= 0.85).
    private val longDesc = "A " + "x".repeat(60) // 62 chars, exact duplicate tests
    private val longDesc2 = "B " + "y".repeat(60) // 62 chars, different content
    private val shortDesc = "Short description" // < 50 chars
    private val longDescSim1 = "a young hero reincarnated in another world must save the kingdom from ancient evil forces awakening"
    // 95 chars, all tokens except last 2 shared with sim2
    private val longDescSim2 = "a young hero reincarnated in another world must save the kingdom from ancient evil forces awakened"
    // 95 chars (awakening→awakened): Jaccard ≈ 14/15 ≈ 0.93 → above 0.85
    private val longDescDiff = "a romance story about two office workers falling in love through workplace misunderstandings slowly"
    // 98 chars, completely different content from sim1

    // Titles used in similar-title tests.
    // 9 shared tokens + 1 different each → intersection=9, union=11 → Jaccard=9/11≈0.818 ≥ 0.80
    private val similarTitleA = "sword hero adventure protagonist chapter arc story world quest bonus"
    private val similarTitleB = "sword hero adventure protagonist chapter arc story world tale bonus"

    // --- Legacy inclusion / exclusion tests (preserved from v0.7.0) ---

    @Test
    fun `love entries are included`() {
        val inputs = listOf(input("k1", "Title A", longDesc))
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(1, result.size)
        assertEquals("k1", result[0].primaryKey)
    }

    @Test
    fun `each entry produces at least one group`() {
        val inputs = listOf(
            input("k1", "Title A", longDesc),
            input("k2", "Title B", longDesc2),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(2, result.size)
    }

    @Test
    fun `same title and same non-blank description groups`() {
        val inputs = listOf(
            input("k1", "My Manga", longDesc),
            input("k2", "My Manga", longDesc),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(1, result.size)
        assertEquals("k1", result[0].primaryKey)
        assertEquals(2, result[0].versionCount)
        assertTrue("k2" in result[0].memberKeys)
    }

    @Test
    fun `same title but different description does not group`() {
        val inputs = listOf(
            input("k1", "My Manga", longDesc),
            input("k2", "My Manga", longDesc2),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(2, result.size)
        assertTrue(result.all { it.versionCount == 1 })
    }

    @Test
    fun `blank description does not group`() {
        val inputs = listOf(
            input("k1", "My Manga", ""),
            input("k2", "My Manga", ""),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(2, result.size)
        assertTrue(result.all { it.versionCount == 1 })
    }

    @Test
    fun `short description below threshold does not group`() {
        val inputs = listOf(
            input("k1", "My Manga", shortDesc),
            input("k2", "My Manga", shortDesc),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(2, result.size)
    }

    @Test
    fun `title case differences do not prevent grouping`() {
        val inputs = listOf(
            input("k1", "MY MANGA", longDesc),
            input("k2", "my manga", longDesc),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(1, result.size)
        assertEquals(2, result[0].versionCount)
    }

    @Test
    fun `grouping disabled — all entries returned as size-1 groups`() {
        val inputs = listOf(
            input("k1", "My Manga", longDesc),
            input("k2", "My Manga", longDesc),
            input("k3", "My Manga", longDesc),
        )
        val standalone = inputs.map { LovedMangaDuplicateGrouper.computeGroups(listOf(it)) }
        assertTrue(standalone.all { it.size == 1 && it[0].versionCount == 1 })
    }

    @Test
    fun `empty input returns empty list`() {
        val result = LovedMangaDuplicateGrouper.computeGroups(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `first entry in group becomes primary key`() {
        val inputs = listOf(
            input("k1", "Manga", longDesc),
            input("k2", "Manga", longDesc),
            input("k3", "Manga", longDesc),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(1, result.size)
        assertEquals("k1", result[0].primaryKey)
        assertEquals(listOf("k1", "k2", "k3"), result[0].memberKeys)
    }

    @Test
    fun `output order follows first-occurrence order`() {
        val inputs = listOf(
            input("k1", "Title A", longDesc), // standalone (different desc from k3)
            input("k2", "Title B", longDesc2), // group 2 primary
            input("k3", "Title A", ""), // standalone (no desc)
            input("k4", "Title B", longDesc2), // merges into group 2
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(3, result.size)
        assertEquals("k1", result[0].primaryKey)
        assertEquals("k2", result[1].primaryKey)
        assertEquals("k3", result[2].primaryKey)
        assertEquals(2, result[1].versionCount)
    }

    @Test
    fun `three-way group with mixed standalone entries`() {
        val inputs = listOf(
            input("k1", "Same Title", longDesc),
            input("k2", "Other Title", longDesc2),
            input("k3", "Same Title", longDesc),
            input("k4", "Same Title", longDesc),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(2, result.size)
        val sameGroup = result.first { it.primaryKey == "k1" }
        assertEquals(3, sameGroup.versionCount)
        assertEquals("k2", result.first { it.primaryKey == "k2" }.primaryKey)
    }

    // --- Link group tests ---

    @Test
    fun `same link group groups entries even with different titles`() {
        val inputs = listOf(
            input("k1", "Solo Leveling", linkGroupId = "group1"),
            input("k2", "Only I Level Up", linkGroupId = "group1"),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(1, result.size)
        assertEquals("k1", result[0].primaryKey)
        assertEquals(2, result[0].versionCount)
        assertTrue("k2" in result[0].memberKeys)
        assertEquals(LovedMangaDuplicateGrouper.LovedMangaGroupReason.LINK_GROUP, result[0].reason)
    }

    @Test
    fun `same link group groups entries even with different descriptions`() {
        val inputs = listOf(
            input("k1", "My Manga", longDesc, linkGroupId = "group1"),
            input("k2", "My Manga", longDesc2, linkGroupId = "group1"),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(1, result.size)
        assertEquals(2, result[0].versionCount)
        assertEquals(LovedMangaDuplicateGrouper.LovedMangaGroupReason.LINK_GROUP, result[0].reason)
    }

    @Test
    fun `different link groups do not merge`() {
        val inputs = listOf(
            input("k1", "My Manga", linkGroupId = "group1"),
            input("k2", "My Manga", linkGroupId = "group2"),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(2, result.size)
        assertTrue(result.all { it.versionCount == 1 })
    }

    @Test
    fun `linked entries keep first-sorted entry as primary`() {
        val inputs = listOf(
            input("k1", "Title First", linkGroupId = "group1"),
            input("k2", "Title Second", linkGroupId = "group1"),
            input("k3", "Title Third", linkGroupId = "group1"),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(1, result.size)
        assertEquals("k1", result[0].primaryKey)
        assertEquals(listOf("k1", "k2", "k3"), result[0].memberKeys)
    }

    // --- Metadata strong match tests ---

    @Test
    fun `exact normalized title plus same author groups`() {
        val inputs = listOf(
            input("k1", "My Manga", author = "Author Name"),
            input("k2", "My Manga", author = "Author Name"),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(1, result.size)
        assertEquals("k1", result[0].primaryKey)
        assertEquals(2, result[0].versionCount)
        assertEquals(LovedMangaDuplicateGrouper.LovedMangaGroupReason.TITLE_AND_AUTHOR, result[0].reason)
    }

    @Test
    fun `exact normalized title plus same artist groups`() {
        val inputs = listOf(
            input("k1", "My Manga", artist = "Artist Name"),
            input("k2", "My Manga", artist = "Artist Name"),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(1, result.size)
        assertEquals(2, result[0].versionCount)
        assertEquals(LovedMangaDuplicateGrouper.LovedMangaGroupReason.TITLE_AND_ARTIST, result[0].reason)
    }

    @Test
    fun `exact normalized title plus different author does not group when descriptions differ`() {
        val inputs = listOf(
            input("k1", "My Manga", description = longDesc, author = "Author A"),
            input("k2", "My Manga", description = longDesc2, author = "Author B"),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(2, result.size)
        assertTrue(result.all { it.versionCount == 1 })
    }

    @Test
    fun `same title with blank author artist and blank description does not group`() {
        val inputs = listOf(
            input("k1", "My Manga", description = "", author = null, artist = null),
            input("k2", "My Manga", description = "", author = null, artist = null),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(2, result.size)
        assertTrue(result.all { it.versionCount == 1 })
        assertTrue(result.all { it.reason == LovedMangaDuplicateGrouper.LovedMangaGroupReason.STANDALONE })
    }

    // --- Description similarity tests ---

    @Test
    fun `exact title plus near-identical long descriptions groups`() {
        // longDescSim1 and longDescSim2 differ by only 1 word over ~15 tokens → Jaccard ≥ 0.85
        val inputs = listOf(
            input("k1", "My Manga", longDescSim1),
            input("k2", "My Manga", longDescSim2),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(1, result.size)
        assertEquals(2, result[0].versionCount)
        assertEquals(LovedMangaDuplicateGrouper.LovedMangaGroupReason.TITLE_AND_SIMILAR_DESCRIPTION, result[0].reason)
    }

    @Test
    fun `exact title plus clearly different long descriptions does not group`() {
        val inputs = listOf(
            input("k1", "My Manga", longDescSim1),
            input("k2", "My Manga", longDescDiff),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(2, result.size)
        assertTrue(result.all { it.versionCount == 1 })
    }

    @Test
    fun `short descriptions do not group by description`() {
        val inputs = listOf(
            input("k1", "My Manga", shortDesc),
            input("k2", "My Manga", shortDesc),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(2, result.size)
        assertTrue(result.all { it.versionCount == 1 })
    }

    // --- Slight title difference tests ---

    @Test
    fun `similar title plus same author groups`() {
        // similarTitleA and similarTitleB share 9/11 tokens → Jaccard = 9/11 ≈ 0.818 ≥ 0.80
        val inputs = listOf(
            input("k1", similarTitleA, author = "Author Name"),
            input("k2", similarTitleB, author = "Author Name"),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(1, result.size)
        assertEquals(2, result[0].versionCount)
        assertEquals(LovedMangaDuplicateGrouper.LovedMangaGroupReason.SIMILAR_TITLE_AND_AUTHOR, result[0].reason)
    }

    @Test
    fun `similar title plus same artist groups`() {
        val inputs = listOf(
            input("k1", similarTitleA, artist = "Artist Name"),
            input("k2", similarTitleB, artist = "Artist Name"),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(1, result.size)
        assertEquals(2, result[0].versionCount)
        assertEquals(LovedMangaDuplicateGrouper.LovedMangaGroupReason.SIMILAR_TITLE_AND_ARTIST, result[0].reason)
    }

    @Test
    fun `similar title without author or artist does not group`() {
        val inputs = listOf(
            input("k1", similarTitleA, author = null, artist = null),
            input("k2", similarTitleB, author = null, artist = null),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(2, result.size)
        assertTrue(result.all { it.versionCount == 1 })
    }

    @Test
    fun `translated or romanized title without link group or supporting metadata does not group`() {
        // "Solo Leveling" vs "Only I Level Up" — different title, no author/artist/linkGroup
        val inputs = listOf(
            input("k1", "Solo Leveling"),
            input("k2", "Only I Level Up"),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(2, result.size)
        assertTrue(result.all { it.versionCount == 1 })
    }

    // --- Safety tests ---

    @Test
    fun `title-only duplicate names remain separate`() {
        val inputs = listOf(
            input("k1", "Hero"),
            input("k2", "Hero"),
            input("k3", "Hero"),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(3, result.size)
        assertTrue(result.all { it.versionCount == 1 })
        assertTrue(result.all { it.reason == LovedMangaDuplicateGrouper.LovedMangaGroupReason.STANDALONE })
    }

    @Test
    fun `blank metadata entries remain standalone`() {
        val inputs = listOf(
            input("k1", "Some Title"),
            input("k2", "Other Title"),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(2, result.size)
        assertTrue(result.all { it.reason == LovedMangaDuplicateGrouper.LovedMangaGroupReason.STANDALONE })
    }

    @Test
    fun `output order follows first occurrence most recent representative`() {
        // Entries are pre-sorted by recency (most recent first). Order of group representatives in output
        // must follow the order in which the primary key first appeared.
        val inputs = listOf(
            input("k1", "Title A", author = "Author A"),
            input("k2", "Title B", author = "Author B"),
            input("k3", "Title A", author = "Author A"), // merges into k1's group
            input("k4", "Title B", author = "Author B"), // merges into k2's group
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(2, result.size)
        assertEquals("k1", result[0].primaryKey)
        assertEquals("k2", result[1].primaryKey)
        assertEquals(2, result[0].versionCount)
        assertEquals(2, result[1].versionCount)
    }

    @Test
    fun `version count equals grouped member count`() {
        val inputs = listOf(
            input("k1", "Manga", linkGroupId = "g1"),
            input("k2", "Manga", linkGroupId = "g1"),
            input("k3", "Manga", linkGroupId = "g1"),
        )
        val result = LovedMangaDuplicateGrouper.computeGroups(inputs)
        assertEquals(1, result.size)
        assertEquals(3, result[0].versionCount)
        assertEquals(3, result[0].memberKeys.size)
    }

    // --- Screen model integration: link groups passed into grouper ---

    @Test
    fun `State Success with link group map groups entries via linkGroupId`() {
        val taste1 = MangaTaste(
            mangaId = 1L,
            source = 100L,
            url = "url1",
            title = "Solo Leveling",
            rating = MangaRating.LOVE.value,
            createdAt = 1000L,
            updatedAt = 2000L,
        )
        val taste2 = MangaTaste(
            mangaId = 2L,
            source = 200L,
            url = "url2",
            title = "Only I Level Up",
            rating = MangaRating.LOVE.value,
            createdAt = 900L,
            updatedAt = 1800L,
        )
        val entries = listOf(
            LovedMangaEntry(taste = taste1, manga = null),
            LovedMangaEntry(taste = taste2, manga = null),
        )
        // Both entries share a confirmed cross-source link group
        val linkGroupByKey = mapOf(
            "100|url1" to "confirmed-group-id",
            "200|url2" to "confirmed-group-id",
        )
        val state = LovedMangaScreenModel.State.Success(
            entries = entries,
            groupDuplicates = true,
            linkGroupByKey = linkGroupByKey,
            confirmedLinkGroupByKey = linkGroupByKey,
        )
        val display = state.displayItems
        assertEquals(1, display.size)
        assertEquals(2, display[0].versionCount)
        assertEquals(taste1, display[0].taste) // first entry (most recent) is representative
    }

    @Test
    fun `State Success with empty link group map falls back to metadata grouping`() {
        val taste1 = MangaTaste(
            mangaId = 1L,
            source = 100L,
            url = "url1",
            title = "My Manga",
            rating = MangaRating.LOVE.value,
            createdAt = 1000L,
            updatedAt = 2000L,
        )
        val taste2 = MangaTaste(
            mangaId = 2L,
            source = 200L,
            url = "url2",
            title = "My Manga",
            rating = MangaRating.LOVE.value,
            createdAt = 900L,
            updatedAt = 1800L,
        )
        val entries = listOf(
            LovedMangaEntry(taste = taste1, manga = null),
            LovedMangaEntry(taste = taste2, manga = null),
        )
        // No link groups → titles match but no description → standalone
        val state = LovedMangaScreenModel.State.Success(
            entries = entries,
            groupDuplicates = true,
            linkGroupByKey = emptyMap(),
        )
        val display = state.displayItems
        // taste.title matches but no description → won't group
        assertEquals(2, display.size)
    }

    // --- Normalization tests ---

    @Test
    fun `normalizeTitle handles punctuation separators`() {
        assertEquals(
            LovedMangaDuplicateGrouper.normalizeTitle("My Manga"),
            LovedMangaDuplicateGrouper.normalizeTitle("My-Manga"),
        )
        assertEquals(
            LovedMangaDuplicateGrouper.normalizeTitle("My Manga"),
            LovedMangaDuplicateGrouper.normalizeTitle("My: Manga"),
        )
    }

    @Test
    fun `tokenJaccardSimilarity returns 1 for identical strings`() {
        assertEquals(1.0, LovedMangaDuplicateGrouper.tokenJaccardSimilarity("hello world", "hello world"), 0.001)
    }

    @Test
    fun `tokenJaccardSimilarity returns 0 for disjoint strings`() {
        assertEquals(0.0, LovedMangaDuplicateGrouper.tokenJaccardSimilarity("hello world", "foo bar"), 0.001)
    }

    @Test
    fun `tokenJaccardSimilarity returns correct value for partial overlap`() {
        // "a b c" vs "a b d" → intersection=2, union=4 → 0.5
        val similarity = LovedMangaDuplicateGrouper.tokenJaccardSimilarity("a b c", "a b d")
        assertEquals(0.5, similarity, 0.001)
    }

    @Test
    fun `similar title threshold requires enough shared tokens`() {
        // similarTitleA and similarTitleB: 10 tokens each, 9 shared, 1 different each
        // intersection=9, union=11 → 9/11 ≈ 0.818 ≥ 0.80
        val sim = LovedMangaDuplicateGrouper.tokenJaccardSimilarity(
            LovedMangaDuplicateGrouper.normalizeTitle(similarTitleA),
            LovedMangaDuplicateGrouper.normalizeTitle(similarTitleB),
        )
        assertTrue(sim >= LovedMangaDuplicateGrouper.TITLE_SIMILARITY_THRESHOLD) { "Expected >= 0.80, got $sim" }
    }
}
// KMK <--
