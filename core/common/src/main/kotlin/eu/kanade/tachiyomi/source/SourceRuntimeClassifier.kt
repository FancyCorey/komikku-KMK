package eu.kanade.tachiyomi.source

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

// KMK v0.8.10-fix3 -->
/*
 * Pure, module-agnostic classification of extension source-runtime failures.
 *
 * Lives in `core:common` (not `app`, where the original `SourceRuntime.kt` lives) specifically so
 * that call sites in the `data` module -- e.g. `tachiyomi.data.source.SourcePagingSource`, which
 * `app` depends on but which cannot depend back on `app` -- can share this exact classification
 * logic instead of duplicating it. `app`'s `eu.kanade.tachiyomi.source.SourceRuntime` (the
 * Android/coroutine-facing execution helper, `SourceRuntimeFailureRegistry`, and the
 * domain-model-aware `toSourceRuntimeFailureKind()` UI/diagnostics categorizer, which needs
 * `tachiyomi.domain.source.model.SourceNotInstalledException` and therefore cannot live here without
 * adding a `domain` dependency to `core:common`) both build on top of these same two functions
 * rather than reimplementing them.
 *
 * Confirmed root cause this exists for: a broken/incompletely-packaged extension (the installed
 * Asura Scans extension, referencing `okhttp3.zstd.Zstd`, which is absent from the extension APK)
 * throws a LinkageError the first time one of its methods lazily touches the missing dependency
 * (e.g. while constructing its HTTP client) -- not at class-load time, so the failure surfaces from
 * many independent call sites across the app, not only one screen.
 */

/**
 * True when [this] is a recoverable source-scoped failure: any ordinary [Exception], or a
 * [LinkageError] (covers [NoClassDefFoundError], [NoSuchMethodError], [NoSuchFieldError],
 * [IncompatibleClassChangeError], [ExceptionInInitializerError], and any other [LinkageError]
 * subtype -- all recoverable, since the request that triggered them simply fails; nothing else on
 * the JVM is corrupted). False for `kotlinx.coroutines.CancellationException` handled separately by
 * callers before this check is reached) and for any other [Error] -- [OutOfMemoryError],
 * [StackOverflowError], [ThreadDeath], [AssertionError], and any unclassified [Error] -- which must
 * always propagate uncaught.
 */
fun Throwable.isRecoverableSourceRuntimeFailure(): Boolean = this !is Error || this is LinkageError

/**
 * Unwraps a failure that may have been wrapped by concurrency/reflection bridging code
 * ([ExecutionException], [CompletionException], [InvocationTargetException]) so the real underlying
 * cause (e.g. a [LinkageError]) is what gets classified, not the wrapper.
 */
fun Throwable.unwrapSourceRuntimeCause(): Throwable {
    var current: Throwable = this
    while (
        (current is ExecutionException || current is CompletionException || current is InvocationTargetException) &&
        current.cause != null
    ) {
        current = current.cause!!
    }
    return current
}
// KMK <--
