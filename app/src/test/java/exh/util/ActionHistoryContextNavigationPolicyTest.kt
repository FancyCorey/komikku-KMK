package exh.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ActionHistoryContextNavigationPolicyTest {
    @Test
    fun `positive journal-owned identity opens manga context`() {
        assertEquals(42L, ActionHistoryContextNavigationPolicy.targetMangaId(42L))
    }

    @Test
    fun `missing or invalid identity has no navigation target`() {
        assertNull(ActionHistoryContextNavigationPolicy.targetMangaId(null))
        assertNull(ActionHistoryContextNavigationPolicy.targetMangaId(0L))
        assertNull(ActionHistoryContextNavigationPolicy.targetMangaId(-1L))
    }

    @Test
    fun `history screen keeps navigator back behavior and delegates row intent`() {
        val source = File("src/main/java/exh/util/EvaluationModeActionHistoryScreen.kt").readText()

        assertTrue(source.contains("navigateUp = navigator::pop"))
        assertTrue(source.contains("ActionHistoryRowIntentPolicy.resolve("))
        assertTrue(source.contains("MangaScreen(intent.mangaId, intent.fromSource)"))
    }
}
