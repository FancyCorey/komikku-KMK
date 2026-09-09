package eu.kanade.domain.track.service

import exh.util.FakePreferenceStore
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrackPreferencesTest {

    @Test
    fun `local progress inheritance is enabled by default and durable`() {
        val preferences = TrackPreferences(FakePreferenceStore())

        assertTrue(preferences.autoInheritLocalProgress().get())

        preferences.autoInheritLocalProgress().set(false)

        assertFalse(preferences.autoInheritLocalProgress().get())
    }
}
