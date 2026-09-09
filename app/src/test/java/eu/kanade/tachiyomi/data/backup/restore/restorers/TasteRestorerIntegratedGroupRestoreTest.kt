package eu.kanade.tachiyomi.data.backup.restore.restorers

// KMK HR-2026-08-26-BACKUP-RATING-GROUP-INTEGRITY -->
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceGroupPrimary
import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceIdentityDecision
import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceMangaLink
import eu.kanade.tachiyomi.data.backup.models.BackupMangaTaste
import exh.taste.StubMangaRepository
import exh.util.FakeTasteRepository
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.UpsertCrossSourceMangaLinks
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.repository.AlternateSourceBridgeRepository

/**
 * Integrated create-decode/decode-restore relationship round trip for the
 * HR-2026-08-26-BACKUP-RATING-GROUP-INTEGRITY gate (section G of the 2026-08-26 recovery intake).
 * [TasteRestorerRatedMangaIdentityTest] already proves a single rating restores onto a minimal
 * created identity in isolation; this file proves the FULL relationship graph (rating, cross-source
 * link, group primary, identity decision) resolves together for alternates that are not library
 * favorites, plus the adversarial precedence/partial-failure cases the frozen contract's "Required
 * validation" section requires. All assertions run against the real [TasteRestorer] restore
 * functions in [BackupRestorer]'s own call order (manga tastes, then links, then primaries, then
 * identity decisions), never a copied expression.
 */
class TasteRestorerIntegratedGroupRestoreTest {

    private class RecordingMangaRepository : StubMangaRepository() {
        private val mangas = linkedMapOf<Pair<Long, String>, Manga>()
        var nextId = 1_000L
        var failOnUrl: String? = null

        override suspend fun getMangaById(id: Long): Manga =
            mangas.values.first { it.id == id }

        override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga? =
            mangas[sourceId to url]

        override suspend fun insertNetworkManga(manga: List<Manga>, updateInfo: Boolean): List<Manga> {
            manga.firstOrNull { it.url == failOnUrl }?.let {
                if (it.url == "cancel-me") throw CancellationException("cancelled mid-restore")
                throw RuntimeException("simulated insert failure for ${it.url}")
            }
            return manga.map { incoming ->
                mangas.getOrPut(incoming.source to incoming.url) {
                    incoming.copy(id = nextId++)
                }
            }
        }

        fun all() = mangas.values.toList()
    }

    private fun restorer(
        tasteRepository: FakeTasteRepository,
        mangaRepository: RecordingMangaRepository,
        sourcePreferences: SourcePreferences = mockk(relaxed = true),
    ) = TasteRestorer(
        tasteRepository = tasteRepository,
        alternateSourceBridgeRepository = mockk<AlternateSourceBridgeRepository>(relaxed = true),
        mangaRepository = mangaRepository,
        getManga = GetManga(mangaRepository),
        getMangaTaste = GetMangaTaste(tasteRepository),
        getTagTaste = mockk(relaxed = true),
        getTagAliases = mockk(relaxed = true),
        getDisabledSources = mockk(relaxed = true),
        setSourceEnabled = mockk(relaxed = true),
        upsertTagAlias = mockk(relaxed = true),
        getCrossSourceMangaLinks = GetCrossSourceMangaLinks(tasteRepository),
        upsertCrossSourceMangaLinks = UpsertCrossSourceMangaLinks(tasteRepository),
        getCrossSourceGroupPrimary = GetCrossSourceGroupPrimary(tasteRepository),
        getMangaSourceQualitySignals = mockk(relaxed = true),
        upsertMangaSourceQualitySignal = mockk(relaxed = true),
        sourcePreferences = sourcePreferences,
    )

    // --- integrated round trip: two confirmed groups, alternates not library favorites ---

    @Test
    fun `two confirmed cross-source groups restore their ratings, links, primaries, and identity decisions together`() = runTest {
        val tastes = FakeTasteRepository()
        val mangas = RecordingMangaRepository()
        val restorerUnderTest = restorer(tastes, mangas)

        // Group "isekai-classic": origin (already local) LOVEd, alternate (not local) also LOVEd.
        // Group "shounen-arc": origin (not local) LIKEd, alternate (not local) DISLIKEd.
        val backupTastes = listOf(
            BackupMangaTaste(source = 1L, url = "/isekai/origin", title = "Isekai Origin", rating = MangaRating.LOVE.value, updatedAt = 1_000L),
            BackupMangaTaste(source = 2L, url = "/isekai/alt", title = "Isekai Alt", rating = MangaRating.LOVE.value, updatedAt = 1_000L),
            BackupMangaTaste(source = 3L, url = "/shounen/origin", title = "Shounen Origin", rating = MangaRating.LIKE.value, updatedAt = 1_000L),
            BackupMangaTaste(source = 4L, url = "/shounen/alt", title = "Shounen Alt", rating = MangaRating.DISLIKE.value, updatedAt = 1_000L),
        )
        val backupLinks = listOf(
            BackupCrossSourceMangaLink(source = 1L, url = "/isekai/origin", groupId = "isekai-classic", title = "Isekai Origin", updatedAt = 1_000L),
            BackupCrossSourceMangaLink(source = 2L, url = "/isekai/alt", groupId = "isekai-classic", title = "Isekai Alt", updatedAt = 1_000L),
            BackupCrossSourceMangaLink(source = 3L, url = "/shounen/origin", groupId = "shounen-arc", title = "Shounen Origin", updatedAt = 1_000L),
            BackupCrossSourceMangaLink(source = 4L, url = "/shounen/alt", groupId = "shounen-arc", title = "Shounen Alt", updatedAt = 1_000L),
        )
        val backupPrimaries = listOf(
            BackupCrossSourceGroupPrimary(groupId = "isekai-classic", source = 1L, url = "/isekai/origin", updatedAt = 1_000L),
            BackupCrossSourceGroupPrimary(groupId = "shounen-arc", source = 4L, url = "/shounen/alt", updatedAt = 1_000L),
        )
        val backupDecisions = listOf(
            BackupCrossSourceIdentityDecision(
                leftSource = 1L, leftUrl = "/isekai/origin",
                rightSource = 2L, rightUrl = "/isekai/alt",
                decision = "USER_CONFIRMED", decisionVersion = 1, evidenceVersion = 1,
                reasonCodes = listOf("USER_CONFIRMATION"),
                reviewState = "CURRENT", createdAt = 1_000L, updatedAt = 1_000L,
            ),
        )

        // Exact BackupRestorer call order: manga tastes, then links, then primaries, then decisions.
        val tasteErrors = restorerUnderTest.restoreMangaTastes(backupTastes)
        val linkErrors = restorerUnderTest.restoreCrossSourceMangaLinks(backupLinks)
        val primaryErrors = restorerUnderTest.restoreCrossSourceGroupPrimaries(backupPrimaries)
        val decisionErrors = restorerUnderTest.restoreCrossSourceIdentityDecisions(backupDecisions)

        assertEquals(emptyList<String>(), tasteErrors, "no rated member should be skipped")
        assertEquals(emptyList<String>(), linkErrors)
        assertEquals(emptyList<String>(), primaryErrors)
        assertEquals(emptyList<String>(), decisionErrors)

        // All four minimal identities were created, non-favorite and uninitialized.
        assertEquals(4, mangas.all().size)
        mangas.all().forEach {
            assertTrue(!it.favorite, "restored minimal identity must not be a library favorite")
            assertTrue(!it.initialized, "restored minimal identity must not be marked initialized")
        }

        // All four ratings are queryable, keyed to the newly-created local manga ids.
        val isekaiOrigin = mangas.all().single { it.url == "/isekai/origin" }
        val isekaiAlt = mangas.all().single { it.url == "/isekai/alt" }
        val shounenOrigin = mangas.all().single { it.url == "/shounen/origin" }
        val shounenAlt = mangas.all().single { it.url == "/shounen/alt" }
        assertEquals(MangaRating.LOVE.value, tastes.getMangaTaste(isekaiOrigin.id)?.rating)
        assertEquals(MangaRating.LOVE.value, tastes.getMangaTaste(isekaiAlt.id)?.rating)
        assertEquals(MangaRating.LIKE.value, tastes.getMangaTaste(shounenOrigin.id)?.rating)
        assertEquals(MangaRating.DISLIKE.value, tastes.getMangaTaste(shounenAlt.id)?.rating)

        // Both groups' link membership is visible -- the actual "grouping" the invariant cares about.
        val isekaiLinks = tastes.getCrossSourceMangaLinksByGroupId("isekai-classic")
        assertEquals(setOf("/isekai/origin", "/isekai/alt"), isekaiLinks.map { it.url }.toSet())
        val shounenLinks = tastes.getCrossSourceMangaLinksByGroupId("shounen-arc")
        assertEquals(setOf("/shounen/origin", "/shounen/alt"), shounenLinks.map { it.url }.toSet())

        // Both groups' primaries resolve to the confirmed choice.
        assertEquals(1L to "/isekai/origin", tastes.getCrossSourceGroupPrimary("isekai-classic")?.let { it.source to it.url })
        assertEquals(4L to "/shounen/alt", tastes.getCrossSourceGroupPrimary("shounen-arc")?.let { it.source to it.url })

        // The identity decision survives too.
        val decisions = tastes.getAllCrossSourceIdentityDecisions()
        assertEquals(1, decisions.size)
        assertEquals(tachiyomi.domain.taste.model.CrossSourceIdentityDecisionValue.USER_CONFIRMED, decisions.single().decision)
    }

    // --- precedence: an existing, newer local rating must not be overwritten by an older backup ---

    @Test
    fun `an existing newer local rating is never overwritten by an older backup value`() = runTest {
        val tastes = FakeTasteRepository()
        val mangas = RecordingMangaRepository()
        val restorerUnderTest = restorer(tastes, mangas)

        // Origin already exists locally (e.g. a previously-restored/minimal row) with a NEWER Love
        // rating. Deliberately not `favorite = true` here: Manga's private customMangaInfo init
        // block calls the Injekt-backed GetCustomMangaInfo only when favorite is true, which this
        // unit test's Injekt context does not register -- favorite status is irrelevant to the
        // precedence rule under test (existing-newer-wins), so this stays false like every other
        // minimal identity in this file.
        val origin = mangas.insertNetworkManga(
            listOf(Manga.create().copy(source = 5L, url = "/existing", ogTitle = "Existing", favorite = false)),
            updateInfo = false,
        ).single()
        tastes.upsertMangaTaste(
            tachiyomi.domain.taste.model.MangaTaste(
                mangaId = origin.id,
                source = 5L,
                url = "/existing",
                title = "Existing",
                rating = MangaRating.LOVE.value,
                createdAt = 500L,
                updatedAt = 5_000L,
            ),
        )

        // Backup carries an OLDER Dislike for the same manga.
        val errors = restorerUnderTest.restoreMangaTastes(
            listOf(BackupMangaTaste(source = 5L, url = "/existing", title = "Existing", rating = MangaRating.DISLIKE.value, updatedAt = 1_000L)),
        )

        assertEquals(emptyList<String>(), errors, "an older backup value is a no-op, not a failure")
        assertEquals(MangaRating.LOVE.value, tastes.getMangaTaste(origin.id)?.rating, "the newer local rating must survive")
    }

    // --- duplicate identity within the same restore batch ---

    @Test
    fun `duplicate identity rows in the same batch converge on the newest timestamp`() = runTest {
        val tastes = FakeTasteRepository()
        val mangas = RecordingMangaRepository()
        val restorerUnderTest = restorer(tastes, mangas)

        val errors = restorerUnderTest.restoreMangaTastes(
            listOf(
                BackupMangaTaste(source = 6L, url = "/dup", title = "Duplicate", rating = MangaRating.LIKE.value, updatedAt = 1_000L),
                BackupMangaTaste(source = 6L, url = "/dup", title = "Duplicate", rating = MangaRating.LOVE.value, updatedAt = 2_000L),
            ),
        )

        assertEquals(emptyList<String>(), errors)
        assertEquals(1, mangas.all().size, "a duplicate identity must not create two manga rows")
        val manga = mangas.all().single()
        assertEquals(MangaRating.LOVE.value, tastes.getMangaTaste(manga.id)?.rating, "the newer-timestamped duplicate must win")
    }

    // --- partial write failure: one entry fails, the rest of the batch still restores ---

    @Test
    fun `a single failing insert is reported and isolated -- the rest of the batch still restores`() = runTest {
        val tastes = FakeTasteRepository()
        val mangas = RecordingMangaRepository().apply { failOnUrl = "/broken" }
        val restorerUnderTest = restorer(tastes, mangas)

        val errors = restorerUnderTest.restoreMangaTastes(
            listOf(
                BackupMangaTaste(source = 7L, url = "/broken", title = "Broken", rating = MangaRating.LOVE.value, updatedAt = 1_000L),
                BackupMangaTaste(source = 8L, url = "/fine", title = "Fine", rating = MangaRating.LIKE.value, updatedAt = 1_000L),
            ),
        )

        assertEquals(1, errors.size, "exactly the failing entry must be reported")
        assertTrue(errors.single().contains("Broken"), "the error must identify the failing entry")
        assertEquals(listOf("/fine"), mangas.all().map { it.url }, "the unrelated entry must still restore")
        assertEquals(1, tastes.getAllMangaTastes().size)
    }

    // --- cancellation must propagate, never be swallowed as a reported error ---

    @Test
    fun `cancellation during a batch propagates instead of being reported as a restore error`() = runTest {
        val tastes = FakeTasteRepository()
        val mangas = RecordingMangaRepository().apply { failOnUrl = "cancel-me" }
        val restorerUnderTest = restorer(tastes, mangas)

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                restorerUnderTest.restoreMangaTastes(
                    listOf(BackupMangaTaste(source = 9L, url = "cancel-me", title = "Cancel", rating = MangaRating.LOVE.value, updatedAt = 1_000L)),
                )
            }
        }
    }

    // --- missing primary: links still restore; querying the primary is a graceful null, not an error ---

    @Test
    fun `a group with links but no backed-up primary still restores its links, and the primary query is a graceful null`() = runTest {
        val tastes = FakeTasteRepository()
        val mangas = RecordingMangaRepository()
        val restorerUnderTest = restorer(tastes, mangas)

        restorerUnderTest.restoreMangaTastes(
            listOf(
                BackupMangaTaste(source = 10L, url = "/no-primary/a", title = "A", rating = MangaRating.LIKE.value, updatedAt = 1_000L),
                BackupMangaTaste(source = 11L, url = "/no-primary/b", title = "B", rating = MangaRating.LIKE.value, updatedAt = 1_000L),
            ),
        )
        val linkErrors = restorerUnderTest.restoreCrossSourceMangaLinks(
            listOf(
                BackupCrossSourceMangaLink(source = 10L, url = "/no-primary/a", groupId = "no-primary-group", title = "A", updatedAt = 1_000L),
                BackupCrossSourceMangaLink(source = 11L, url = "/no-primary/b", groupId = "no-primary-group", title = "B", updatedAt = 1_000L),
            ),
        )
        val primaryErrors = restorerUnderTest.restoreCrossSourceGroupPrimaries(emptyList())

        assertEquals(emptyList<String>(), linkErrors)
        assertEquals(emptyList<String>(), primaryErrors)
        assertEquals(2, tastes.getCrossSourceMangaLinksByGroupId("no-primary-group").size)
        assertNull(tastes.getCrossSourceGroupPrimary("no-primary-group"), "no primary backed up -- must resolve to null, not throw or error")
    }

    // --- malformed identity decision is skipped without corrupting other state ---

    @Test
    fun `a malformed identity decision is skipped with a bounded error and does not corrupt other decisions`() = runTest {
        val tastes = FakeTasteRepository()
        val mangas = RecordingMangaRepository()
        val restorerUnderTest = restorer(tastes, mangas)

        val errors = restorerUnderTest.restoreCrossSourceIdentityDecisions(
            listOf(
                // Blank left/right identity -- cannot safely resolve to any pair.
                BackupCrossSourceIdentityDecision(
                    leftSource = 0L, leftUrl = "", rightSource = 0L, rightUrl = "",
                    decision = "USER_CONFIRMED", decisionVersion = 1, evidenceVersion = 1,
                    reasonCodes = listOf("USER_CONFIRMATION"),
                    reviewState = "CURRENT", createdAt = 1_000L, updatedAt = 1_000L,
                ),
                BackupCrossSourceIdentityDecision(
                    leftSource = 12L, leftUrl = "/valid/a",
                    rightSource = 13L, rightUrl = "/valid/b",
                    decision = "USER_CONFIRMED", decisionVersion = 1, evidenceVersion = 1,
                    reasonCodes = listOf("USER_CONFIRMATION"),
                    reviewState = "CURRENT", createdAt = 1_000L, updatedAt = 1_000L,
                ),
            ),
        )

        assertEquals(1, errors.size, "exactly the malformed row must be reported")
        assertEquals(1, tastes.getAllCrossSourceIdentityDecisions().size, "the valid decision must still restore")
    }
}
// KMK <--
