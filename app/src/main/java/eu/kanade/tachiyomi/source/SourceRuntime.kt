package eu.kanade.tachiyomi.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

// KMK v0.8.10-fix3 -->
/**
 * Shared, app-layer runtime boundary for executing methods on a loaded extension [Source].
 *
 * ## Why this exists
 *
 * Official Komikku's [eu.kanade.tachiyomi.extension.util.ExtensionLoader] already guards
 * *construction* of a source class: it catches `Throwable`, and a source that fails to construct
 * never becomes a usable [Source] (`LoadResult.Error`). What official Komikku does not centrally
 * guard is *later* method calls on an already-successfully-constructed source — [AndroidSourceManager]
 * just stores and returns the raw [Source] instance; nothing wraps `getPopularManga()`,
 * `getSearchManga()`, `getPageList()`, etc.
 *
 * A source can construct successfully and still throw a [LinkageError] the first time one of its
 * methods lazily touches a missing runtime dependency — confirmed in practice: the installed Asura
 * Scans extension references `okhttp3.zstd.Zstd`, which is absent from the extension APK, and throws
 * `NoClassDefFoundError` the first time it constructs its HTTP client (inside `getPopularManga()`/
 * `getSearchManga()`/etc., not at class-load time). [LinkageError] is a [java.lang.Error], not an
 * [Exception] — every call site that only wrote `catch (e: Exception)` let it escape uncaught.
 *
 * v0.8.10-fix2 patched exactly two recommendation call sites with a local, recommendation-specific
 * classifier ([exh.recs.RecommendationErrorClassifier]). That was containment, not the structural
 * fix: any of the many other independent call sites across Browse, global search, feeds, manga
 * detail, reader, downloads, library update, bulk favorite, and every KMK bulk/global recommendation
 * flow can invoke the same broken source and crash the same way. This file is the one shared runtime
 * boundary all of those call sites should route through instead of each inventing their own
 * `catch`.
 *
 * ## Contract
 *
 * - [CancellationException] is always rethrown — structured concurrency must never be masked.
 * - Genuinely fatal VM/system conditions ([OutOfMemoryError], [StackOverflowError], [ThreadDeath],
 *   [AssertionError], and any other [Error] that is not a [LinkageError]) are always rethrown.
 * - [LinkageError] (and all its common subtypes — [NoClassDefFoundError], [NoSuchMethodError],
 *   [NoSuchFieldError], [IncompatibleClassChangeError], [ExceptionInInitializerError], or any other
 *   generic [LinkageError]) is treated as a recoverable, source-scoped failure.
 * - A failure wrapped in [ExecutionException], [CompletionException], or [InvocationTargetException]
 *   is unwrapped (via its `cause`) before classification, since some source/coroutine bridging code
 *   can wrap the real linkage failure in one of these.
 * - Ordinary [Exception]s are always recoverable, exactly as before this fix — this boundary changes
 *   nothing about normal network/IO/timeout error handling.
 */
object SourceRuntime {

    /**
     * Runs [block] against [source] on [dispatcher], classifying any failure. Returns
     * [Result.success] on success, or [Result.failure] with the (possibly unwrapped) throwable when
     * the failure is recoverable. A non-recoverable failure ([CancellationException] or a genuinely
     * fatal error) is rethrown, not returned as a [Result].
     */
    suspend inline fun <T> run(
        source: Source,
        operation: SourceRuntimeOperation,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        crossinline block: suspend Source.() -> T,
    ): Result<T> {
        return try {
            Result.success(withContext(dispatcher) { source.block() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val unwrapped = e.unwrapSourceRuntimeCause()
            if (!unwrapped.isRecoverableSourceRuntimeFailure()) throw e
            SourceRuntimeFailureRegistry.record(
                SourceRuntimeFailure(
                    sourceId = source.id,
                    sourceName = source.name,
                    sourceLang = source.lang,
                    operation = operation,
                    kind = unwrapped.toSourceRuntimeFailureKind(),
                    throwable = unwrapped,
                ),
            )
            Result.failure(unwrapped)
        }
    }

    /**
     * Synchronous variant for call sites that are not `suspend` (or are already deliberately
     * controlling their own threading) and cannot use [run]. Does not switch dispatcher.
     */
    inline fun <T> runBlockingSourceCall(
        source: Source,
        operation: SourceRuntimeOperation,
        block: Source.() -> T,
    ): Result<T> {
        return try {
            Result.success(source.block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val unwrapped = e.unwrapSourceRuntimeCause()
            if (!unwrapped.isRecoverableSourceRuntimeFailure()) throw e
            SourceRuntimeFailureRegistry.record(
                SourceRuntimeFailure(
                    sourceId = source.id,
                    sourceName = source.name,
                    sourceLang = source.lang,
                    operation = operation,
                    kind = unwrapped.toSourceRuntimeFailureKind(),
                    throwable = unwrapped,
                ),
            )
            Result.failure(unwrapped)
        }
    }
}

/** The source-facing operation family being guarded — used for diagnostics and per-operation policy. */
enum class SourceRuntimeOperation {
    FilterList,
    Popular,
    Latest,
    Search,
    MangaUpdate,
    PageList,
    ImageUrl,
    RelatedManga,
}

/** Classification of a recoverable [SourceRuntimeFailure] for diagnostics/UI. */
sealed class SourceRuntimeFailureKind {
    data object ExtensionIncompatible : SourceRuntimeFailureKind()
    data object Network : SourceRuntimeFailureKind()
    data object Timeout : SourceRuntimeFailureKind()
    data object SourceNotInstalled : SourceRuntimeFailureKind()
    data object Unsupported : SourceRuntimeFailureKind()
    data object Internal : SourceRuntimeFailureKind()
}

/** A single recorded recoverable failure, with enough source identity for diagnostics/UI/registry. */
data class SourceRuntimeFailure(
    val sourceId: Long,
    val sourceName: String,
    val sourceLang: String,
    val operation: SourceRuntimeOperation,
    val kind: SourceRuntimeFailureKind,
    val throwable: Throwable,
)

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

/**
 * True when [this] (already unwrapped via [unwrapSourceRuntimeCause] by callers that need it) is a
 * recoverable source-scoped failure: any ordinary [Exception], or a [LinkageError] (covers
 * [NoClassDefFoundError], [NoSuchMethodError], [NoSuchFieldError], [IncompatibleClassChangeError],
 * [ExceptionInInitializerError], and any other [LinkageError] subtype — all recoverable, since the
 * request that triggered them simply fails; nothing else on the JVM is corrupted). False for
 * [CancellationException] (handled separately by callers, never reaching this check) and for any
 * other [Error] — [OutOfMemoryError], [StackOverflowError], [ThreadDeath], [AssertionError], and any
 * unclassified [Error] — which must always propagate uncaught.
 */
fun Throwable.isRecoverableSourceRuntimeFailure(): Boolean = this !is Error || this is LinkageError

/** Maps a recoverable failure to a [SourceRuntimeFailureKind] for diagnostics/UI. */
fun Throwable.toSourceRuntimeFailureKind(): SourceRuntimeFailureKind = when (this) {
    is LinkageError -> SourceRuntimeFailureKind.ExtensionIncompatible
    is java.net.UnknownHostException -> SourceRuntimeFailureKind.Network
    is java.net.SocketTimeoutException -> SourceRuntimeFailureKind.Timeout
    is java.io.IOException -> SourceRuntimeFailureKind.Network
    is tachiyomi.domain.source.model.SourceNotInstalledException -> SourceRuntimeFailureKind.SourceNotInstalled
    is UnsupportedOperationException -> SourceRuntimeFailureKind.Unsupported
    else -> SourceRuntimeFailureKind.Internal
}
// KMK <--
