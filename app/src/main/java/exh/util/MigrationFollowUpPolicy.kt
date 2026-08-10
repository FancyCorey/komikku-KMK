package exh.util

import tachiyomi.domain.manga.model.Manga

// KMK Universal Action History Recovery Plan 2026-07-31 -->
/**
 * Decides whether a safe "Migrate back" follow-up can be offered for a [MigrationReceipt] -- kept
 * pure and free of any UI/Context/interactor dependency so the decision is directly unit-testable,
 * mirroring [PackageOperationFollowUpPolicy]'s own contract for extension install/uninstall
 * follow-ups.
 *
 * A follow-up is only [Offered] when the origin manga row still exists, its source is still
 * installed, the target manga row (the live migrated-to entry) still exists, and the origin has not
 * already become a live library entry through some other path since the migration -- that last check
 * is the conflict rule: if the origin is already favorited again, something else already changed its
 * state (e.g. the user manually re-added it, or ran their own reverse migration), and migrating back
 * automatically here could silently clobber that newer, independent change.
 */
object MigrationFollowUpPolicy {

    sealed interface MigrateBackFollowUp {
        data object Offered : MigrateBackFollowUp
        data class Unavailable(val reason: Reason) : MigrateBackFollowUp

        enum class Reason {
            /** The origin manga row no longer exists (deleted from the local database). */
            ORIGIN_MANGA_NOT_FOUND,

            /** The origin manga's source is not currently installed. */
            ORIGIN_SOURCE_NOT_INSTALLED,

            /** The target manga row (the live migrated-to entry) no longer exists. */
            TARGET_MANGA_NOT_FOUND,

            /**
             * The origin manga is already a favorite -- something else already changed its state
             * since the migration (a manual re-add, an independent reverse migration, etc.); migrating
             * back now could overwrite that newer, independent change.
             */
            ORIGIN_ALREADY_FAVORITE,
        }
    }

    fun evaluate(
        originManga: Manga?,
        originSourceInstalled: Boolean,
        targetManga: Manga?,
    ): MigrateBackFollowUp {
        if (originManga == null) {
            return MigrateBackFollowUp.Unavailable(MigrateBackFollowUp.Reason.ORIGIN_MANGA_NOT_FOUND)
        }
        if (!originSourceInstalled) {
            return MigrateBackFollowUp.Unavailable(MigrateBackFollowUp.Reason.ORIGIN_SOURCE_NOT_INSTALLED)
        }
        if (targetManga == null) {
            return MigrateBackFollowUp.Unavailable(MigrateBackFollowUp.Reason.TARGET_MANGA_NOT_FOUND)
        }
        if (originManga.favorite) {
            return MigrateBackFollowUp.Unavailable(MigrateBackFollowUp.Reason.ORIGIN_ALREADY_FAVORITE)
        }
        return MigrateBackFollowUp.Offered
    }
}
// KMK <--
