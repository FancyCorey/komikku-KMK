package eu.kanade.tachiyomi.data.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import eu.kanade.domain.manga.model.PagePreview
import eu.kanade.tachiyomi.data.cache.PagePreviewCache
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.PagePreviewSource
import eu.kanade.tachiyomi.source.SourceRuntime
import eu.kanade.tachiyomi.source.SourceRuntimeOperation
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.safeHeadersOrNull
import exh.source.getMainSource
import logcat.LogPriority
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Source
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException

/**
 * A [Fetcher] that fetches page preview image for [PagePreview] object.
 *
 * Disk caching is handled by [PagePreviewCache], otherwise
 * handled by Coil's [DiskCache].
 */
class PagePreviewFetcher(
    private val page: PagePreview,
    private val options: Options,
    private val pagePreviewFile: () -> File,
    private val isInCache: () -> Boolean,
    private val writeToCache: (Source) -> Unit,
    private val diskCacheKeyLazy: Lazy<String>,
    private val sourceLazy: Lazy<PagePreviewSource?>,
    private val callFactoryLazy: Lazy<Call.Factory>,
    private val imageLoader: ImageLoader,
) : Fetcher {

    private val diskCacheKey: String
        get() = diskCacheKeyLazy.value

    override suspend fun fetch(): FetchResult {
        return httpLoader()
    }

    private fun fileLoader(file: File): FetchResult {
        return SourceFetchResult(
            source = ImageSource(
                file = file.toOkioPath(),
                fileSystem = FileSystem.SYSTEM,
                diskCacheKey = diskCacheKey,
            ),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private suspend fun httpLoader(): FetchResult {
        if (isInCache() && options.diskCachePolicy.readEnabled) {
            return fileLoader(pagePreviewFile())
        }
        var snapshot = readFromDiskCache()
        try {
            // Fetch from disk cache
            if (snapshot != null) {
                val snapshotPagePreviewCache = moveSnapshotToPagePreviewCache(snapshot)
                if (snapshotPagePreviewCache != null) {
                    // Read from page preview cache
                    return fileLoader(snapshotPagePreviewCache)
                }

                // Read from snapshot
                return SourceFetchResult(
                    source = snapshot.toImageSource(),
                    mimeType = "image/*",
                    dataSource = DataSource.DISK,
                )
            }

            // Fetch from network
            val response = executeNetworkRequest()
            val responseBody = checkNotNull(response.body) { "Null response source" }
            try {
                // Read from page preview cache after page preview updated
                val responsePagePreviewCache = writeResponseToPagePreviewCache(response)
                if (responsePagePreviewCache != null) {
                    return fileLoader(responsePagePreviewCache)
                }

                // Read from disk cache
                snapshot = writeToDiskCache(response)
                if (snapshot != null) {
                    return SourceFetchResult(
                        source = snapshot.toImageSource(),
                        mimeType = "image/*",
                        dataSource = DataSource.NETWORK,
                    )
                }

                // Read from response if cache is unused or unusable
                return SourceFetchResult(
                    source = ImageSource(source = responseBody.source(), fileSystem = FileSystem.SYSTEM),
                    mimeType = "image/*",
                    dataSource = if (response.cacheResponse != null) DataSource.DISK else DataSource.NETWORK,
                )
            } catch (e: Exception) {
                responseBody.close()
                throw e
            }
        } catch (e: Exception) {
            snapshot?.close()
            throw e
        }
    }

    private suspend fun executeNetworkRequest(): Response {
        // KMK v0.8.10-fix5: fetchPreviewImage() and the source's own client construction can throw
        // LinkageError just like any other source-owned code -- PagePreviewSource always extends
        // Source, so route through SourceRuntime and fall back to a direct image request instead of
        // crashing Best Version / preview UI.
        val previewSource = sourceLazy.value
        val response = if (previewSource != null) {
            SourceRuntime.run(previewSource, SourceRuntimeOperation.PreviewImage) {
                (this as PagePreviewSource).fetchPreviewImage(
                    page.getPagePreviewInfo(),
                    getCacheControl(),
                )
            }.getOrNull()
        } else {
            null
        } ?: callFactoryLazy.value.newCall(newRequest()).await()
        if (!response.isSuccessful && response.code != HTTP_NOT_MODIFIED) {
            response.close()
            throw IOException(response.message)
        }
        return response
    }

    private fun getCacheControl(): CacheControl {
        return when {
            options.networkCachePolicy.readEnabled -> {
                // don't take up okhttp cache
                CACHE_CONTROL_NO_STORE
            }
            else -> {
                // This causes the request to fail with a 504 Unsatisfiable Request.
                CACHE_CONTROL_NO_NETWORK_NO_CACHE
            }
        }
    }

    private fun newRequest(): Request {
        val request = Request.Builder().apply {
            url(page.imageUrl)

            // KMK v0.8.10-fix5: same lazy-property linkage-failure risk as MangaCoverFetcher.
            val sourceHeaders = (sourceLazy.value as? HttpSource)?.safeHeadersOrNull()
            if (sourceHeaders != null) {
                headers(sourceHeaders)
            }
        }

        request.cacheControl(getCacheControl())

        return request.build()
    }

    private fun moveSnapshotToPagePreviewCache(snapshot: DiskCache.Snapshot): File? {
        return try {
            imageLoader.diskCache?.run {
                fileSystem.source(snapshot.data).use { input ->
                    writeSourceToPagePreviewCache(input)
                }
                remove(diskCacheKey)
            }
            return if (isInCache()) {
                pagePreviewFile()
            } else {
                null
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Page-preview snapshot cache write failed" }
            null
        }
    }

    private fun writeResponseToPagePreviewCache(response: Response): File? {
        if (!options.diskCachePolicy.writeEnabled) return null
        return try {
            response.peekBody(Long.MAX_VALUE).source().use { input ->
                writeSourceToPagePreviewCache(input)
            }
            return if (isInCache()) {
                pagePreviewFile()
            } else {
                null
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Page-preview response cache write failed" }
            null
        }
    }

    private fun writeSourceToPagePreviewCache(input: Source) {
        writeToCache(input)
    }

    private fun readFromDiskCache(): DiskCache.Snapshot? {
        return if (options.diskCachePolicy.readEnabled) imageLoader.diskCache?.openSnapshot(diskCacheKey) else null
    }

    private fun writeToDiskCache(
        response: Response,
    ): DiskCache.Snapshot? {
        val diskCache = imageLoader.diskCache
        val editor = diskCache?.openEditor(diskCacheKey) ?: return null
        try {
            diskCache.fileSystem.write(editor.data) {
                response.body.source().readAll(this)
            }
            return editor.commitAndOpenSnapshot()
        } catch (e: Exception) {
            try {
                editor.abort()
            } catch (ignored: Exception) {
            }
            throw e
        }
    }

    private fun DiskCache.Snapshot.toImageSource(): ImageSource {
        return ImageSource(
            file = data,
            fileSystem = FileSystem.SYSTEM,
            diskCacheKey = diskCacheKey,
            closeable = this,
        )
    }

    class Factory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<PagePreview> {

        private val pagePreviewCache: PagePreviewCache by injectLazy()
        private val sourceManager: SourceManager by injectLazy()

        override fun create(data: PagePreview, options: Options, imageLoader: ImageLoader): Fetcher {
            return PagePreviewFetcher(
                page = data,
                options = options,
                pagePreviewFile = { pagePreviewCache.getImageFile(data.imageUrl) },
                isInCache = { pagePreviewCache.isImageInCache(data.imageUrl) },
                writeToCache = { pagePreviewCache.putImageToCache(data.imageUrl, it) },
                diskCacheKeyLazy = lazy { imageLoader.components.key(data, options)!! },
                sourceLazy = lazy { sourceManager.get(data.source)?.getMainSource<PagePreviewSource>() },
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
            )
        }
    }

    companion object {
        private val CACHE_CONTROL_NO_STORE = CacheControl.Builder().noStore().build()
        private val CACHE_CONTROL_NO_NETWORK_NO_CACHE = CacheControl.Builder().noCache().onlyIfCached().build()

        private const val HTTP_NOT_MODIFIED = 304
    }
}
