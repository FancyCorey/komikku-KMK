package exh.recs.share

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->

class RecommendationBundleSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private fun sampleBundle() = RecommendationBundle(
        kmkRecsVersion = "KMK-Recs v0.7.5",
        appVersionName = "1.13.6",
        createdAt = 1_000_000L,
        title = "Test Top Picks",
        bundleType = RecommendationBundleType.TOP_PICKS,
        requiredSources = listOf(
            RecommendationBundleSource(
                sourceId = 12345L,
                sourceName = "MangaDex",
                sourceLang = "en",
                extensionPkgName = "eu.kanade.tachiyomi.extension.en.mangadex",
                extensionSignatureHash = "abc123",
            ),
        ),
        items = listOf(
            RecommendationBundleItem(
                title = "Test Manga",
                url = "/manga/test-slug",
                sourceId = 12345L,
                sourceName = "MangaDex",
                sourceLang = "en",
                extensionPkgName = "eu.kanade.tachiyomi.extension.en.mangadex",
                extensionSignatureHash = "abc123",
                author = "Author Name",
                genres = listOf("Action", "Adventure"),
                score = 0.85,
                matchedGroups = listOf("action", "isekai"),
            ),
        ),
    )

    @Test
    fun `round trip preserves schema fields`() {
        val bundle = sampleBundle()
        val encoded = json.encodeToString(bundle)
        val decoded = json.decodeFromString<RecommendationBundle>(encoded)

        assertEquals(RecommendationBundle.SCHEMA_ID, decoded.schema)
        assertEquals(RecommendationBundle.SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(bundle.kmkRecsVersion, decoded.kmkRecsVersion)
        assertEquals(bundle.title, decoded.title)
        assertEquals(bundle.bundleType, decoded.bundleType)
        assertEquals(bundle.createdAt, decoded.createdAt)
    }

    @Test
    fun `round trip preserves source metadata`() {
        val bundle = sampleBundle()
        val encoded = json.encodeToString(bundle)
        val decoded = json.decodeFromString<RecommendationBundle>(encoded)

        assertEquals(1, decoded.requiredSources.size)
        val source = decoded.requiredSources.first()
        assertEquals(12345L, source.sourceId)
        assertEquals("MangaDex", source.sourceName)
        assertEquals("en", source.sourceLang)
        assertEquals("eu.kanade.tachiyomi.extension.en.mangadex", source.extensionPkgName)
        assertEquals("abc123", source.extensionSignatureHash)
    }

    @Test
    fun `round trip preserves manga metadata`() {
        val bundle = sampleBundle()
        val encoded = json.encodeToString(bundle)
        val decoded = json.decodeFromString<RecommendationBundle>(encoded)

        assertEquals(1, decoded.items.size)
        val item = decoded.items.first()
        assertEquals("Test Manga", item.title)
        assertEquals("/manga/test-slug", item.url)
        assertEquals(12345L, item.sourceId)
        assertEquals("Author Name", item.author)
        assertEquals(listOf("Action", "Adventure"), item.genres)
        assertEquals(0.85, item.score)
        assertEquals(listOf("action", "isekai"), item.matchedGroups)
    }

    @Test
    fun `null optional fields survive round trip`() {
        val bundle = sampleBundle().copy(description = null, appVersionName = null)
        val encoded = json.encodeToString(bundle)
        val decoded = json.decodeFromString<RecommendationBundle>(encoded)

        assertNull(decoded.description)
        assertNull(decoded.appVersionName)
    }

    @Test
    fun `empty items list round trips`() {
        val bundle = sampleBundle().copy(items = emptyList(), requiredSources = emptyList())
        val encoded = json.encodeToString(bundle)
        val decoded = json.decodeFromString<RecommendationBundle>(encoded)

        assertTrue(decoded.items.isEmpty())
        assertTrue(decoded.requiredSources.isEmpty())
    }

    @Test
    fun `bundle type enum round trips`() {
        RecommendationBundleType.values().forEach { type ->
            val bundle = sampleBundle().copy(bundleType = type)
            val encoded = json.encodeToString(bundle)
            val decoded = json.decodeFromString<RecommendationBundle>(encoded)
            assertEquals(type, decoded.bundleType)
        }
    }

    @Test
    fun `unknown json fields are ignored on decode`() {
        val json2 = Json { ignoreUnknownKeys = true }
        val withExtra = """
            {
                "schema":"kmk.recommendation.bundle",
                "schemaVersion":1,
                "kmkRecsVersion":"v0.7.5",
                "createdAt":1000000,
                "title":"Test",
                "bundleType":"TOP_PICKS",
                "items":[],
                "unknownFutureField":"ignored"
            }
        """.trimIndent()
        val decoded = json2.decodeFromString<RecommendationBundle>(withExtra)
        assertNotNull(decoded)
        assertEquals("Test", decoded.title)
    }

    @Test
    fun `cross source group id is preserved`() {
        val item = RecommendationBundleItem(
            title = "Manga",
            url = "/url",
            sourceId = 1L,
            crossSourceGroupId = "group-abc-123",
        )
        val bundle = sampleBundle().copy(items = listOf(item))
        val encoded = json.encodeToString(bundle)
        val decoded = json.decodeFromString<RecommendationBundle>(encoded)

        assertEquals("group-abc-123", decoded.items.first().crossSourceGroupId)
    }
}

// KMK <--
