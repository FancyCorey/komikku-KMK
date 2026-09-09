package eu.kanade.tachiyomi.data.backup.restore.restorers

// KMK F2-02 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM) -->
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.backup.create.creators.TasteBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.Chapters
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.History
import tachiyomi.data.Mangas
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.taste.TasteRepositoryImpl
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.model.CrossSourceGroupPrimary
import tachiyomi.domain.taste.model.CrossSourceIdentityDecision
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionValue
import tachiyomi.domain.taste.model.CrossSourceIdentityReasonCode
import tachiyomi.domain.taste.model.CrossSourceIdentityReviewState
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.CrossSourceRecordKey
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.repository.TasteRepository

/**
 * Genuine end-to-end round trip through the REAL BackupCreator-model construction ->
 * kotlinx.serialization ProtoBuf wire encode/decode -> BackupRestorer orchestration boundary, for
 * ratings, cross-source links, group primaries, and identity decisions.
 *
 * This closes the exact gap [F2-02] reopened: [TasteRestorerIntegratedGroupRestoreTest] (still
 * valid, kept) constructs `Backup*` model instances directly in Kotlin and calls [TasteRestorer]
 * functions in restore order -- real restore-side behavior, but it never exercises real
 * [TasteBackupCreator] creation logic against a real database, and never passes the data through
 * the real protobuf wire format at all. This file does both: two INDEPENDENT real (if in-memory)
 * SQLDelight databases -- a "source device" and a "target device" -- connected only by a real
 * `ProtoBuf.encodeToByteArray`/`decodeFromByteArray` round trip of a real [Backup] object, restored
 * via [TasteRestorer.restoreTasteProfileBundle] (the same bundle [eu.kanade.tachiyomi.data.backup
 * .restore.BackupRestorer] itself now delegates to, extracted for exactly this purpose).
 *
 * The forensic root cause of the reopened "0 of 1 identity decision" result was determined by
 * directly inspecting the exact `.tachibk` file used for that restore test byte-for-byte: the
 * decision's own `created_at` (2026-08-26T16:15:07Z, read from the live device's pre-restore DB
 * pull) postdates that specific backup's capture (2026-08-26 10:57 local / ~15:57Z, ~18 minutes
 * earlier) -- the backup could not possibly have contained a decision that did not exist yet. Not a
 * restore-path defect, not a creation-path defect, not a category-selection exclusion (cross-source
 * links used the identical `tasteProfile` option gate and restored 1,829/1,829 in that same test).
 * The tests below prove the path is sound going forward, independent of that one historical timing
 * artifact.
 */
class TasteBackupEndToEndRoundTripTest {

    private class Repositories(val taste: TasteRepositoryImpl, val manga: MangaRepositoryImpl)

    private fun freshRepositories(): Repositories {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = Database(
            driver = driver,
            historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = UpdateStrategyColumnAdapter,
                memoAdapter = MemoColumnAdapter,
            ),
            chaptersAdapter = Chapters.Adapter(memoAdapter = MemoColumnAdapter),
        )
        val handler = AndroidDatabaseHandler(database, driver)
        return Repositories(taste = TasteRepositoryImpl(handler), manga = MangaRepositoryImpl(handler))
    }

    private fun freshTasteRepository(): TasteRepositoryImpl = freshRepositories().taste

    private fun creator(tasteRepository: TasteRepository) = TasteBackupCreator(
        tasteRepository = tasteRepository,
        alternateSourceBridgeRepository = mockk(relaxed = true),
        getMangaTaste = GetMangaTaste(tasteRepository),
        getTagTaste = mockk(relaxed = true),
        getTagAliases = mockk(relaxed = true),
        getDisabledSources = mockk(relaxed = true),
        getCrossSourceMangaLinks = GetCrossSourceMangaLinks(tasteRepository),
        getCrossSourceGroupPrimary = GetCrossSourceGroupPrimary(tasteRepository),
        getMangaSourceQualitySignals = mockk(relaxed = true),
        sourcePreferences = mockk<SourcePreferences>(relaxed = true),
    )

    private fun restorer(tasteRepository: TasteRepository, mangaRepository: MangaRepository = mockk(relaxed = true)) = TasteRestorer(
        tasteRepository = tasteRepository,
        alternateSourceBridgeRepository = mockk(relaxed = true),
        mangaRepository = mangaRepository,
        getManga = GetManga(mangaRepository),
        getMangaTaste = GetMangaTaste(tasteRepository),
        getTagTaste = mockk(relaxed = true),
        getTagAliases = mockk(relaxed = true),
        getDisabledSources = mockk(relaxed = true),
        setSourceEnabled = mockk(relaxed = true),
        upsertTagAlias = mockk(relaxed = true),
        getCrossSourceMangaLinks = GetCrossSourceMangaLinks(tasteRepository),
        upsertCrossSourceMangaLinks = tachiyomi.domain.taste.interactor.UpsertCrossSourceMangaLinks(tasteRepository),
        getCrossSourceGroupPrimary = GetCrossSourceGroupPrimary(tasteRepository),
        getMangaSourceQualitySignals = mockk(relaxed = true),
        upsertMangaSourceQualitySignal = mockk(relaxed = true),
        sourcePreferences = mockk(relaxed = true),
    )

    /** Real protobuf wire round trip -- proves the wire format itself, not merely object equality. */
    private fun roundTripThroughWire(backup: Backup): Backup {
        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)
        return ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
    }

    @Test
    fun `an identity decision survives real creation, real wire encode-decode, and real restore, with no local manga row needed`() = runTest {
        val sourceRepo = freshTasteRepository()
        val now = System.currentTimeMillis()
        val left = CrossSourceRecordKey(10L, "/left")
        val right = CrossSourceRecordKey(20L, "/right")
        sourceRepo.upsertCrossSourceIdentityDecisions(
            listOf(
                CrossSourceIdentityDecision(
                    pair = CrossSourceIdentityDecisionPolicy.canonicalPair(left, right),
                    decision = CrossSourceIdentityDecisionValue.USER_CONFIRMED,
                    decisionVersion = CrossSourceIdentityDecisionPolicy.CURRENT_DECISION_VERSION,
                    evidenceVersion = CrossSourceIdentityDecisionPolicy.CURRENT_EVIDENCE_VERSION,
                    reasonCodes = setOf(CrossSourceIdentityReasonCode.USER_CONFIRMATION),
                    reviewState = CrossSourceIdentityReviewState.CURRENT,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                ),
            ),
        )

        // Real creation, from the real (if in-memory) source database.
        val backupDecisions = creator(sourceRepo).backupCrossSourceIdentityDecisions()
        assertEquals(1, backupDecisions.size, "real BackupCreator logic must produce exactly the one decision seeded")

        // Real wire round trip.
        val decoded = roundTripThroughWire(Backup(backupManga = emptyList(), backupCrossSourceIdentityDecisions = backupDecisions))
        assertEquals(1, decoded.backupCrossSourceIdentityDecisions.size)

        // Real restore, against an INDEPENDENT target database -- no manga row for either side of
        // the pair exists here at all, proving identity decisions restore without requiring a local
        // library row (unlike manga_taste, which does).
        val targetRepo = freshTasteRepository()
        val errors = restorer(targetRepo).restoreTasteProfileBundle(
            backupMangaTastes = emptyList(),
            backupTagTastes = emptyList(),
            backupTagAliases = emptyList(),
            backupDisabledSources = emptyList(),
            backupCrossSourceMangaLinks = emptyList(),
            backupCrossSourceGroupPrimaries = emptyList(),
            backupCrossSourceIdentityDecisions = decoded.backupCrossSourceIdentityDecisions,
            backupAlternateSourceBridges = emptyList(),
            backupAlternateSourceBridgeMappings = emptyList(),
            backupMangaSourceQualitySignals = emptyList(),
            backupSeenMangaKeys = emptyList(),
            backupSavedFocusModes = emptyList(),
        )

        assertTrue(errors.isEmpty(), "expected a clean restore, got: $errors")
        val restored = targetRepo.getAllCrossSourceIdentityDecisions()
        assertEquals(1, restored.size, "the identity decision must survive the complete create-encode-decode-restore path")
        assertEquals(CrossSourceIdentityDecisionValue.USER_CONFIRMED, restored.single().decision)
        assertEquals(CrossSourceIdentityReviewState.CURRENT, restored.single().reviewState)
    }

    @Test
    fun `ratings, links, a primary, and an identity decision for the same group all round trip together`() = runTest {
        val sourceRepo = freshTasteRepository()
        val now = System.currentTimeMillis()
        val a = CrossSourceRecordKey(1L, "/a")
        val b = CrossSourceRecordKey(2L, "/b")
        val groupId = "group-e2e"

        // KMK: manga_taste's primary key is mangaId -- both rows MUST use distinct fixture ids here
        // (they never correspond to real local rows in the SOURCE db either) or the second upsert
        // silently overwrites the first as a same-row collision.
        sourceRepo.upsertMangaTaste(MangaTaste(mangaId = 501, source = a.source, url = a.url, title = "Alpha", rating = MangaRating.LOVE.value, createdAt = now, updatedAt = now))
        sourceRepo.upsertMangaTaste(MangaTaste(mangaId = 502, source = b.source, url = b.url, title = "Alpha Alt", rating = MangaRating.LOVE.value, createdAt = now, updatedAt = now))
        sourceRepo.upsertCrossSourceMangaLinks(
            listOf(
                CrossSourceMangaLink(source = a.source, url = a.url, groupId = groupId, title = "Alpha", createdAt = now, updatedAt = now),
                CrossSourceMangaLink(source = b.source, url = b.url, groupId = groupId, title = "Alpha Alt", createdAt = now, updatedAt = now),
            ),
        )
        sourceRepo.upsertCrossSourceGroupPrimary(CrossSourceGroupPrimary(groupId = groupId, source = a.source, url = a.url, updatedAt = now))
        sourceRepo.upsertCrossSourceIdentityDecisions(
            listOf(
                CrossSourceIdentityDecision(
                    pair = CrossSourceIdentityDecisionPolicy.canonicalPair(a, b),
                    decision = CrossSourceIdentityDecisionValue.USER_CONFIRMED,
                    decisionVersion = CrossSourceIdentityDecisionPolicy.CURRENT_DECISION_VERSION,
                    evidenceVersion = CrossSourceIdentityDecisionPolicy.CURRENT_EVIDENCE_VERSION,
                    reasonCodes = setOf(CrossSourceIdentityReasonCode.USER_CONFIRMATION),
                    reviewState = CrossSourceIdentityReviewState.CURRENT,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                ),
            ),
        )

        val c = creator(sourceRepo)
        val backup = Backup(
            backupManga = emptyList(),
            backupMangaTastes = c.backupMangaTastes(),
            backupCrossSourceMangaLinks = c.backupCrossSourceMangaLinks(),
            backupCrossSourceGroupPrimaries = c.backupCrossSourceGroupPrimaries(),
            backupCrossSourceIdentityDecisions = c.backupCrossSourceIdentityDecisions(),
        )
        assertEquals(2, backup.backupMangaTastes.size)
        assertEquals(2, backup.backupCrossSourceMangaLinks.size)
        assertEquals(1, backup.backupCrossSourceGroupPrimaries.size)
        assertEquals(1, backup.backupCrossSourceIdentityDecisions.size)

        val decoded = roundTripThroughWire(backup)

        // Target device already has both sides of the pair as real local manga rows (e.g. both
        // extensions installed and both manga already known locally) -- this exercises the
        // resolves-by-(source,url) manga_taste path for real, alongside the manga-row-independent
        // relationship tables, so the FULL graph restores with zero errors, not just the relationship
        // tables in isolation.
        val target = freshRepositories()
        target.manga.insertNetworkManga(
            listOf(
                Manga.create().copy(source = a.source, url = a.url, ogTitle = "Alpha", favorite = false, initialized = true),
                Manga.create().copy(source = b.source, url = b.url, ogTitle = "Alpha Alt", favorite = false, initialized = true),
            ),
            updateInfo = false,
        )
        val errors = restorer(target.taste, target.manga).restoreTasteProfileBundle(
            backupMangaTastes = decoded.backupMangaTastes,
            backupTagTastes = emptyList(),
            backupTagAliases = emptyList(),
            backupDisabledSources = emptyList(),
            backupCrossSourceMangaLinks = decoded.backupCrossSourceMangaLinks,
            backupCrossSourceGroupPrimaries = decoded.backupCrossSourceGroupPrimaries,
            backupCrossSourceIdentityDecisions = decoded.backupCrossSourceIdentityDecisions,
            backupAlternateSourceBridges = emptyList(),
            backupAlternateSourceBridgeMappings = emptyList(),
            backupMangaSourceQualitySignals = emptyList(),
            backupSeenMangaKeys = emptyList(),
            backupSavedFocusModes = emptyList(),
        )

        assertTrue(errors.isEmpty(), "expected a fully clean restore of the whole relationship graph, got: $errors")
        assertEquals(2, target.taste.getAllMangaTastes().size)
        assertEquals(2, target.taste.getAllCrossSourceMangaLinks().size)
        assertEquals(1, target.taste.getAllCrossSourceGroupPrimaries().size)
        assertEquals(1, target.taste.getAllCrossSourceIdentityDecisions().size)
    }

    @Test
    fun `a real backup with an empty taste profile restores cleanly with zero errors and zero rows`() = runTest {
        val sourceRepo = freshTasteRepository()
        val c = creator(sourceRepo)
        val backup = Backup(
            backupManga = emptyList(),
            backupMangaTastes = c.backupMangaTastes(),
            backupCrossSourceMangaLinks = c.backupCrossSourceMangaLinks(),
            backupCrossSourceGroupPrimaries = c.backupCrossSourceGroupPrimaries(),
            backupCrossSourceIdentityDecisions = c.backupCrossSourceIdentityDecisions(),
        )
        val decoded = roundTripThroughWire(backup)

        val targetRepo = freshTasteRepository()
        val errors = restorer(targetRepo).restoreTasteProfileBundle(
            backupMangaTastes = decoded.backupMangaTastes,
            backupTagTastes = emptyList(),
            backupTagAliases = emptyList(),
            backupDisabledSources = emptyList(),
            backupCrossSourceMangaLinks = decoded.backupCrossSourceMangaLinks,
            backupCrossSourceGroupPrimaries = decoded.backupCrossSourceGroupPrimaries,
            backupCrossSourceIdentityDecisions = decoded.backupCrossSourceIdentityDecisions,
            backupAlternateSourceBridges = emptyList(),
            backupAlternateSourceBridgeMappings = emptyList(),
            backupMangaSourceQualitySignals = emptyList(),
            backupSeenMangaKeys = emptyList(),
            backupSavedFocusModes = emptyList(),
        )

        assertTrue(errors.isEmpty())
        assertTrue(targetRepo.getAllCrossSourceIdentityDecisions().isEmpty())
    }

    @Test
    fun `a tombstoned identity decision round trips its deletedAt and is not silently dropped by creation or decode`() = runTest {
        val sourceRepo = freshTasteRepository()
        val now = System.currentTimeMillis()
        val left = CrossSourceRecordKey(10L, "/left")
        val right = CrossSourceRecordKey(20L, "/right")
        sourceRepo.upsertCrossSourceIdentityDecisions(
            listOf(
                CrossSourceIdentityDecision(
                    pair = CrossSourceIdentityDecisionPolicy.canonicalPair(left, right),
                    decision = CrossSourceIdentityDecisionValue.USER_REJECTED,
                    decisionVersion = CrossSourceIdentityDecisionPolicy.CURRENT_DECISION_VERSION,
                    evidenceVersion = CrossSourceIdentityDecisionPolicy.CURRENT_EVIDENCE_VERSION,
                    reasonCodes = setOf(CrossSourceIdentityReasonCode.USER_CONFIRMATION),
                    reviewState = CrossSourceIdentityReviewState.CURRENT,
                    createdAt = now,
                    updatedAt = now + 1,
                    deletedAt = now + 1,
                ),
            ),
        )

        val backupDecisions = creator(sourceRepo).backupCrossSourceIdentityDecisions()
        assertEquals(1, backupDecisions.size, "a tombstoned row must still be captured by creation, not silently excluded")
        assertTrue(backupDecisions.single().deletedAt > 0L)

        val decoded = roundTripThroughWire(Backup(backupManga = emptyList(), backupCrossSourceIdentityDecisions = backupDecisions))
        assertEquals(1, decoded.backupCrossSourceIdentityDecisions.size)
        assertEquals(backupDecisions.single().deletedAt, decoded.backupCrossSourceIdentityDecisions.single().deletedAt)
    }
}
// KMK <--
