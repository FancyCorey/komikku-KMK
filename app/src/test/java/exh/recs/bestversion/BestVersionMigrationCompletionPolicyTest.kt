package exh.recs.bestversion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK v0.8.16 -->
class BestVersionMigrationCompletionPolicyTest {

    @Test
    fun `resolved target id navigates to that manga`() {
        val result = BestVersionMigrationCompletionPolicy.resolve(completedTargetMangaId = 42L)
        assertEquals(BestVersionMigrationCompletionPolicy.Destination.TargetManga(42L), result)
    }

    @Test
    fun `null target id falls back instead of crashing`() {
        val result = BestVersionMigrationCompletionPolicy.resolve(completedTargetMangaId = null)
        assertEquals(BestVersionMigrationCompletionPolicy.Destination.Fallback, result)
    }
}
// KMK <--
