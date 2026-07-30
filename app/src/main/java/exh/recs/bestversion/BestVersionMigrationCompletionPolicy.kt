package exh.recs.bestversion

// KMK v0.8.16 -->
/**
 * Decides where the Done button on [BestVersionCompareScreen] should navigate after a migration/copy
 * completes. Kept pure so the fallback behavior (target could not be resolved) is directly testable
 * without a Compose/navigation harness.
 */
object BestVersionMigrationCompletionPolicy {

    sealed interface Destination {
        data class TargetManga(val mangaId: Long) : Destination
        data object Fallback : Destination
    }

    fun resolve(completedTargetMangaId: Long?): Destination =
        if (completedTargetMangaId != null) {
            Destination.TargetManga(completedTargetMangaId)
        } else {
            Destination.Fallback
        }
}
// KMK <--
