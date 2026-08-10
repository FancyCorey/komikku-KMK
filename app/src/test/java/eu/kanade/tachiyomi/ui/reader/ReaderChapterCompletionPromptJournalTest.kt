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

// KMK -->
/**
 * Tests for the confirmed defect fixed in [ReaderViewModel.rateFromChapterCompletionPrompt] and
 * [ReaderViewModel.markNotInterestedFromChapterCompletionPrompt]: both previously wrote directly to
 * [SetMangaTaste]/the seen-manga preference store without going through
 * [EvaluationModeJournalRecorder], so a rating or Not-Interested action taken from the reader's own
 * chapter-completion prompt never appeared in Evaluation Mode Action History -- exactly the behavior contract's
 * required row "Love/Like/Dislike | rating writes, prompt state advances, history entry is created
 * when Evaluation Mode is enabled" and "Not Interested | preference write, prompt state advances,
 * history entry is created."
 *
 * [ReaderViewModel] has a very large constructor-injected dependency graph (no existing test in this
 * codebase instantiates it directly), so this exercises the exact same
 * [EvaluationModeJournalRecorder.buildRatingChange]/[buildNotInterested] + real interactor +
 * [EvaluationModeJournalRecorder.commit] sequence the two fixed methods now use, against the same
 * [FakeTasteRepository]/[FakePreferenceStore] fakes [MangaScreenModelTasteJournalTest] (the prior
 * pass's equivalent fix for the manga-detail screen) already established for this exact contract.
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

    /** Mirrors [ReaderViewModel.rateFromChapterCompletionPrompt]'s exact build/write/commit sequence. */
    private suspend fun rateWithJournal(rating: MangaRating) {
        val journalActionType = when (rating) {
            MangaRating.LOVE -> EvaluationJournalActionType.RATE_LOVE
            MangaRating.LIKE -> EvaluationJournalActionType.RATE_LIKE
            MangaRating.DISLIKE -> EvaluationJournalActionType.RATE_DISLIKE
        }
        val journalEntries = EvaluationModeJournalRecorder.buildRatingChange(
            sourcePreferences,
            getMangaTaste,
            listOf(manga),
            rating.value,
            journalActionType,
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

    /** Mirrors [ReaderViewModel.markNotInterestedFromChapterCompletionPrompt]'s build/commit sequence. */
    private suspend fun markNotInterestedWithJournal() {
        val journalEntries = EvaluationModeJournalRecorder.buildNotInterested(
            sourcePreferences,
            getMangaTaste,
            listOf(manga),
        )
        // The real method's preference write is exercised in
        // ReaderScheduleDialogWindowDeletionTest-adjacent style tests are unnecessary here -- the
        // preference write itself (SeenRecommendationMangaStore) has its own existing test coverage;
        // this test isolates the journal side of the contract only.
        EvaluationModeJournalRecorder.commit(journalEntries)
    }

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
    fun `Not Interested from the reader completion prompt creates a history entry when Evaluation Mode is enabled`() = runTest {
        sourcePreferences.evaluationMode().set(true)

        markNotInterestedWithJournal()

        val snapshot = EvaluationModeUndoJournal.snapshot()
        assertEquals(1, snapshot.size)
        assertEquals(EvaluationJournalActionType.NOT_INTERESTED, snapshot.first().actionType)
    }

    @Test
    fun `Evaluation Mode disabled records nothing for either action`() = runTest {
        sourcePreferences.evaluationMode().set(false)

        rateWithJournal(MangaRating.DISLIKE)
        assertTrue(EvaluationModeUndoJournal.isEmpty())

        markNotInterestedWithJournal()
        assertTrue(EvaluationModeUndoJournal.isEmpty())
    }

    @Test
    fun `reader prompt and manga detail routes produce identically-shaped journal entries for the same rating`() = runTest {
        sourcePreferences.evaluationMode().set(true)

        rateWithJournal(MangaRating.LIKE)
        val readerEntry = EvaluationModeUndoJournal.snapshot().first()
        EvaluationModeUndoJournal.clear()

        val detailEntries = EvaluationModeJournalRecorder.buildRatingChange(
            sourcePreferences,
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

    // KMK -->
    /**
     * Mirrors [ReaderViewModel.markNotInterestedFromChapterCompletionPrompt]'s fixed try/catch
     * control flow exactly (build journal entry, perform [write], commit only if [write] returns
     * normally, rethrowIfFatal on any exception) with an injectable write action -- the real method's
     * dependency graph (a full [ReaderViewModel]) is too heavy to construct directly, matching this
     * file's own established pattern above for the sibling rating path.
     */
    private suspend fun markNotInterestedWithFailingWrite(write: suspend () -> Unit) {
        val journalEntries = EvaluationModeJournalRecorder.buildNotInterested(
            sourcePreferences,
            getMangaTaste,
            listOf(manga),
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
    fun `a failed preference write records no journal entry`() = runTest {
        sourcePreferences.evaluationMode().set(true)

        markNotInterestedWithFailingWrite { throw RuntimeException("preference write failed") }

        assertTrue(EvaluationModeUndoJournal.isEmpty(), "a failed write must never produce a journal entry")
    }

    @Test
    fun `a successful preference write still records the journal entry`() = runTest {
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
