package eu.kanade.tachiyomi.ui.manga

import eu.kanade.domain.source.service.SourcePreferences
import exh.util.EvaluationJournalActionType
import exh.util.EvaluationModeJournalRecorder
import exh.util.EvaluationModeUndoJournal
import exh.util.EvaluationModeUndoService
import exh.util.FakePreferenceStore
import exh.util.FakeTasteRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.interactor.ClearMangaTaste
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.SetMangaTaste
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste

// KMK v0.8.20-fix1 -->
/**
 * Tests for [MangaScreenModel.setMangaTaste]/[clearMangaTaste]'s Evaluation Mode journal wiring
 * (the fix for the confirmed defect: detail-page ratings previously bypassed
 * [EvaluationModeJournalRecorder] entirely, so they never appeared in Action History).
 *
 * [MangaScreenModel] itself has a very large constructor-injected dependency graph, so rather than
 * instantiating the real screen model, this exercises exactly the same build-before-write/commit-
 * after-success sequence [MangaScreenModel.setMangaTaste]/[clearMangaTaste] now use --
 * [EvaluationModeJournalRecorder.buildRatingChange] + the real [SetMangaTaste]/[ClearMangaTaste]
 * interactors + [EvaluationModeJournalRecorder.commit] -- against a [FakeTasteRepository] and
 * [FakePreferenceStore], the same fakes [exh.util.EvaluationModeUndoServiceRestoreTest] and
 * [exh.util.LibraryUndoServiceRestoreTest] already use for this exact contract. This proves the
 * shared recorder call sequence behaves correctly; [MangaScreenModel]'s own two methods call this
 * exact sequence verbatim (see their source).
 *
 * KMK v0.8.21-fix3: R1 correction -- [MangaRating.NOT_INTERESTED] is exercised here as just
 * another rating value through the exact same [setMangaTasteWithJournal]/[clearMangaTasteWithJournal]
 * sequence as Love/Like/Dislike. The two test files that previously mirrored a separate
 * `markSeen()`/`clearSeen()` dual-write sequence (`MangaScreenModelNotInterestedJournalTest`,
 * `MangaScreenModelNotInterestedToRatingTransitionTest`) are deleted -- that sequence no longer
 * exists, and this single-store path is genuinely identical for all four rating values now.
 */
class MangaScreenModelTasteJournalTest {

    private lateinit var tasteRepository: FakeTasteRepository
    private lateinit var sourcePreferences: SourcePreferences
    private lateinit var getMangaTaste: GetMangaTaste
    private lateinit var setMangaTaste: SetMangaTaste
    private lateinit var clearMangaTaste: ClearMangaTaste

    private val manga = Manga.create().copy(id = 10L, source = 1L, url = "/m/10", ogTitle = "Test Manga")

    @BeforeEach
    fun setUp() {
        EvaluationModeUndoJournal.clear()
        tasteRepository = FakeTasteRepository()
        sourcePreferences = SourcePreferences(FakePreferenceStore())
        getMangaTaste = GetMangaTaste(tasteRepository)
        setMangaTaste = SetMangaTaste(tasteRepository)
        clearMangaTaste = ClearMangaTaste(tasteRepository)
    }

    @AfterEach
    fun tearDown() {
        EvaluationModeUndoJournal.clear()
    }

    private fun journalActionType(rating: MangaRating) = when (rating) {
        MangaRating.LOVE -> EvaluationJournalActionType.RATE_LOVE
        MangaRating.LIKE -> EvaluationJournalActionType.RATE_LIKE
        MangaRating.DISLIKE -> EvaluationJournalActionType.RATE_DISLIKE
        MangaRating.NOT_INTERESTED -> EvaluationJournalActionType.NOT_INTERESTED
    }

    /** Mirrors [MangaScreenModel.setMangaTaste]'s exact build/write/commit sequence. */
    private suspend fun setMangaTasteWithJournal(rating: MangaRating) {
        val journalEntries = EvaluationModeJournalRecorder.buildRatingChange(
            getMangaTaste,
            listOf(manga),
            rating.value,
            journalActionType(rating),
        )
        setMangaTaste.await(
            mangaId = manga.id,
            source = manga.source,
            url = manga.url,
            title = manga.title,
            rating = rating,
        )
        EvaluationModeJournalRecorder.commit(journalEntries)
    }

    /** Mirrors [MangaScreenModel.clearMangaTaste]'s exact build/write/commit sequence. */
    private suspend fun clearMangaTasteWithJournal() {
        val journalEntries = EvaluationModeJournalRecorder.buildRatingChange(
            getMangaTaste,
            listOf(manga),
            null,
            EvaluationJournalActionType.CLEAR_RATING,
        )
        clearMangaTaste.await(manga.source, manga.url)
        EvaluationModeJournalRecorder.commit(journalEntries)
    }

    @Test
    fun `detail-page rating creates a history entry after success`() = runTest {
        sourcePreferences.evaluationMode().set(true)

        setMangaTasteWithJournal(MangaRating.LOVE)

        val snapshot = EvaluationModeUndoJournal.snapshot()
        assertEquals(1, snapshot.size)
        val entry = snapshot.first()
        assertEquals(EvaluationJournalActionType.RATE_LOVE, entry.actionType)
        assertEquals(manga.source, entry.source)
        assertEquals(manga.url, entry.url)
        assertEquals(null, entry.previousRating)
        assertEquals(MangaRating.LOVE.value, entry.newRating)
        // The write actually happened -- the journal isn't just recording an intent.
        assertEquals(MangaRating.LOVE.value, tasteRepository.getMangaTaste(manga.id)?.rating)
    }

    @Test
    fun `detail-page clear rating creates a history entry after success`() = runTest {
        sourcePreferences.evaluationMode().set(true)
        // Seed an existing rating so clear has something to clear and a previousRating to record.
        tasteRepository.upsertMangaTaste(
            MangaTaste(
                mangaId = manga.id,
                source = manga.source,
                url = manga.url,
                title = manga.title,
                rating = MangaRating.LIKE.value,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )

        clearMangaTasteWithJournal()

        val snapshot = EvaluationModeUndoJournal.snapshot()
        assertEquals(1, snapshot.size)
        val entry = snapshot.first()
        assertEquals(EvaluationJournalActionType.CLEAR_RATING, entry.actionType)
        assertEquals(MangaRating.LIKE.value, entry.previousRating)
        assertEquals(null, entry.newRating)
        // The write actually happened.
        assertEquals(null, tasteRepository.getMangaTaste(manga.id))
    }

    @Test
    fun `failed write does not create a history entry`() = runTest {
        sourcePreferences.evaluationMode().set(true)
        val failingRepository = object : tachiyomi.domain.taste.repository.TasteRepository by tasteRepository {
            override suspend fun upsertMangaTaste(taste: MangaTaste) = throw RuntimeException("simulated write failure")
        }
        val failingSetMangaTaste = SetMangaTaste(failingRepository)

        // Build (as MangaScreenModel does), attempt the write, and only commit on success -- here the
        // write throws, so commit must never run, exactly like MangaScreenModel's real method.
        val journalEntries = EvaluationModeJournalRecorder.buildRatingChange(
            getMangaTaste,
            listOf(manga),
            MangaRating.LOVE.value,
            EvaluationJournalActionType.RATE_LOVE,
        )
        try {
            failingSetMangaTaste.await(
                mangaId = manga.id,
                source = manga.source,
                url = manga.url,
                title = manga.title,
                rating = MangaRating.LOVE,
            )
            EvaluationModeJournalRecorder.commit(journalEntries)
        } catch (_: RuntimeException) {
            // Expected -- commit above must not have run.
        }

        assertTrue(EvaluationModeUndoJournal.isEmpty(), "a failed write must not leave a journal entry")
        // KMK v0.8.21-fix3: R1 adversarial coverage -- a failed write leaves the store exactly as it
        // was before the attempt (there is only one store now, so "contradictory stores" cannot
        // arise even in principle; this asserts the failure case still leaves it genuinely untouched).
        assertNull(tasteRepository.getMangaTaste(manga.id), "a failed write must not change stored state")
    }

    @Test
    fun `ordinary-user rating creates history while Evaluation Mode is disabled`() = runTest {
        sourcePreferences.evaluationMode().set(false)

        setMangaTasteWithJournal(MangaRating.DISLIKE)

        // The write still happened and the ordinary-user history boundary is active.
        assertEquals(MangaRating.DISLIKE.value, tasteRepository.getMangaTaste(manga.id)?.rating)
        assertEquals(EvaluationJournalActionType.RATE_DISLIKE, EvaluationModeUndoJournal.snapshot().single().actionType)
    }

    @Test
    fun `detail-page and For You routes produce identically-shaped journal entries for the same rating`() = runTest {
        sourcePreferences.evaluationMode().set(true)

        // Detail-page path (the new code under test).
        setMangaTasteWithJournal(MangaRating.LIKE)
        val detailEntry = EvaluationModeUndoJournal.snapshot().first()
        EvaluationModeUndoJournal.clear()

        // The same shared-recorder call the For You/Loved/Liked/Disliked routes already use
        // (exh.recs.BrowsePersonalRecommendationsScreenModel.rateSelected), applied to the same manga.
        val forYouEntries = EvaluationModeJournalRecorder.buildRatingChange(
            getMangaTaste,
            listOf(manga),
            MangaRating.LIKE.value,
            EvaluationJournalActionType.RATE_LIKE,
        )
        EvaluationModeJournalRecorder.commit(forYouEntries)
        val forYouEntry = EvaluationModeUndoJournal.snapshot().first()

        // Same action type, same identity, same field set -- one shared history semantics, not two.
        assertEquals(detailEntry.actionType, forYouEntry.actionType)
        assertEquals(detailEntry.source, forYouEntry.source)
        assertEquals(detailEntry.url, forYouEntry.url)
        assertEquals(detailEntry.changedFields, forYouEntry.changedFields)
    }

    // KMK v0.8.21-fix3: R1 correction -- Not Interested is exercised as an ordinary rating value,
    // through the identical sequence as Love/Like/Dislike above. No special-case dual-write path
    // exists to test separately anymore.

    @Test
    fun `marking Not Interested creates a history entry and writes exactly one MangaTaste row`() = runTest {
        sourcePreferences.evaluationMode().set(true)

        setMangaTasteWithJournal(MangaRating.NOT_INTERESTED)

        val snapshot = EvaluationModeUndoJournal.snapshot()
        assertEquals(1, snapshot.size)
        val entry = snapshot.first()
        assertEquals(EvaluationJournalActionType.NOT_INTERESTED, entry.actionType)
        assertEquals(MangaRating.NOT_INTERESTED.value, entry.newRating)
        assertEquals(MangaRating.NOT_INTERESTED.value, tasteRepository.getMangaTaste(manga.id)?.rating)
    }

    @Test
    fun `an existing rating and Not Interested cannot coexist -- setting one replaces the other in the same row`() = runTest {
        sourcePreferences.evaluationMode().set(true)
        setMangaTasteWithJournal(MangaRating.LOVE)
        assertEquals(MangaRating.LOVE.value, tasteRepository.getMangaTaste(manga.id)?.rating)

        setMangaTasteWithJournal(MangaRating.NOT_INTERESTED)

        // Exactly one MangaTaste row exists for this manga, and it now holds NOT_INTERESTED -- not
        // LOVE, and not both. There is structurally no way for two rating-family states to coexist:
        // MangaTaste has exactly one `rating: Int` column, upserted by mangaId, never appended to.
        val current = tasteRepository.getMangaTaste(manga.id)
        assertEquals(MangaRating.NOT_INTERESTED.value, current?.rating)
        assertFalse(current?.rating == MangaRating.LOVE.value, "the prior LOVE rating must not still be present")
    }

    @Test
    fun `transitioning from Not Interested to an ordinary rating is the same single-store write, and undo restores it correctly`() = runTest {
        sourcePreferences.evaluationMode().set(true)
        val undoService = EvaluationModeUndoService(getMangaTaste, setMangaTaste, clearMangaTaste)

        setMangaTasteWithJournal(MangaRating.NOT_INTERESTED)
        EvaluationModeUndoJournal.clear()
        setMangaTasteWithJournal(MangaRating.LOVE)
        val entry = EvaluationModeUndoJournal.snapshot().first()

        assertEquals(MangaRating.NOT_INTERESTED.value, entry.previousRating)
        assertEquals(MangaRating.LOVE.value, entry.newRating)

        val outcome = undoService.undo(entry.id)

        assertEquals(1, outcome.restoredCount)
        assertEquals(MangaRating.NOT_INTERESTED.value, tasteRepository.getMangaTaste(manga.id)?.rating, "undo must restore the prior Not Interested state")
    }

    @Test
    fun `clearing Not Interested uses the same owner as clearing any other rating`() = runTest {
        sourcePreferences.evaluationMode().set(true)
        setMangaTasteWithJournal(MangaRating.NOT_INTERESTED)

        clearMangaTasteWithJournal()

        assertNull(tasteRepository.getMangaTaste(manga.id))
        val entry = EvaluationModeUndoJournal.snapshot().first()
        assertEquals(EvaluationJournalActionType.CLEAR_RATING, entry.actionType)
        assertEquals(MangaRating.NOT_INTERESTED.value, entry.previousRating)
    }
}
// KMK <--
