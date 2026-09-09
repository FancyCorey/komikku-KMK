package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.source.SourceRuntime
import eu.kanade.tachiyomi.source.SourceRuntimeFailureRegistry
import eu.kanade.tachiyomi.source.SourceRuntimeOperation
import eu.kanade.tachiyomi.source.getOrThrowSourceRuntimeException
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.rethrowIfFatal
import eu.kanade.tachiyomi.ui.reader.ReaderEffectiveResourcePolicy
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import exh.source.isEhBasedSource
import exh.util.DataSaver
import exh.util.DataSaver.Companion.getImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.min

/**
 * Loader used to load chapters from an online source.
 */
@OptIn(DelicateCoroutinesApi::class)
internal class HttpPageLoader(
    private val chapter: ReaderChapter,
    private val source: HttpSource,
    private val chapterCache: ChapterCache = Injekt.get(),
    // SY -->
    private val readerPreferences: ReaderPreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    // SY <--
) : PageLoader() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workerLock = Any()
    private val workerJobs = mutableListOf<Job>()
    private var backgrounded = false

    private val resourceConfig = ReaderEffectiveResourcePolicy.resolve(
        rawWorkerCount = readerPreferences.readerThreads().get(),
        rawPreloadSize = readerPreferences.preloadSize().get(),
        rawCacheSizeMb = readerPreferences.cacheSize().get(),
        rawArchiveReaderMode = readerPreferences.archiveReaderMode().get(),
    )
    private val queue = BoundedReaderPageQueue(resourceConfig.queueCapacity)

    // SY -->
    private val dataSaver = DataSaver(source, sourcePreferences)
    // SY <--

    init {
        startWorkers()
    }

    override var isLocal: Boolean = false

    override fun onReaderBackground() {
        if (isRecycled) return
        queue.pause()
        synchronized(workerLock) {
            backgrounded = true
            workerJobs.clear()
        }
        // Cancel workers and explicit retry enqueuers together. Active page work is requeued by
        // runWorker's cancellation path, while the paused queue prevents new work from starting.
        scope.coroutineContext.cancelChildren()
    }

    override fun onReaderForeground() {
        if (isRecycled) return
        synchronized(workerLock) {
            backgrounded = false
        }
        queue.resume()
        startWorkers()
    }

    /**
     * Returns the page list for a chapter. It tries to return the page list from the local cache,
     * otherwise fallbacks to network.
     */
    override suspend fun getPages(): List<ReaderPage> {
        val pages = try {
            chapterCache.getPageListFromCache(chapter.chapter.toDomainChapter()!!)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            // KMK v0.8.10-fix6/fix7: this cache-miss fallback call was NOT covered by the outer
            // catch(Throwable) -- a LinkageError thrown here would still propagate out of getPages()
            // uncaught, since we're already inside the catch block that handles it, not wrapping the
            // fallback expression itself. Route through SourceRuntime. fix7: ChapterLoader.loadChapter()
            // already wraps this in catch(Throwable) and sets ReaderChapter.State.Error(e) before
            // rethrowing, so this specific escape is not itself an app-crash today -- but
            // getOrThrowSourceRuntimeException() converts a recoverable failure into an Exception
            // (RecoverableSourceRuntimeException) instead of a raw Error, so any downstream
            // Exception-only handling in the reader chain is also protected, with no behavior change
            // for the existing catch(Throwable) path.
            SourceRuntime.run(source, SourceRuntimeOperation.PageList) {
                getPageList(chapter.chapter)
            }.getOrThrowSourceRuntimeException().also { fetchedPages ->
                if (isRecycled) return@also
                try {
                    chapterCache.putPageListToCache(chapter.chapter.toDomainChapter()!!, fetchedPages)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    rethrowIfFatal(e)
                }
            }
        }
        // SY -->
        val rp = pages.mapIndexed { index, page ->
            // Don't trust sources and use our own indexing
            ReaderPage(index, page.url, page.imageUrl)
        }
        if (readerPreferences.aggressivePageLoading().get()) {
            rp.forEach {
                if (it.status == Page.State.Queue) {
                    queue.offer(it, ReaderPageRequestPriority.PRELOAD)
                }
            }
        }
        return rp
        // SY <--
    }

    /**
     * Loads a page through the queue. Handles re-enqueueing pages if they were evicted from the cache.
     */
    override suspend fun loadPage(page: ReaderPage) = withIOContext {
        val imageUrl = page.imageUrl

        // Check if the image has been deleted
        if (page.status == Page.State.Ready && imageUrl != null && !chapterCache.isImageInCache(imageUrl)) {
            page.status = Page.State.Queue
        }

        // Automatically retry failed pages when subscribed to this page
        if (page.status is Page.State.Error) {
            page.status = Page.State.Queue
        }

        val queuedRequests = mutableListOf<ReaderPageQueueRequest>()
        if (page.status == Page.State.Queue) {
            when (val admission = runInterruptible { queue.put(page, ReaderPageRequestPriority.CURRENT) }) {
                is ReaderPageQueueAdmission.Enqueued -> queuedRequests += admission.request
                ReaderPageQueueAdmission.Closed -> return@withIOContext
                ReaderPageQueueAdmission.AlreadyScheduled,
                ReaderPageQueueAdmission.Full,
                -> Unit
            }
        }
        queuedRequests += preloadNextPages(page, resourceConfig.preloadSize)

        suspendCancellableCoroutine<Nothing> { continuation ->
            continuation.invokeOnCancellation {
                queuedRequests.forEach { request ->
                    if (request.page.status == Page.State.Queue) {
                        queue.remove(request)
                    }
                }
            }
        }
    }

    /**
     * Retries every failed page in the requested page's chapter. The individual page holders use
     * this same entry point, so tapping any visible Retry action recovers the complete current
     * failed set while preserving pages that already succeeded.
     */
    override fun retryPage(page: ReaderPage) {
        if (isRecycled) return
        val failedPages = page.chapter.pages.orEmpty().filter { it.status is Page.State.Error }
        if (failedPages.isEmpty()) return

        // An explicit reader retry is the user's recovery decision. Clear only this source's
        // process-lifetime suppression so SourceRuntime can actually reattempt the source call.
        SourceRuntimeFailureRegistry.clear(source.id)

        failedPages.forEach { failedPage ->
            failedPage.status = Page.State.Queue
            // EXH -->
            // Grab a new image URL on EH sources for every retried page.
            if (source.isEhBasedSource()) {
                failedPage.imageUrl = null
            }

            if (failedPage === page && readerPreferences.readerInstantRetry().get()) { // EXH <--
                boostPage(failedPage)
            } else {
                enqueueRetry(failedPage)
            }
        }
    }

    override fun recycle() {
        super.recycle()
        synchronized(workerLock) {
            backgrounded = true
            workerJobs.clear()
        }
        queue.close()
        scope.cancel()
    }

    private fun startWorkers() {
        synchronized(workerLock) {
            if (isRecycled || backgrounded || workerJobs.any { it.isActive }) return
            repeat(resourceConfig.workerCount) {
                workerJobs += scope.launchIO { runWorker() }
            }
        }
    }

    private suspend fun runWorker() {
        while (true) {
            val request = runInterruptible { queue.take() } ?: return
            var requeueAfterCancellation = false
            try {
                if (request.page.status == Page.State.Queue) {
                    internalLoadPage(request.page)
                }
            } catch (e: CancellationException) {
                if (!isRecycled && request.page.status != Page.State.Ready) {
                    request.page.status = Page.State.Queue
                    requeueAfterCancellation = true
                }
                throw e
            } finally {
                queue.complete(request)
                if (requeueAfterCancellation && !isRecycled) {
                    queue.offer(request.page, ReaderPageRequestPriority.CURRENT)
                }
            }
        }
    }

    /**
     * Preloads the given [amount] of pages after the [currentPage] with a lower priority.
     *
     * @return queue requests that were added and therefore belong to the current subscriber
     */
    private fun preloadNextPages(currentPage: ReaderPage, amount: Int): List<ReaderPageQueueRequest> {
        val pageIndex = currentPage.index
        val pages = currentPage.chapter.pages ?: return emptyList()
        if (pageIndex == pages.lastIndex) return emptyList()

        return pages
            .subList(pageIndex + 1, min(pageIndex + 1 + amount, pages.size))
            .mapNotNull {
                if (it.status == Page.State.Queue) {
                    when (val admission = queue.offer(it, ReaderPageRequestPriority.PRELOAD)) {
                        is ReaderPageQueueAdmission.Enqueued -> admission.request
                        else -> null
                    }
                } else {
                    null
                }
            }
    }

    /**
     * Loads the page, retrieving the image URL and downloading the image if necessary.
     * Downloaded images are stored in the chapter cache.
     *
     * @param page the page whose source image has to be downloaded.
     */
    private suspend fun internalLoadPage(page: ReaderPage) {
        try {
            if (page.imageUrl.isNullOrEmpty()) {
                page.status = Page.State.LoadPage
                page.imageUrl = SourceRuntime.run(source, SourceRuntimeOperation.ImageUrl) {
                    (this as HttpSource).getImageUrl(page)
                }.getOrThrowSourceRuntimeException()
            }
            val imageUrl = page.imageUrl!!

            if (!chapterCache.isImageInCache(imageUrl)) {
                page.status = Page.State.DownloadImage
                val imageResponse = SourceRuntime.run(source, SourceRuntimeOperation.Image) {
                    (this as HttpSource).getImage(page, dataSaver)
                }.getOrThrowSourceRuntimeException()
                chapterCache.putImageToCache(imageUrl, imageResponse)
            }

            page.stream = { chapterCache.getImageFile(imageUrl).inputStream() }
            page.status = Page.State.Ready
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            rethrowIfFatal(e)
            page.status = Page.State.Error(e)
        }
    }

    // EXH -->
    fun boostPage(page: ReaderPage) {
        if (page.status == Page.State.Queue) {
            enqueueRetry(page)
        }
    }

    private fun enqueueRetry(page: ReaderPage) {
        scope.launchIO {
            runInterruptible {
                queue.put(page, ReaderPageRequestPriority.RETRY)
            }
        }
    }
    // EXH <--
}
