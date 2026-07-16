package exh.recs

import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.CancellationException
import tachiyomi.i18n.kmk.KMR
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

// KMK --> v0.7.46: shared, pure error classifier for non-OCR KMK flows (Best Version comparison,
// recommendation bundle import) that previously stored a raw caught exception's `.message` directly
// in UI-facing state. Deliberately reuses the same shape as
// `exh.recs.evaluation.SourceEvaluationProbeErrorClassifier` and `exh.ocr.OcrErrorClassifier` rather
// than inventing a fourth taxonomy — see those two for the established pattern in this codebase.
enum class RecommendationErrorKind(val storageKey: String) {
    Network("REC_ERROR_NETWORK"),
    Timeout("REC_ERROR_TIMEOUT"),
    Cancelled("REC_ERROR_CANCELLED"),
    FileAccess("REC_ERROR_FILE_ACCESS"),
    Internal("REC_ERROR_INTERNAL"),
    ;

    companion object {
        fun fromStorageKey(key: String?): RecommendationErrorKind? = entries.find { it.storageKey == key }
    }
}

object RecommendationErrorClassifier {
    fun classify(e: Throwable): RecommendationErrorKind = when {
        e is CancellationException -> RecommendationErrorKind.Cancelled
        e is UnknownHostException -> RecommendationErrorKind.Network
        e is SocketTimeoutException -> RecommendationErrorKind.Timeout
        e is FileNotFoundException -> RecommendationErrorKind.FileAccess
        e is IOException -> RecommendationErrorKind.Network
        else -> RecommendationErrorKind.Internal
    }

    /** Stable storage key — used in place of a raw exception message in UI-facing state fields. */
    fun classifyToStorageKey(e: Throwable): String = classify(e).storageKey
}

/**
 * Non-Composable KMR string lookup for [RecommendationErrorKind], for call sites that have a
 * [android.content.Context] but are not themselves `@Composable` (e.g. [exh.recs.share.RecommendationBundleImporter]).
 * Composable call sites should prefer resolving [RecommendationErrorKind] directly with `stringResource(...)`.
 */
fun recommendationErrorMessageRes(kind: RecommendationErrorKind): StringResource = when (kind) {
    RecommendationErrorKind.Network -> KMR.strings.rec_error_network
    RecommendationErrorKind.Timeout -> KMR.strings.rec_error_timeout
    RecommendationErrorKind.Cancelled -> KMR.strings.rec_error_cancelled
    RecommendationErrorKind.FileAccess -> KMR.strings.rec_error_file_access
    RecommendationErrorKind.Internal -> KMR.strings.rec_error_internal
}
// KMK <--
