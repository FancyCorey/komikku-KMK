package exh.recs.evaluation

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

// KMK --> v0.7.45: public-readiness Phase 4 — classify per-source probe exceptions into a stable,
// storage-safe key instead of persisting the raw exception message. `SourceEvaluation.errorMessage`
// is shown to the user (EvaluationResultRow's row subtitle), so a caught exception's `.message` —
// which can be an arbitrary string from an extension/network library, not something written for
// end users — must not flow into it directly. The full exception is still logged via `logcat` at
// the catch site for diagnostics; only the DB/UI-facing value is classified here.
internal enum class SourceEvaluationProbeErrorKind(val storageKey: String) {
    NETWORK_UNAVAILABLE("NETWORK_UNAVAILABLE"),
    TIMEOUT("TIMEOUT"),
    UNSUPPORTED("UNSUPPORTED"),
    INTERNAL("INTERNAL"),
    ;

    companion object {
        fun fromStorageKey(key: String?): SourceEvaluationProbeErrorKind? =
            entries.find { it.storageKey == key }
    }
}

internal object SourceEvaluationProbeErrorClassifier {
    fun classify(e: Throwable): SourceEvaluationProbeErrorKind = when {
        e is UnknownHostException -> SourceEvaluationProbeErrorKind.NETWORK_UNAVAILABLE
        e is SocketTimeoutException -> SourceEvaluationProbeErrorKind.TIMEOUT
        e is UnsupportedOperationException -> SourceEvaluationProbeErrorKind.UNSUPPORTED
        e is IOException -> SourceEvaluationProbeErrorKind.NETWORK_UNAVAILABLE
        else -> SourceEvaluationProbeErrorKind.INTERNAL
    }

    /** Stable storage key to persist in place of the exception's raw, unpredictable message. */
    fun classifyToStorageKey(e: Throwable): String = classify(e).storageKey
}
// KMK <--
