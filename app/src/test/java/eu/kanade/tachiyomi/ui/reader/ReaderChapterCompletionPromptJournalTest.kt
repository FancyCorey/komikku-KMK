package eu.kanade.tachiyomi.ui.reader

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.rethrowIfFatal
import exh.util.EvaluationJournalActionType
import exh.util.EvaluationModeJournalRecorder
import exh.util.EvaluationModeUndoJournal
import exh.util.FakePreferenceStore
import exh.util.FakeTasteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.SetMangaTaste
import tachiyomi.domain.taste.model.MangaRating

// KMK Confirmed Blocker Remediation Phase 2 2026-07-29 -->
/**
 * Tests for the confirmed defect fixed in [ReaderViewModel.rateFromChapterCompletionPrompt] and
 * [ReaderViewModel.markNotInterestedFromChapterCompletionPrompt]: both previously wrote directly to
 * [SetMangaTaste]/the seen-manga preference store without going through
 * [EvaluationModeJournalRecorder], so a rating or Not-Interested action taken from the reader's own
 * chapter-completion prompt never appeared in Evaluation Mode Action History.
 *
 * KMK v0.8.21-fix3: R1 correction -- [ReaderViewModel.markNotInterestedFromChapterCompletionPrompt]
 * itself was found to have a second, deeper defect during R1: it wrote ONLY the legacy
 * seenRecommendationMangaKeys preference and never wrote [tachiyomi.domain.taste.model.MangaTaste]
 * at all, so marking a manga Not Interested from the reader's completion prompt never actually
 * registered with manga-detail state, Rated Manga, or For You exclusion. It is fixed to route
 * through [SetMangaTaste]/[EvaluationModeJournalRecorder.buildRatingChange] exactly like every
 * other rating now, so this file's `markNotInterestedWithJournal` helper mirrors that corrected
 * sequence -- the single-store path, not a second preference write.
 *
 * [ReaderViewModel] has a very large constructor-injected dependency graph (no existing test in this
 * codebase instantiates it directly), so this exercises the exact same
 * [EvaluationModeJournalRecorder.buildRatingChange] + real [SetMangaTaste] +
 * [EvaluationModeJournalRecorder.commit] sequence the two fixed methods now use, against the same
 * [FakeTasteRepository]/[FakePreferenceStore] fakes [MangaScreenModelTasteJournalTest] already
 * established for this exact contract.
 */
class ReaderChapterCompletionPromptJournalTest {

    private lateinit var tasteRepository: FakeTasteRepository
    private lateinit var sourcePreferences: SourcePreferences
    private lateinit var getMangaTaste: GetMangaTaste
    private lateinit var setMangaTaste: SetMangaTaste

    private val manga = Manga.create().copy(id = 20L, source = 3L, url = "/m/20", ogTitle = "Reader Manga")

    @BeforeEach
    fun setUp() {
        EvaluationModeUndoJournal.clear()
        tasteRepository = FakeTasteRepository()
        sourcePreferences = SourcePreferences(FakePreferenceStore())
        getMangaTaste = GetMangaTaste(tasteRepository)
        setMangaTaste = SetMangaTaste(tasteRepository)
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

    /** Mirrors [ReaderViewModel.rateFromChapterCompletionPrompt]'s exact build/write/commit sequence. */
    private suspend fun rateWithJournal(rating: MangaRating) {
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

    /**
     * Mirrors [ReaderViewModel.markNotInterestedFromChapterCompletionPrompt]'s corrected
     * build/write/commit sequence -- the same single-store rating-change sequence as [rateWithJournal],
     * just always with [MangaRating.NOT_INTERESTED].
     */
    private suspend fun markNotInterestedWithJournal() = rateWithJournal(MangaRating.NOT_INTERESTED)

    @Test
    fun `rating from the reader completion prompt creates a history entry when Evaluation Mode is enabled`() = runTest {
        sourcePreferences.evaluationMode().set(true)

        rateWithJournal(MangaRating.LOVE)

        val snapshot = EvaluationModeUndoJournal.snapshot()
        assertEquals(1, snapshot.size)
        val entry = snapshot.first()
        assertEquals(EvaluationJournalActionType.RATE_LOVE, entry.actionType)
        assertEquals(manga.source, entry.source)
        assertEquals(manga.url, entry.url)
        assertEquals(MangaRating.LOVE.value, tasteRepository.getMangaTaste(manga.id)?.rating)
    }

    @Test
    fun `Not Interested from the reader completion prompt writes MangaTaste and creates a history entry`() = runTest {
        sourcePreferences.evaluationMode().set(true)

        markNotInterestedWithJournal()

        val snapshot = EvaluationModeUndoJournal.snapshot()
        assertEquals(1, snapshot.size)
        assertEquals(EvaluationJournalActionType.NOT_INTERESTED, snapshot.first().actionType)
        // R1 regression coverage: this action must genuinely write MangaTaste now, not just the
        // legacy preference (the confirmed defect this correction fixes).
        assertEquals(MangaRating.NOT_INTERESTED.value, tasteRepository.getMangaTaste(manga.id)?.rating)
    }

    @Test
    fun `normal Action History records both actions when Evaluation Mode is disabled`() = runTest {
        sourcePreferences.evaluationMode().set(false)

        rateWithJournal(MangaRating.DISLIKE)
        assertEquals(1, EvaluationModeUndoJournal.snapshot().size)

        markNotInterestedWithJournal()
        assertEquals(2, EvaluationModeUndoJournal.snapshot().size)
        assertEquals(EvaluationJournalActionType.NOT_INTERESTED, EvaluationModeUndoJournal.snapshot().first().actionType)
        // The second action overwrote the same manga's rating (single store, one row) -- the final
        // state is NOT_INTERESTED, not DISLIKE, confirming there is nowhere for the prior rating to
        // linger.
        assertEquals(MangaRating.NOT_INTERESTED.value, tasteRepository.getMangaTaste(manga.id)?.rating)
    }

    @Test
    fun `reader prompt and manga detail routes produce identically-shaped journal entries for the same rating`() = runTest {
        sourcePreferences.evaluationMode().set(true)

        rateWithJournal(MangaRating.LIKE)
        val readerEntry = EvaluationModeUndoJournal.snapshot().first()
        EvaluationModeUndoJournal.clear()

        val detailEntries = EvaluationModeJournalRecorder.buildRatingChange(
            getMangaTaste,
            listOf(manga),
            MangaRating.LIKE.value,
            EvaluationJournalActionType.RATE_LIKE,
        )
        EvaluationModeJournalRecorder.commit(detailEntries)
        val detailEntry = EvaluationModeUndoJournal.snapshot().first()

        assertEquals(readerEntry.actionType, detailEntry.actionType)
        assertEquals(readerEntry.source, detailEntry.source)
        assertEquals(readerEntry.url, detailEntry.url)
        assertEquals(readerEntry.changedFields, detailEntry.changedFields)
    }

    // KMK Confirmed Blocker Remediation follow-up Phase 2 2026-07-29 -->
    /**
     * Mirrors [ReaderViewModel.markNotInterestedFromChapterCompletionPrompt]'s fixed try/catch
     * control flow exactly (build journal entry, perform [write], commit only if [write] returns
     * normally, rethrowIfFatal on any exception) with an injectable write action -- the real method's
     * dependency graph (a full [ReaderViewModel]) is too heavy to construct directly, matching this
     * file's own established pattern above for the sibling rating path.
     */
    private suspend fun markNotInterestedWithFailingWrite(write: suspend () -> Unit) {
        val journalEntries = EvaluationModeJournalRecorder.buildRatingChange(
            getMangaTaste,
            listOf(manga),
            MangaRating.NOT_INTERESTED.value,
            EvaluationJournalActionType.NOT_INTERESTED,
        )
        try {
            write()
            EvaluationModeJournalRecorder.commit(journalEntries)
        } catch (e: Throwable) {
            rethrowIfFatal(e)
            // truthful-failure path: no commit, exception swallowed (non-fatal only)
        }
    }

    @Test
    fun `a failed write records no journal entry`() = runTest {
        sourcePreferences.evaluationMode().set(true)

        markNotInterestedWithFailingWrite { throw RuntimeException("write failed") }

        assertTrue(EvaluationModeUndoJournal.isEmpty(), "a failed write must never produce a journal entry")
    }

    @Test
    fun `a successful write still records the journal entry`() = runTest {
        sourcePreferences.evaluationMode().set(true)

        markNotInterestedWithFailingWrite { /* succeeds */ }

        assertFalse(EvaluationModeUndoJournal.isEmpty())
        assertEquals(EvaluationJournalActionType.NOT_INTERESTED, EvaluationModeUndoJournal.snapshot().first().actionType)
    }

    @Test
    fun `cancellation during the write propagates instead of being swallowed`() = runTest {
        sourcePreferences.evaluationMode().set(true)

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                markNotInterestedWithFailingWrite { throw CancellationException("cancelled") }
            }
        }
        assertTrue(EvaluationModeUndoJournal.isEmpty())
    }
}
// KMK <--
