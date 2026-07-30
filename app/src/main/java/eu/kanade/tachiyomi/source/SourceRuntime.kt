package eu.kanade.tachiyomi.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        // KMK v0.8.10-fix8: enforce suppression before ever touching the source again -- previously
        // SourceRuntimeFailureRegistry.isTemporarilyUnavailable() was advisory only (callers had to
        // remember to check it themselves), so a source that had just thrown a lazy-init LinkageError
        // could still be re-invoked by any call site that didn't opt in, re-triggering the same crash
        // risk. This check is unconditional for every run()/runBlockingSourceCall() caller now.
        suppressionFailureOrNull(source)?.let { return Result.failure(it) }
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
        // KMK v0.8.10-fix8: same enforced suppression as run() above.
        suppressionFailureOrNull(source)?.let { return Result.failure(it) }
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

    /**
     * Returns a [SourceTemporarilyUnavailableException] (never touching [source]) when
     * [SourceRuntimeFailureRegistry.isTemporarilyUnavailable] is true for [source], or `null` when the
     * caller should proceed normally. Shared by [run] and [runBlockingSourceCall] so both enforce the
     * same suppression window identically.
     */
    fun suppressionFailureOrNull(source: Source): SourceTemporarilyUnavailableException? {
        if (!SourceRuntimeFailureRegistry.isTemporarilyUnavailable(source.id)) return null
        val entry = SourceRuntimeFailureRegistry.get(source.id)
        return SourceTemporarilyUnavailableException(
            sourceId = source.id,
            sourceName = source.name,
            sourceLang = source.lang,
            lastOperation = entry?.operation,
            lastKind = entry?.kind,
        )
    }
}

// KMK v0.8.10-fix8 -->
/**
 * Thrown (as a [Result.failure], never actually thrown across a call boundary) when
 * [SourceRuntime.run]/[SourceRuntime.runBlockingSourceCall] refuses to touch [sourceId] again because
 * it recently produced a recoverable failure ([SourceRuntimeFailureRegistry.isTemporarilyUnavailable]).
 * An ordinary [Exception] -- never [CancellationException], never a fatal [Error] -- so every existing
 * `catch (e: Exception)` path and [getOrThrowSourceRuntimeException] caller already handle it
 * correctly as a non-fatal per-source failure.
 *
 * This is process-lifetime suppression only, exactly like the registry it reads from -- it does not
 * persist across process restarts and does not disable/uninstall/block the extension. A user-initiated
 * retry remains possible via [SourceRuntimeFailureRegistry.clear]/`clearAll` (already exposed through
 * Source Evaluation's runtime-health "Retry now" action, added in v0.8.10-fix5).
 */
class SourceTemporarilyUnavailableException(
    val sourceId: Long,
    val sourceName: String,
    val sourceLang: String,
    val lastOperation: SourceRuntimeOperation?,
    val lastKind: SourceRuntimeFailureKind?,
) : Exception(
    "Source \"$sourceName\" ($sourceLang) is temporarily unavailable after a recent recoverable " +
        "failure" + (lastOperation?.let { " ($it)" } ?: ""),
)
// KMK <--

// KMK v0.8.10-fix9 -->
/**
 * Rethrows [e] unchanged if it must never be swallowed by a broad `catch (e: Throwable)` block:
 * [CancellationException] (structured concurrency must never be masked), or any genuinely fatal
 * [Error] per [isRecoverableSourceRuntimeFailure] (unwrapped first via [unwrapSourceRuntimeCause], so
 * a fatal error hidden behind [java.util.concurrent.ExecutionException]/`CompletionException`/
 * `InvocationTargetException` is still caught). Returns normally (does not rethrow) for an ordinary
 * [Exception] or a recoverable [LinkageError] -- the caller should isolate those per-item, exactly as
 * before.
 *
 * Intended for app-layer call sites that isolate per-item failures across a batch (migration, library
 * update, metadata update) but were catching every [Throwable] without distinguishing fatal VM/system
 * conditions from recoverable per-item failures. Reuses the same classification
 * [SourceRuntime.run]/[SourceRuntime.runBlockingSourceCall] use internally -- this is not a second,
 * competing classifier.
 */
fun rethrowIfFatal(e: Throwable) {
    if (e is CancellationException) throw e
    if (!e.unwrapSourceRuntimeCause().isRecoverableSourceRuntimeFailure()) throw e
}
// KMK <--

/** The source-facing operation family being guarded — used for diagnostics and per-operation policy. */
enum class SourceRuntimeOperation {
    FilterList,
    Popular,
    Latest,
    Search,
    MangaUpdate,
    PageList,
    ImageUrl,
    Image,
    RelatedManga,
    // KMK v0.8.10-fix5: lazy source-owned properties/image-fetch paths (client/headers construction,
    // cover/preview requests) are a separate operation family from the method calls above — they can
    // throw LinkageError while rendering covers/previews, not only while explicitly using a source.
    Client,
    Headers,
    CoverImage,
    PreviewImage,
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

// KMK v0.8.10-fix3: isRecoverableSourceRuntimeFailure() and unwrapSourceRuntimeCause() moved to
// core:common's eu.kanade.tachiyomi.source.SourceRuntimeClassifier.kt (same package, different
// module) so data-module call sites (e.g. tachiyomi.data.source.SourcePagingSource, which `app`
// depends on but which cannot depend back on `app`) can share this exact classification logic
// instead of duplicating it. No import needed here since both files share this package. See that
// file's KDoc for the full module-boundary reasoning.

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

// KMK v0.8.10-fix7 -->
/**
 * Bridges a recoverable [SourceRuntime]-classified source failure (which may be a raw
 * [LinkageError] — an [Error], not an [Exception]) into an ordinary [Exception] for call sites that
 * need exception-style flow control (a UI row, a source wrapper, a background job) and would
 * otherwise crash on an uncaught [Error] from a `catch (e: Exception)`-only path.
 *
 * [SourceRuntime.run]/[SourceRuntime.runBlockingSourceCall] already classify and record the failure
 * in [SourceRuntimeFailureRegistry] — this bridge only changes what type the *caller* sees when it
 * chooses to rethrow via [Result.getOrThrow]-style flow instead of [Result.fold]. It does not replace
 * `SourceRuntime.run(...).fold(...)` for code that can naturally handle a [Result] directly.
 */
class RecoverableSourceRuntimeException(
    val sourceRuntimeFailure: Throwable,
) : Exception(sourceRuntimeFailure.message, sourceRuntimeFailure)

fun Throwable.asRecoverableSourceRuntimeException(): RecoverableSourceRuntimeException {
    val unwrapped = unwrapSourceRuntimeCause()
    return if (unwrapped is RecoverableSourceRuntimeException) {
        unwrapped
    } else {
        RecoverableSourceRuntimeException(unwrapped)
    }
}

/**
 * Like [Result.getOrThrow], but a recoverable source failure (as classified by
 * [isRecoverableSourceRuntimeFailure]) is thrown as [RecoverableSourceRuntimeException] (an
 * [Exception]) instead of the raw [Throwable] stored in the [Result] — which may be a [LinkageError]
 * that would otherwise crash any `catch (e: Exception)`-only path. Non-recoverable fatal errors and
 * [CancellationException] are rethrown unchanged, exactly as [Result.getOrThrow] would.
 *
 * KMK v0.8.10-fix7 correction: [isRecoverableSourceRuntimeFailure] classifies *any* non-[Error]
 * [Throwable] as recoverable — including [CancellationException], since it is itself an [Exception]
 * subtype, not an [Error]. Without an explicit check here first, a [Result.failure] carrying a
 * [CancellationException] would be wrongly wrapped into [RecoverableSourceRuntimeException], breaking
 * structured concurrency (the exact type must propagate unchanged for cancellation to work). The
 * fix7 plan's own required test ("does not wrap CancellationException") depends on this guard.
 */
fun <T> Result<T>.getOrThrowSourceRuntimeException(): T {
    return getOrElse { throwable ->
        if (throwable is CancellationException) throw throwable
        val unwrapped = throwable.unwrapSourceRuntimeCause()
        if (unwrapped is CancellationException) throw unwrapped
        if (unwrapped.isRecoverableSourceRuntimeFailure()) {
            throw unwrapped.asRecoverableSourceRuntimeException()
        }
        throw throwable
    }
}
// KMK <--
