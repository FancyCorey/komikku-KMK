package exh.recs.loved

import eu.kanade.domain.source.service.SourcePreferences
import exh.util.FakePreferenceStore
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** HR-16: action placement is a persisted user preference, not transient screen state. */
class RatedMangaActionPlacementPreferenceTest {

    @Test
    fun `selection placement defaults on and survives switching to item menus`() {
        val store = FakePreferenceStore()
        val preferences = SourcePreferences(store)

        assertTrue(preferences.ratedMangaActionsUseSelection().get())
        preferences.ratedMangaActionsUseSelection().set(false)

        assertFalse(SourcePreferences(store).ratedMangaActionsUseSelection().get())
    }
}
