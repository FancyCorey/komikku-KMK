package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Call
import okhttp3.Headers

// KMK v0.8.10-fix5 -->
/**
 * Safe accessors for [HttpSource]'s lazy-initialized `client`/`headers` properties.
 *
 * ## Why this exists
 *
 * [SourceRuntime] already guards *method calls* on a [Source] (`getPopularManga()`,
 * `getSearchManga()`, etc.), but [HttpSource.client] and [HttpSource.headers] are lazily-initialized
 * *properties*, not methods — reading either one for the first time can run an extension's lazy
 * initializer (e.g. building an OkHttp client), which can throw a [LinkageError] just like any other
 * source-owned code. Confirmed in practice: the installed Asura Scans extension throws
 * `NoClassDefFoundError: okhttp3.zstd.Zstd` from `HttpSource.client`'s lazy initializer, reached from
 * cover/page-preview image loading (`MangaCoverFetcher`/`PagePreviewFetcher`), not from any
 * `SourceRuntime`-guarded method call. These accessors route that lazy-property read through the same
 * [SourceRuntime.runBlockingSourceCall] classification/recording boundary.
 */
fun HttpSource.safeClientOrNull(): Call.Factory? {
    return SourceRuntime.runBlockingSourceCall(this, SourceRuntimeOperation.Client) {
        (this as HttpSource).client
    }.getOrNull()
}

fun HttpSource.safeHeadersOrNull(): Headers? {
    return SourceRuntime.runBlockingSourceCall(this, SourceRuntimeOperation.Headers) {
        (this as HttpSource).headers
    }.getOrNull()
}
// KMK <--
