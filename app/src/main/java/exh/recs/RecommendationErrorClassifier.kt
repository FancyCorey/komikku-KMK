package exh.recs

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.isRecoverableSourceRuntimeFailure
import eu.kanade.tachiyomi.source.unwrapSourceRuntimeCause
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
    // KMK v0.8.10-fix2: a broken/incompletely-packaged extension throwing a LinkageError
    // (NoClassDefFoundError, NoSuchMethodError, IncompatibleClassChangeError, etc.) while
    // constructing its HTTP client or otherwise executing. Recoverable as a per-source failure —
    // never the whole recommendation load.
    ExtensionIncompatible("REC_ERROR_EXTENSION_INCOMPATIBLE"),
    Internal("REC_ERROR_INTERNAL"),
    // KMK v0.8.21-fix2: distinguished from Internal per the reopened AUG-05/AUG-09 investigation --
    // eu.kanade.tachiyomi.network.HttpException carries a real HTTP status code that was previously
    // discarded into the generic Internal bucket, giving the user no truthful reason for a source
    // failure (confirmed live: a genuine HTTP 502 from a real source was showing as unclassified
    // "Something went wrong"). RateLimit and Authentication are the two status-derived categories
    // named in that investigation's required taxonomy; other HTTP statuses remain Internal rather
    // than inventing categories the investigation did not ask for.
    RateLimit("REC_ERROR_RATE_LIMIT"),
    Authentication("REC_ERROR_AUTHENTICATION"),
    // KMK v0.8.21-fix4: R2/AUG-05 source-review correction -- the live-observed failure was a real
    // HTTP 502 from the source's own server, which the prior fix2 taxonomy deliberately left folded
    // into Internal ("other HTTP statuses remain Internal rather than inventing categories the
    // investigation did not ask for"). The corrective source review specifically named 502 as the
    // reproduced failure and required truthful classification, so this adds the one HTTP-status-derived
    // category that was still missing: any 5xx status (the source's server reporting its own failure,
    // as opposed to a 4xx client-side rejection). Distinct from Internal (an unclassified app-side
    // failure) and from Network/Timeout (no response was ever received at all).
    ServerError("REC_ERROR_SERVER"),
    ;

    companion object {
        fun fromStorageKey(key: String?): RecommendationErrorKind? = entries.find { it.storageKey == key }
    }
}

object RecommendationErrorClassifier {
    fun classify(e: Throwable): RecommendationErrorKind = when {
        e is CancellationException -> RecommendationErrorKind.Cancelled
        // KMK v0.8.10-fix3: unwrap first, same as SourceRuntime, in case a linkage failure arrived
        // wrapped in ExecutionException/CompletionException/InvocationTargetException.
        e.unwrapSourceRuntimeCause() is LinkageError -> RecommendationErrorKind.ExtensionIncompatible
        e is UnknownHostException -> RecommendationErrorKind.Network
        e is SocketTimeoutException -> RecommendationErrorKind.Timeout
        e is FileNotFoundException -> RecommendationErrorKind.FileAccess
        // KMK v0.8.21-fix2: HttpException does not extend IOException in this codebase (it extends
        // IllegalStateException, see core/common HttpException.kt), so it was previously falling all
        // the way through to Internal regardless of its carried status code. Checked before the
        // generic IOException branch since HttpException is the more specific, more informative type.
        e is HttpException && e.code == 429 -> RecommendationErrorKind.RateLimit
        e is HttpException && (e.code == 401 || e.code == 403) -> RecommendationErrorKind.Authentication
        // KMK v0.8.21-fix4: checked before the generic IOException branch, same reasoning as the
        // 429/401/403 checks above -- a 5xx is the source's own server reporting failure, not a
        // connectivity problem, so it should never share a bucket with UnknownHostException/generic
        // IOException even though HttpException doesn't extend IOException in this codebase anyway.
        e is HttpException && e.code in 500..599 -> RecommendationErrorKind.ServerError
        e is IOException -> RecommendationErrorKind.Network
        else -> RecommendationErrorKind.Internal
    }

    /** Stable storage key — used in place of a raw exception message in UI-facing state fields. */
    fun classifyToStorageKey(e: Throwable): String = classify(e).storageKey

    // KMK v0.8.10-fix2 -->
    /**
     * Confirmed root cause (v0.8.10 fix2): a broken or incompletely-packaged extension can throw a
     * [LinkageError] (observed in practice: `NoClassDefFoundError: okhttp3.zstd.Zstd` while the
     * installed Asura Scans extension constructs its HTTP client) partway through a per-source
     * recommendation request. [LinkageError] is an [Error], not an [Exception] — the per-source
     * request loops in [exh.recs.RecommendsScreenModel] and
     * [exh.recs.BrowsePersonalRecommendationsScreenModel] previously either rethrew every [Error]
     * unconditionally, or (for the plain `catch (e: Exception)` request loop) never caught [Error] at
     * all — so one incompatible extension crashed the entire For You screen / recommendation page
     * instead of becoming a single failed source row.
     *
     * True for any [Exception] (never fatal to the process) and for [LinkageError] specifically
     * (a load-time/class-resolution failure that is safely recoverable — the request that triggered
     * it simply fails, nothing else on the JVM is corrupted). False for genuinely fatal VM
     * conditions — [OutOfMemoryError], [StackOverflowError], and any other [Error] that is not a
     * [LinkageError] — which must always propagate uncaught, exactly as before this fix.
     *
     * KMK v0.8.10-fix3: delegates to the shared, app-wide
     * [eu.kanade.tachiyomi.source.isRecoverableSourceRuntimeFailure] rather than duplicating the
     * decision — recommendation screens must never classify a linkage failure differently than
     * Browse, global search, or Source Evaluation. Unwraps first so a wrapped linkage failure
     * (`ExecutionException`/`CompletionException`/`InvocationTargetException`) is classified
     * correctly too.
     */
    fun isRecoverableSourceFailure(e: Throwable): Boolean = e.unwrapSourceRuntimeCause().isRecoverableSourceRuntimeFailure()
    // KMK <--
}

// KMK v0.8.21-fix4 -->
/**
 * True for a failure kind where an identical second attempt against the same source could
 * plausibly succeed -- a transient network hiccup, timeout, rate limit, or the source's own
 * server reporting a 5xx. False for a terminal condition (authentication, file access, an
 * incompatible extension, or an unclassified internal failure) that repeating the exact same
 * request cannot change. Used by [exh.recs.matching.SameMangaCandidateSearcher] to bound its
 * same-source discovery retry to failures actually worth retrying.
 */
fun RecommendationErrorKind.isRetryableForDiscovery(): Boolean = when (this) {
    RecommendationErrorKind.Network,
    RecommendationErrorKind.Timeout,
    RecommendationErrorKind.RateLimit,
    RecommendationErrorKind.ServerError,
    -> true
    RecommendationErrorKind.Cancelled,
    RecommendationErrorKind.FileAccess,
    RecommendationErrorKind.ExtensionIncompatible,
    RecommendationErrorKind.Internal,
    RecommendationErrorKind.Authentication,
    -> false
}
// KMK <--

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
    RecommendationErrorKind.ExtensionIncompatible -> KMR.strings.rec_error_extension_incompatible
    RecommendationErrorKind.Internal -> KMR.strings.rec_error_internal
    RecommendationErrorKind.RateLimit -> KMR.strings.rec_error_rate_limited
    RecommendationErrorKind.Authentication -> KMR.strings.rec_error_authentication
    RecommendationErrorKind.ServerError -> KMR.strings.rec_error_server
}
// KMK <--
