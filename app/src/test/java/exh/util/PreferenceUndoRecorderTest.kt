package exh.util

import eu.kanade.domain.source.service.SourcePreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.TagPreference

// KMK -->
class PreferenceUndoRecorderTest {

    private val preferenceStore = FakePreferenceStore()
    private val sourcePreferences = SourcePreferences(preferenceStore)

    @Test
    fun `no entry is built when Evaluation Mode is disabled`() {
        sourcePreferences.evaluationMode().set(false)
        val pref = preferenceStore.getInt("test_key", 0)
        assertNull(exh.util.PreferenceUndoRecorder.buildPreferenceEntry(sourcePreferences, PreferenceJournalActionType.MIN_CHAPTER_COUNT, "test", pref, 0, 5))
    }

    @Test
    fun `no entry is built when the value did not actually change`() {
        sourcePreferences.evaluationMode().set(true)
        val pref = preferenceStore.getInt("test_key", 0)
        assertNull(exh.util.PreferenceUndoRecorder.buildPreferenceEntry(sourcePreferences, PreferenceJournalActionType.MIN_CHAPTER_COUNT, "test", pref, 3, 3))
    }

    @Test
    fun `entry captures the previous and expected values with working read-restore functions`() {
        sourcePreferences.evaluationMode().set(true)
        val pref = preferenceStore.getInt("test_key", 0)
        pref.set(5)
        val entry = exh.util.PreferenceUndoRecorder.buildPreferenceEntry(sourcePreferences, PreferenceJournalActionType.MIN_CHAPTER_COUNT, "test", pref, 3, 5)
        assertEquals(3, entry?.previousValue)
        assertEquals(5, entry?.expectedPostValue)
        assertEquals(5, entry?.readCurrent?.let { kotlinx.coroutines.runBlocking { it() } })
    }

    @Test
    fun `tag preference entry no-ops when Evaluation Mode is disabled`() {
        sourcePreferences.evaluationMode().set(false)
        val repo = exh.util.FakeTasteRepository()
        val getTagTaste = tachiyomi.domain.taste.interactor.GetTagTaste(repo)
        val setTagTaste = tachiyomi.domain.taste.interactor.SetTagTaste(repo)
        val clearTagTaste = tachiyomi.domain.taste.interactor.ClearTagTaste(repo)
        val entry = exh.util.PreferenceUndoRecorder.buildTagPreferenceEntry(
            sourcePreferences,
            getTagTaste,
            setTagTaste,
            clearTagTaste,
            "Action",
            "action",
            null,
            TagPreference.PREFER,
        )
        assertNull(entry)
    }
}
// KMK <--
