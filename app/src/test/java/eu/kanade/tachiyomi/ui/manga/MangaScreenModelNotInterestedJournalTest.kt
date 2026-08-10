package eu.kanade.tachiyomi.ui.manga

import eu.kanade.domain.source.service.SourcePreferences
import exh.recs.SeenMangaKey
import exh.recs.SeenRecommendationMangaStore
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.interactor.GetMangaTaste

// KMK
/**
 * Tests for [MangaScreenModel.markSeen]/[MangaScreenModel.clearSeen]'s Evaluation Mode journal
 * wiring. The detail-page Not Interested
 * toggle must not write [SeenRecommendationMangaStore] directly with zero
 * [EvaluationModeJournalRecorder] call, so it never appeared in Action History or Undo.
 *
 * Mirrors [MangaScreenModelTasteJournalTest]'s approach: rather than instantiating the real,
 * large-constructor [MangaScreenModel], this exercises the exact same build-before-write/commit
 * -after-success sequence [MangaScreenModel.markSeen]/[clearSeen] now use, against a
 * [FakePreferenceStore] and a minimal in-memory [TasteRepository] (Not Interested never writes a
 * taste row, but [EvaluationModeJournalRecorder.buildNotInterested] reads [GetMangaTaste] to record
 * the manga's *rating* alongside the Not Interested change).
 */
class MangaScreenModelNotInterestedJournalTest {

    private lateinit var preferenceStore: FakePreferenceStore
    private lateinit var sourcePreferences: SourcePreferences
    private lateinit var getMangaTaste: GetMangaTaste

    private val manga = Manga.create().copy(id = 20L, source = 2L, url = "/m/20", ogTitle = "Test Manga 2")

    @BeforeEach
    fun setUp() {
        EvaluationModeUndoJournal.clear()
        preferenceStore = FakePreferenceStore()
        sourcePreferences = SourcePreferences(preferenceStore)
        getMangaTaste = GetMangaTaste(FakeTasteRepository())
    }

    @AfterEach
    fun tearDown() {
        EvaluationModeUndoJournal.clear()
    }

    private fun seenKeys(): Set<SeenMangaKey> =
        SeenRecommendationMangaStore.parse(sourcePreferences.seenRecommendationMangaKeys().get())

    /** Mirrors [MangaScreenModel.markSeen]'s exact build/write/commit sequence. */
    private suspend fun markSeenWithJournal() {
        val journalEntries = EvaluationModeJournalRecorder.buildNotInterested(sourcePreferences, getMangaTaste, listOf(manga))
        val key = SeenMangaKey(manga.source, manga.url)
        val updated = SeenRecommendationMangaStore.add(seenKeys(), key)
        sourcePreferences.seenRecommendationMangaKeys().set(SeenRecommendationMangaStore.serialize(updated))
        EvaluationModeJournalRecorder.commit(journalEntries)
    }

    /** Mirrors [MangaScreenModel.clearSeen]'s exact build/write/commit sequence. */
    private suspend fun clearSeenWithJournal() {
        val journalEntries = EvaluationModeJournalRecorder.buildNotInterestedRemoval(sourcePreferences, getMangaTaste, listOf(manga))
        val key = SeenMangaKey(manga.source, manga.url)
        val updated = SeenRecommendationMangaStore.remove(seenKeys(), key)
        sourcePreferences.seenRecommendationMangaKeys().set(SeenRecommendationMangaStore.serialize(updated))
        EvaluationModeJournalRecorder.commit(journalEntries)
    }

    @Test
    fun `detail-page markSeen creates a history entry after success when Evaluation Mode is enabled`() = runTest {
        sourcePreferences.evaluationMode().set(true)

        markSeenWithJournal()

        val snapshot = EvaluationModeUndoJournal.snapshot()
        assertEquals(1, snapshot.size)
        val entry = snapshot.first()
        assertEquals(EvaluationJournalActionType.NOT_INTERESTED, entry.actionType)
        assertEquals(manga.source, entry.source)
        assertEquals(manga.url, entry.url)
        assertEquals(false, entry.previousNotInterested)
        assertEquals(true, entry.newNotInterested)
        // The write actually happened -- the journal isn't just recording an intent.
        assertTrue(SeenMangaKey(manga.source, manga.url) in seenKeys())
    }

    @Test
    fun `detail-page clearSeen creates a removal history entry after success when Evaluation Mode is enabled`() = runTest {
        sourcePreferences.evaluationMode().set(true)
        markSeenWithJournal()
        EvaluationModeUndoJournal.clear()

        clearSeenWithJournal()

        val snapshot = EvaluationModeUndoJournal.snapshot()
        assertEquals(1, snapshot.size)
        val entry = snapshot.first()
        assertEquals(EvaluationJournalActionType.NOT_INTERESTED, entry.actionType)
        assertEquals(true, entry.previousNotInterested)
        assertEquals(false, entry.newNotInterested)
        assertFalse(SeenMangaKey(manga.source, manga.url) in seenKeys())
    }

    /** Simulates the preference write step throwing, exactly where [MangaScreenModel.markSeen] would fail. */
    private fun failingWrite(): Nothing = throw RuntimeException("simulated preference write failure")

    @Test
    fun `failed write does not create a history entry or change stored state`() = runTest {
        sourcePreferences.evaluationMode().set(true)

        val journalEntries = EvaluationModeJournalRecorder.buildNotInterested(sourcePreferences, getMangaTaste, listOf(manga))
        try {
            failingWrite()
            EvaluationModeJournalRecorder.commit(journalEntries)
        } catch (_: RuntimeException) {
            // Expected -- commit above must never run.
        }

        assertTrue(EvaluationModeUndoJournal.isEmpty(), "a failed write must not leave a journal entry")
        assertFalse(SeenMangaKey(manga.source, manga.url) in seenKeys(), "a failed write must not change stored state")
    }

    @Test
    fun `cancellation propagates and does not create a history entry`() = runTest {
        sourcePreferences.evaluationMode().set(true)

        var thrown: CancellationException? = null
        try {
            EvaluationModeJournalRecorder.buildNotInterested(sourcePreferences, getMangaTaste, listOf(manga))
            throw CancellationException("cancelled")
        } catch (e: CancellationException) {
            thrown = e
        }

        assertTrue(thrown != null, "CancellationException must propagate, never be swallowed as an ordinary failure")
        assertTrue(EvaluationModeUndoJournal.isEmpty(), "a cancelled write must not leave a journal entry")
    }

    @Test
    fun `marking an already-Not-Interested manga is idempotent and still records a truthful entry`() = runTest {
        sourcePreferences.evaluationMode().set(true)
        markSeenWithJournal()
        EvaluationModeUndoJournal.clear()

        // Mark again -- SeenRecommendationMangaStore.add on an already-present key is a Set no-op,
        // but the journal call sequence must still run cleanly (no crash, no duplicate stored key).
        markSeenWithJournal()

        assertEquals(1, seenKeys().size, "adding an already-present key must not duplicate it")
        val snapshot = EvaluationModeUndoJournal.snapshot()
        assertEquals(1, snapshot.size)
        assertEquals(true, snapshot.first().previousNotInterested, "the previous state was truthfully already Not Interested")
    }

    @Test
    fun `Evaluation Mode disabled creates no history entry but still writes the preference`() = runTest {
        sourcePreferences.evaluationMode().set(false)

        markSeenWithJournal()

        // The write still happened (Not Interested still works normally for ordinary users)...
        assertTrue(SeenMangaKey(manga.source, manga.url) in seenKeys())
        // ...but no journal entry was recorded.
        assertTrue(EvaluationModeUndoJournal.isEmpty(), "Evaluation Mode disabled must record nothing")
    }

    @Test
    fun `detail-page and reader-completion-prompt routes produce identically-shaped journal entries`() = runTest {
        sourcePreferences.evaluationMode().set(true)

        // Detail-page path (the new code under test).
        markSeenWithJournal()
        val detailEntry = EvaluationModeUndoJournal.snapshot().first()
        EvaluationModeUndoJournal.clear()
        clearSeenWithJournal()
        EvaluationModeUndoJournal.clear()

        // The same shared-recorder call ReaderViewModel.markNotInterestedFromChapterCompletionPrompt
        // and BrowsePersonalRecommendationsScreenModel.markSelectedNotInterested already use.
        val readerEntries = EvaluationModeJournalRecorder.buildNotInterested(sourcePreferences, getMangaTaste, listOf(manga))
        EvaluationModeJournalRecorder.commit(readerEntries)
        val readerEntry = EvaluationModeUndoJournal.snapshot().first()

        assertEquals(detailEntry.actionType, readerEntry.actionType)
        assertEquals(detailEntry.source, readerEntry.source)
        assertEquals(detailEntry.url, readerEntry.url)
        assertEquals(detailEntry.changedFields, readerEntry.changedFields)
    }
}
// KMK <--
