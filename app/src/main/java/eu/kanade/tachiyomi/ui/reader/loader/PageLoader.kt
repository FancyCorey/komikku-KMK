package eu.kanade.tachiyomi.ui.reader.loader

import androidx.annotation.CallSuper
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

/**
 * A loader used to load pages into the reader. Any open resources must be cleaned up when the
 * method [recycle] is called.
 */
abstract class PageLoader {

    /**
     * Whether this loader has been already recycled.
     */
    var isRecycled = false
        private set

    abstract var isLocal: Boolean

    /**
     * Returns the list of pages of a chapter.
     */
    abstract suspend fun getPages(): List<ReaderPage>

    /**
     * Loads the page. May also preload other pages.
     * Progress of the page loading should be followed via [page.statusFlow].
     * [loadPage] is not currently guaranteed to complete, so it should be launched asynchronously.
     */
    open suspend fun loadPage(page: ReaderPage) {}

    /**
     * Retries every currently failed page in [page]'s chapter. This method only makes sense when
     * an online source is used; already-ready, queued, loading, and unrelated chapter pages are
     * left untouched.
     */
    open fun retryPage(page: ReaderPage) {}

    /** Releases optional retained resources when Android reports memory pressure. */
    open fun onMemoryPressure() {}

    /** Stops owned foreground work while the reader is not active. */
    open fun onReaderBackground() {}

    /** Restarts owned foreground work after the reader becomes active again. */
    open fun onReaderForeground() {}

    /**
     * Recycles this loader. Implementations must override this method to clean up any active
     * resources.
     */
    @CallSuper
    open fun recycle() {
        isRecycled = true
    }
}
