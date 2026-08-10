package exh.util

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

// KMK Universal Action History Recovery Plan 2026-08-01 -->
/**
 * Decides whether a safe "Re-download" follow-up can be offered for a [DownloadReceipt] -- kept pure
 * and free of any UI/Context/interactor dependency so the decision is directly unit-testable,
 * mirroring [MigrationFollowUpPolicy]'s own contract.
 *
 * A follow-up is only [Offered] when the manga row still exists, its source is still installed, and at
 * least one of the originally-deleted chapter ids still resolves to a real chapter row (chapters are
 * never deleted from the database by a download delete -- only their downloaded files are -- but a
 * chapter could since have been removed from the database through an unrelated path, e.g. a library
 * update that dropped it from the source's chapter list).
 */
object DownloadFollowUpPolicy {

    sealed interface RedownloadFollowUp {
        data class Offered(val resolvedChapters: List<Chapter>) : RedownloadFollowUp
        data class Unavailable(val reason: Reason) : RedownloadFollowUp

        enum class Reason {
            /** The manga row no longer exists (deleted from the local database). */
            MANGA_NOT_FOUND,

            /** The manga's source is not currently installed. */
            SOURCE_NOT_INSTALLED,

            /** None of the originally-deleted chapter ids still resolve to a real chapter row. */
            NO_CHAPTERS_STILL_EXIST,
        }
    }

    fun evaluate(
        manga: Manga?,
        sourceInstalled: Boolean,
        resolvedChapters: List<Chapter>,
    ): RedownloadFollowUp {
        if (manga == null) {
            return RedownloadFollowUp.Unavailable(RedownloadFollowUp.Reason.MANGA_NOT_FOUND)
        }
        if (!sourceInstalled) {
            return RedownloadFollowUp.Unavailable(RedownloadFollowUp.Reason.SOURCE_NOT_INSTALLED)
        }
        if (resolvedChapters.isEmpty()) {
            return RedownloadFollowUp.Unavailable(RedownloadFollowUp.Reason.NO_CHAPTERS_STILL_EXIST)
        }
        return RedownloadFollowUp.Offered(resolvedChapters)
    }
}
// KMK <--
