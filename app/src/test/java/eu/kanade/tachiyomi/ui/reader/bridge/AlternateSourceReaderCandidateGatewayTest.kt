package eu.kanade.tachiyomi.ui.reader.bridge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.AlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeEvidenceState
import tachiyomi.domain.taste.model.AlternateSourceBridgeMapping
import tachiyomi.domain.taste.model.AlternateSourceBridgePolicy
import tachiyomi.domain.taste.model.AlternateSourceBridgeReviewState
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.CrossSourceRecordKey

class AlternateSourceReaderCandidateGatewayTest {

    @Test
    fun `known bridge is first and duplicate confirmed and live records are removed`() = runTest {
        val reads = FakeCandidateReads().apply {
            bridges = listOf(bridge(ALTERNATE_KEY))
            confirmed = listOf(ALTERNATE_KEY, CONFIRMED_KEY)
            live = listOf(
                AlternateSourceReaderLiveCandidateRead.Candidate(manga(ALTERNATE_KEY, 22L, "Duplicate")),
                AlternateSourceReaderLiveCandidateRead.Candidate(manga(LIVE_KEY, 44L, "Primary")),
            )
        }

        val result = AlternateSourceReaderCandidateGateway(reads).discover(request(), includeLiveSearch = true)

        assertEquals(listOf(ALTERNATE_KEY, CONFIRMED_KEY, LIVE_KEY), result.candidates.map { it.manga.recordKey() })
        assertEquals(
            listOf(
                AlternateSourceReaderCandidateOrigin.CURRENT_BRIDGE,
                AlternateSourceReaderCandidateOrigin.CONFIRMED_PAIR,
                AlternateSourceReaderCandidateOrigin.LIVE_SEARCH,
            ),
            result.candidates.map { it.origin },
        )
        assertEquals(0, result.failedSourceCount)
        assertEquals(1, reads.mappingReadCount)
    }

    @Test
    fun `legacy group remains selectable but requires explicit pair confirmation`() = runTest {
        val reads = FakeCandidateReads().apply {
            links = listOf(
                link(PRIMARY_KEY, "group"),
                link(LEGACY_KEY, "group"),
            )
        }

        val candidate = AlternateSourceReaderCandidateGateway(reads)
            .discover(request(), includeLiveSearch = false)
            .candidates.single()

        assertEquals(AlternateSourceReaderCandidateOrigin.LEGACY_GROUP, candidate.origin)
        assertTrue(candidate.requiresPairConfirmation)
    }

    @Test
    fun `confirmed group does not require pair confirmation`() = runTest {
        val reads = FakeCandidateReads().apply {
            links = listOf(link(PRIMARY_KEY, "group"), link(CONFIRMED_KEY, "group"))
            confirmed = listOf(CONFIRMED_KEY)
        }

        val candidate = AlternateSourceReaderCandidateGateway(reads)
            .discover(request(), includeLiveSearch = false)
            .candidates.single()

        assertEquals(AlternateSourceReaderCandidateOrigin.CONFIRMED_PAIR, candidate.origin)
        assertFalse(candidate.requiresPairConfirmation)
    }

    @Test
    fun `live source failures preserve usable partial candidates`() = runTest {
        val reads = FakeCandidateReads().apply {
            live = listOf(
                AlternateSourceReaderLiveCandidateRead.SourceFailure,
                AlternateSourceReaderLiveCandidateRead.Candidate(manga(LIVE_KEY, 44L, "Primary")),
                AlternateSourceReaderLiveCandidateRead.SourceFailure,
            )
        }

        val result = AlternateSourceReaderCandidateGateway(reads).discover(request(), includeLiveSearch = true)

        assertEquals(1, result.candidates.size)
        assertEquals(2, result.failedSourceCount)
    }

    @Test
    fun `unrelated live search rows are withheld while search fallback remains available`() = runTest {
        val reads = FakeCandidateReads().apply {
            live = listOf(
                AlternateSourceReaderLiveCandidateRead.Candidate(manga(LIVE_KEY, 44L, "Cairo")),
                AlternateSourceReaderLiveCandidateRead.Candidate(manga(CONFIRMED_KEY, 33L, "Primary")),
            )
        }

        val result = AlternateSourceReaderCandidateGateway(reads).discover(request(), includeLiveSearch = true)

        assertEquals(listOf(CONFIRMED_KEY), result.candidates.map { it.manga.recordKey() })
    }

    @Test
    fun `invalid route context fails closed without reading candidate stores`() = runTest {
        val reads = FakeCandidateReads().apply { context = null }

        val result = AlternateSourceReaderCandidateGateway(reads).discover(request(), includeLiveSearch = true)

        assertTrue(result.candidates.isEmpty())
        assertEquals(0, reads.bridgeReadCount)
        assertEquals(0, reads.mappingReadCount)
        assertEquals(0, reads.liveReadCount)
    }

    @Test
    fun `missing extension remains a candidate with no source label`() = runTest {
        val reads = FakeCandidateReads().apply {
            confirmed = listOf(CONFIRMED_KEY)
            sourceLabels = emptyMap()
        }

        val candidate = AlternateSourceReaderCandidateGateway(reads)
            .discover(request(), includeLiveSearch = false)
            .candidates.single()

        assertEquals(null, candidate.sourceLabel)
    }

    @Test
    fun `cancellation and fatal errors propagate unchanged`() {
        val cancellation = FakeCandidateReads().apply { bridgeFailure = CancellationException("cancel") }
        assertThrows(CancellationException::class.java) {
            runTest { AlternateSourceReaderCandidateGateway(cancellation).discover(request(), false) }
        }

        val fatal = FakeCandidateReads().apply { bridgeFailure = OutOfMemoryError("fatal") }
        assertThrows(OutOfMemoryError::class.java) {
            runTest { AlternateSourceReaderCandidateGateway(fatal).discover(request(), false) }
        }
    }

    @Test
    fun `chapter discovery is delegated without candidate preselection`() = runTest {
        val expected = AlternateSourceReaderChapterDiscovery.Available(
            listOf(AlternateSourceReaderChapterCandidate("/c/9", "Chapter 9", 9f)),
        )
        val reads = FakeCandidateReads().apply {
            confirmed = listOf(CONFIRMED_KEY)
            chapterResult = expected
        }
        val gateway = AlternateSourceReaderCandidateGateway(reads)
        val candidate = gateway.discover(request(), false).candidates.single()

        assertEquals(expected, gateway.chapters(candidate))
    }

    @Test
    fun `selected global search manga becomes an explicitly selectable candidate`() = runTest {
        val reads = FakeCandidateReads().apply { confirmed = listOf(CONFIRMED_KEY) }
        val candidate = AlternateSourceReaderCandidateGateway(reads)
            .candidateFromSelectedManga(request(), manga(CONFIRMED_KEY, 33L, "Confirmed"))

        assertEquals(AlternateSourceReaderCandidateOrigin.LIVE_SEARCH, candidate?.origin)
        assertFalse(candidate?.requiresPairConfirmation ?: true)
        assertEquals("Three", candidate?.sourceLabel)
    }

    private class FakeCandidateReads : AlternateSourceReaderCandidateReads {
        var context: AlternateSourceReaderCandidateContext? = AlternateSourceReaderCandidateContext(
            origin = manga(PRIMARY_KEY, 11L, "Primary"),
            preceding = chapter(101L, 11L, "/c/1"),
            following = chapter(102L, 11L, "/c/3"),
        )
        var bridges: List<AlternateSourceBridge> = emptyList()
        var mappings: List<AlternateSourceBridgeMapping> = emptyList()
        var links: List<CrossSourceMangaLink> = emptyList()
        var confirmed: List<CrossSourceRecordKey> = emptyList()
        var live: List<AlternateSourceReaderLiveCandidateRead> = emptyList()
        var sourceLabels: Map<Long, String> = mapOf(2L to "Two", 3L to "Three", 4L to "Four", 5L to "Five")
        var chapterResult: AlternateSourceReaderChapterDiscovery = AlternateSourceReaderChapterDiscovery.Empty
        var bridgeFailure: Throwable? = null
        var bridgeReadCount = 0
        var mappingReadCount = 0
        var liveReadCount = 0

        override suspend fun resolveContext(request: AlternateSourceReaderCandidateRequest) = context

        override suspend fun bridges(): List<AlternateSourceBridge> {
            bridgeReadCount++
            bridgeFailure?.let { throw it }
            return bridges
        }

        override suspend fun mappings(): List<AlternateSourceBridgeMapping> {
            mappingReadCount++
            return mappings
        }

        override suspend fun links() = links
        override suspend fun confirmedRecords(origin: CrossSourceRecordKey) = confirmed
        override suspend fun manga(record: CrossSourceRecordKey): Manga? = when (record) {
            ALTERNATE_KEY -> manga(record, 22L, "Alternate")
            CONFIRMED_KEY -> manga(record, 33L, "Confirmed")
            LEGACY_KEY -> manga(record, 55L, "Legacy")
            LIVE_KEY -> manga(record, 44L, "Live")
            else -> null
        }
        override suspend fun isConfirmed(origin: CrossSourceRecordKey, candidate: CrossSourceRecordKey) =
            candidate in confirmed
        override fun sourceLabel(sourceId: Long) = sourceLabels[sourceId]
        override suspend fun liveSearch(origin: Manga): List<AlternateSourceReaderLiveCandidateRead> {
            liveReadCount++
            return live
        }
        override suspend fun chapters(manga: Manga) = chapterResult
    }

    private companion object {
        val PRIMARY_KEY = CrossSourceRecordKey(1L, "/m/primary")
        val ALTERNATE_KEY = CrossSourceRecordKey(2L, "/m/alternate")
        val CONFIRMED_KEY = CrossSourceRecordKey(3L, "/m/confirmed")
        val LIVE_KEY = CrossSourceRecordKey(4L, "/m/live")
        val LEGACY_KEY = CrossSourceRecordKey(5L, "/m/legacy")

        fun request() = AlternateSourceReaderCandidateRequest(
            primaryRoute = AlternateSourceReaderRoute(
                role = AlternateSourceReaderRouteRole.PRIMARY,
                record = PRIMARY_KEY,
                mangaId = 11L,
                chapterUrl = "/c/1",
                chapterId = 101L,
                pageIndex = 0,
            ),
            precedingPrimaryChapterId = 101L,
            followingPrimaryChapterId = 102L,
        )

        fun manga(key: CrossSourceRecordKey, id: Long, title: String) = Manga.create().copy(
            id = id,
            source = key.source,
            url = key.url,
            ogTitle = title,
        )

        fun chapter(id: Long, mangaId: Long, url: String) = Chapter.create().copy(
            id = id,
            mangaId = mangaId,
            url = url,
            name = url,
        )

        fun bridge(alternate: CrossSourceRecordKey) = AlternateSourceBridge(
            key = tachiyomi.domain.taste.model.AlternateSourceBridgeKey(PRIMARY_KEY, alternate),
            version = AlternateSourceBridgePolicy.CURRENT_VERSION,
            offsetMilli = 0,
            offsetState = AlternateSourceBridgeEvidenceState.CONFIRMED,
            reviewState = AlternateSourceBridgeReviewState.CURRENT,
            createdAt = 1_000L,
            updatedAt = 2_000L,
        )

        fun link(key: CrossSourceRecordKey, group: String) = CrossSourceMangaLink(
            source = key.source,
            url = key.url,
            groupId = group,
            title = key.url,
            createdAt = 1_000L,
            updatedAt = 1_000L,
        )

        fun Manga.recordKey() = CrossSourceRecordKey(source, url)
    }
}
