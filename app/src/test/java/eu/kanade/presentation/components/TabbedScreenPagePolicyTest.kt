package eu.kanade.presentation.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TabbedScreenPagePolicyTest {

    @Test
    fun `restored page is clamped when settings reduce the tab list`() {
        assertEquals(2, clampTabbedScreenPage(currentPage = 4, tabCount = 3))
    }

    @Test
    fun `valid page remains unchanged`() {
        assertEquals(1, clampTabbedScreenPage(currentPage = 1, tabCount = 3))
    }

    @Test
    fun `negative page is clamped to the first tab`() {
        assertEquals(0, clampTabbedScreenPage(currentPage = -1, tabCount = 3))
    }

    @Test
    fun `empty tab list has a safe zero page`() {
        assertEquals(0, clampTabbedScreenPage(currentPage = 4, tabCount = 0))
    }
}
