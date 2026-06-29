package eu.kanade.tachiyomi.data.backup

// KMK -->
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceMangaLink
import eu.kanade.tachiyomi.data.backup.models.BackupDisabledRecommendationSource
import eu.kanade.tachiyomi.data.backup.models.BackupMangaSourceQualitySignal
import eu.kanade.tachiyomi.data.backup.models.BackupMangaTaste
import eu.kanade.tachiyomi.data.backup.models.BackupTagAlias
import eu.kanade.tachiyomi.data.backup.models.BackupTagTaste
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TasteBackupRoundTripTest {

    private val parser = ProtoBuf

    private val mangaTastes = listOf(
        BackupMangaTaste(
            mangaId = 42L,
            source = 123456789L,
            url = "/manga/some-title",
            title = "Some Title",
            rating = 2,
            updatedAt = 1717977600000L,
        ),
        BackupMangaTaste(
            mangaId = 7L,
            source = 987654321L,
            url = "/manga/another",
            title = "Another",
            rating = -1,
            updatedAt = 1717977700000L,
        ),
    )

    private val tagTastes = listOf(
        BackupTagTaste(displayName = "Romance", preference = 1, updatedAt = 1717977600000L),
        BackupTagTaste(displayName = "Gore", preference = -2, updatedAt = 1717977700000L),
    )

    private val tagAliases = listOf(
        BackupTagAlias(alias = "Yuri", groupKey = "girls love", displayName = "Girls' Love"),
    )

    private val disabledSources = listOf(
        BackupDisabledRecommendationSource(sourceId = 555L),
    )

    @Test
    fun `taste fields survive encode and decode`() {
        val backup = Backup(
            backupManga = emptyList(),
            backupMangaTastes = mangaTastes,
            backupTagTastes = tagTastes,
            backupTagAliases = tagAliases,
            backupDisabledRecommendationSources = disabledSources,
        )

        val bytes = parser.encodeToByteArray(Backup.serializer(), backup)
        val decoded = parser.decodeFromByteArray(Backup.serializer(), bytes)

        assertEquals(mangaTastes, decoded.backupMangaTastes)
        assertEquals(tagTastes, decoded.backupTagTastes)
        assertEquals(tagAliases, decoded.backupTagAliases)
        assertEquals(disabledSources, decoded.backupDisabledRecommendationSources)
    }

    @Test
    fun `backup without taste fields decodes to empty lists`() {
        // Simulates restoring a backup created before Phase 7
        val oldBackup = Backup(backupManga = emptyList())

        val bytes = parser.encodeToByteArray(Backup.serializer(), oldBackup)
        val decoded = parser.decodeFromByteArray(Backup.serializer(), bytes)

        assertTrue(decoded.backupMangaTastes.isEmpty())
        assertTrue(decoded.backupTagTastes.isEmpty())
        assertTrue(decoded.backupTagAliases.isEmpty())
        assertTrue(decoded.backupDisabledRecommendationSources.isEmpty())
    }

    @Test
    fun `negative ratings and preferences round-trip correctly`() {
        // Proto varint encoding of negative ints is a classic silent-corruption spot
        val backup = Backup(
            backupManga = emptyList(),
            backupMangaTastes = listOf(
                BackupMangaTaste(mangaId = 1L, rating = -1, updatedAt = 1L),
            ),
            backupTagTastes = listOf(
                BackupTagTaste(displayName = "x", preference = -2, updatedAt = 1L),
                BackupTagTaste(displayName = "y", preference = -1, updatedAt = 1L),
            ),
        )

        val bytes = parser.encodeToByteArray(Backup.serializer(), backup)
        val decoded = parser.decodeFromByteArray(Backup.serializer(), bytes)

        assertEquals(-1, decoded.backupMangaTastes.single().rating)
        assertEquals(listOf(-2, -1), decoded.backupTagTastes.map { it.preference })
    }

    @Test
    fun `taste fields do not collide with feed field`() {
        // Fields 610 (feeds) and 620-623 (taste) must coexist
        val backup = Backup(
            backupManga = emptyList(),
            backupMangaTastes = mangaTastes,
            backupTagTastes = tagTastes,
        )

        val bytes = parser.encodeToByteArray(Backup.serializer(), backup)
        val decoded = parser.decodeFromByteArray(Backup.serializer(), bytes)

        assertTrue(decoded.backupFeeds.isEmpty())
        assertEquals(mangaTastes, decoded.backupMangaTastes)
        assertEquals(tagTastes, decoded.backupTagTastes)
    }

    // KMK --> R-011 v0.7.16: proto collision guard tests for fields 624-625

    private val crossSourceLinks = listOf(
        BackupCrossSourceMangaLink(
            source = 111L,
            url = "/manga/link-a",
            groupId = "group-xyz",
            title = "Link Title A",
            updatedAt = 1717977600000L,
        ),
        BackupCrossSourceMangaLink(
            source = 222L,
            url = "/manga/link-b",
            groupId = "group-xyz",
            title = "Link Title B",
            updatedAt = 1717977700000L,
        ),
    )

    private val qualitySignals = listOf(
        BackupMangaSourceQualitySignal(
            originSourceId = 100L,
            originUrl = "/manga/origin",
            originTitle = "Origin Title",
            selectedSourceId = 200L,
            selectedUrl = "/manga/selected",
            selectedTitle = "Selected Title",
            selectedSourceName = "Best Source",
            chapterNumber = 5.5,
            chapterName = "Chapter 5.5",
            selectedAt = 1717977600000L,
            qualitySignalVersion = 1,
        ),
    )

    @Test
    fun `cross-source manga links (proto 624) survive encode and decode`() {
        val backup = Backup(
            backupManga = emptyList(),
            backupCrossSourceMangaLinks = crossSourceLinks,
        )

        val bytes = parser.encodeToByteArray(Backup.serializer(), backup)
        val decoded = parser.decodeFromByteArray(Backup.serializer(), bytes)

        assertEquals(crossSourceLinks, decoded.backupCrossSourceMangaLinks)
    }

    @Test
    fun `manga source quality signals (proto 625) survive encode and decode`() {
        val backup = Backup(
            backupManga = emptyList(),
            backupMangaSourceQualitySignals = qualitySignals,
        )

        val bytes = parser.encodeToByteArray(Backup.serializer(), backup)
        val decoded = parser.decodeFromByteArray(Backup.serializer(), bytes)

        assertEquals(qualitySignals, decoded.backupMangaSourceQualitySignals)
    }

    @Test
    fun `all KMK proto fields 620-625 coexist without corruption`() {
        // Encodes all 6 KMK proto fields simultaneously; verifies none silently corrupt another
        val backup = Backup(
            backupManga = emptyList(),
            backupMangaTastes = mangaTastes,
            backupTagTastes = tagTastes,
            backupTagAliases = tagAliases,
            backupDisabledRecommendationSources = disabledSources,
            backupCrossSourceMangaLinks = crossSourceLinks,
            backupMangaSourceQualitySignals = qualitySignals,
        )

        val bytes = parser.encodeToByteArray(Backup.serializer(), backup)
        val decoded = parser.decodeFromByteArray(Backup.serializer(), bytes)

        assertEquals(mangaTastes, decoded.backupMangaTastes)
        assertEquals(tagTastes, decoded.backupTagTastes)
        assertEquals(tagAliases, decoded.backupTagAliases)
        assertEquals(disabledSources, decoded.backupDisabledRecommendationSources)
        assertEquals(crossSourceLinks, decoded.backupCrossSourceMangaLinks)
        assertEquals(qualitySignals, decoded.backupMangaSourceQualitySignals)
        // Adjacent upstream field must remain empty
        assertTrue(decoded.backupFeeds.isEmpty())
    }

    @Test
    fun `backup without proto 624-625 fields decodes to empty lists (forward compat)`() {
        // Simulates restoring a v0.7.0 backup (has 620-623, lacks 624-625)
        val oldBackup = Backup(
            backupManga = emptyList(),
            backupMangaTastes = mangaTastes,
            backupTagTastes = tagTastes,
        )

        val bytes = parser.encodeToByteArray(Backup.serializer(), oldBackup)
        val decoded = parser.decodeFromByteArray(Backup.serializer(), bytes)

        assertTrue(decoded.backupCrossSourceMangaLinks.isEmpty())
        assertTrue(decoded.backupMangaSourceQualitySignals.isEmpty())
        assertEquals(mangaTastes, decoded.backupMangaTastes)
    }

    @Test
    fun `quality signal chapter number zero sentinel decodes correctly`() {
        // chapterNumber = 0.0 is the null sentinel in the backup model
        val signalWithNullChapter = BackupMangaSourceQualitySignal(
            originSourceId = 1L,
            originUrl = "/manga/x",
            originTitle = "X",
            selectedSourceId = 2L,
            selectedUrl = "/manga/y",
            selectedTitle = "Y",
            selectedSourceName = "Src",
            chapterNumber = 0.0,
            chapterName = "",
            selectedAt = 1000L,
            qualitySignalVersion = 1,
        )
        val backup = Backup(
            backupManga = emptyList(),
            backupMangaSourceQualitySignals = listOf(signalWithNullChapter),
        )

        val bytes = parser.encodeToByteArray(Backup.serializer(), backup)
        val decoded = parser.decodeFromByteArray(Backup.serializer(), bytes)

        assertEquals(0.0, decoded.backupMangaSourceQualitySignals.single().chapterNumber)
    }

    // KMK <--
}
// KMK <--
