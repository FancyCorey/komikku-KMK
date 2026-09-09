package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlobalSearchSelectionPolicyTest {
    @Test
    fun `alternate-source return never auto-selects a sole non-exact result`() {
        assertFalse(GlobalSearchSelectionPolicy.shouldAutoSelect(returnSelection = true, resultCount = 1))
    }

    @Test
    fun `ordinary extension-filtered global search keeps its single-result shortcut`() {
        assertTrue(GlobalSearchSelectionPolicy.shouldAutoSelect(returnSelection = false, resultCount = 1))
    }

    @Test
    fun `multiple results always remain explicit`() {
        assertFalse(GlobalSearchSelectionPolicy.shouldAutoSelect(returnSelection = false, resultCount = 2))
    }
}
