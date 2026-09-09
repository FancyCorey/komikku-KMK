package exh.recs

import android.content.Context
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.i18n.kmk.KMR
import tachiyomi.core.common.i18n.pluralStringResource as contextPluralStringResource
import tachiyomi.core.common.i18n.stringResource as contextStringResource

// KMK v0.8.19 -->
/**
 * Shared bulk-action feedback model for For You, Loved, Liked, and Disliked selection bars
 * (`BrowsePersonalRecommendationsScreenModel`/`Tab` and `LovedMangaScreenModel`/`RatedMangaScreen`).
 * Both surfaces perform the same small set of taste-related bulk writes on the same underlying
 * interactors (`SetMangaTaste`/`SetMangaTasteBatch`, `ClearMangaTaste`,
 * `SeenRecommendationMangaStore`) -- this file is the single place that turns a raw
 * success/failure count into truthful, action-named, correctly-pluralized user-facing text, so the
 * two screens never drift into different wording for the same underlying action.
 */
enum class BulkTasteActionType {
    RATE_LOVE,
    RATE_LIKE,
    RATE_DISLIKE,
    NOT_INTERESTED,
    CLEAR_RATING,
}

/**
 * Truthful result of a bulk taste-related write. [requestedCount] is how many items were selected;
 * [successCount] + [failureCount] + [skippedCount] must not exceed [requestedCount]. Never infer
 * success from [requestedCount] alone -- callers must build this from the actual per-item or
 * per-batch write result, never assume success ahead of the database call completing.
 */
data class BulkTasteOutcome(
    val requestedCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val skippedCount: Int = 0,
) {
    val allFailed: Boolean get() = successCount == 0 && (failureCount > 0 || skippedCount > 0)
    val partialFailure: Boolean get() = successCount > 0 && (failureCount > 0 || skippedCount > 0)
    val allSuccess: Boolean get() = successCount > 0 && failureCount == 0 && skippedCount == 0
    val isNoOp: Boolean get() = requestedCount == 0 || (successCount == 0 && failureCount == 0 && skippedCount == 0)

    companion object {
        fun success(count: Int) = BulkTasteOutcome(requestedCount = count, successCount = count, failureCount = 0)
        fun failed(count: Int) = BulkTasteOutcome(requestedCount = count, successCount = 0, failureCount = count)
    }
}

private fun Context.ratingLabel(action: BulkTasteActionType): String = when (action) {
    BulkTasteActionType.RATE_LOVE -> contextStringResource(KMR.strings.rated_manga_rating_love)
    BulkTasteActionType.RATE_LIKE -> contextStringResource(KMR.strings.rated_manga_rating_like)
    BulkTasteActionType.RATE_DISLIKE -> contextStringResource(KMR.strings.rated_manga_rating_dislike)
    else -> ""
}

fun bulkTasteActionRatingType(rating: MangaRating): BulkTasteActionType = when (rating) {
    MangaRating.LOVE -> BulkTasteActionType.RATE_LOVE
    MangaRating.LIKE -> BulkTasteActionType.RATE_LIKE
    MangaRating.DISLIKE -> BulkTasteActionType.RATE_DISLIKE
    MangaRating.NOT_INTERESTED -> BulkTasteActionType.NOT_INTERESTED
}

/**
 * Builds the exact user-facing message for [outcome], naming [action] and using correct
 * singular/plural wording. Returns null when [outcome] is a no-op (nothing selected/attempted) --
 * callers must not show a Snackbar/toast in that case.
 */
fun bulkTasteActionMessage(context: Context, action: BulkTasteActionType, outcome: BulkTasteOutcome): String? {
    if (outcome.isNoOp) return null
    val failedOrSkipped = outcome.failureCount + outcome.skippedCount
    return when (action) {
        BulkTasteActionType.RATE_LOVE, BulkTasteActionType.RATE_LIKE, BulkTasteActionType.RATE_DISLIKE -> {
            val label = context.ratingLabel(action)
            when {
                outcome.allFailed -> context.contextPluralStringResource(KMR.plurals.rec_bulk_action_rated_all_failed, count = failedOrSkipped, failedOrSkipped, label)
                outcome.partialFailure -> context.contextStringResource(
                    KMR.strings.rec_bulk_action_rated_partial,
                    context.contextPluralStringResource(
                        KMR.plurals.rec_bulk_action_rated_manga_fragment,
                        count = outcome.successCount,
                        outcome.successCount,
                    ),
                    label,
                    context.contextPluralStringResource(
                        KMR.plurals.rec_bulk_action_failed_item_fragment,
                        count = failedOrSkipped,
                        failedOrSkipped,
                    ),
                )
                else -> context.contextPluralStringResource(KMR.plurals.rec_bulk_action_rated_success, count = outcome.successCount, outcome.successCount, label)
            }
        }
        BulkTasteActionType.NOT_INTERESTED -> when {
            outcome.allFailed -> context.contextPluralStringResource(KMR.plurals.rec_bulk_action_not_interested_all_failed, count = failedOrSkipped, failedOrSkipped)
            outcome.partialFailure -> context.contextStringResource(
                KMR.strings.rec_bulk_action_not_interested_partial,
                context.contextPluralStringResource(
                    KMR.plurals.rec_bulk_action_not_interested_manga_fragment,
                    count = outcome.successCount,
                    outcome.successCount,
                ),
                context.contextPluralStringResource(
                    KMR.plurals.rec_bulk_action_failed_item_fragment,
                    count = failedOrSkipped,
                    failedOrSkipped,
                ),
            )
            else -> context.contextPluralStringResource(KMR.plurals.rec_bulk_action_not_interested_success, count = outcome.successCount, outcome.successCount)
        }
        BulkTasteActionType.CLEAR_RATING -> when {
            outcome.allFailed -> context.contextPluralStringResource(KMR.plurals.rec_bulk_action_clear_rating_all_failed, count = failedOrSkipped, failedOrSkipped)
            outcome.partialFailure -> context.contextStringResource(
                KMR.strings.rec_bulk_action_clear_rating_partial,
                context.contextPluralStringResource(
                    KMR.plurals.rec_bulk_action_cleared_rating_fragment,
                    count = outcome.successCount,
                    outcome.successCount,
                ),
                context.contextPluralStringResource(
                    KMR.plurals.rec_bulk_action_failed_item_fragment,
                    count = failedOrSkipped,
                    failedOrSkipped,
                ),
            )
            outcome.successCount == 1 -> context.contextPluralStringResource(KMR.plurals.rec_bulk_action_clear_rating_success, count = outcome.successCount, outcome.successCount)
            else -> context.contextPluralStringResource(KMR.plurals.rec_bulk_action_clear_rating_success, count = outcome.successCount, outcome.successCount)
        }
    }
}
// KMK <--
