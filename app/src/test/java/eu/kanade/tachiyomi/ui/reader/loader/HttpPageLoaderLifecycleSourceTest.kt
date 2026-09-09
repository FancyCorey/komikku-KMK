package eu.kanade.tachiyomi.ui.reader.loader

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class HttpPageLoaderLifecycleSourceTest {

    private val source = File(
        "src/main/java/eu/kanade/tachiyomi/ui/reader/loader/HttpPageLoader.kt",
    ).readText()

    @Test
    fun `recycle closes owned work and starts no detached cache write`() {
        val recycleBody = source
            .substringAfter("override fun recycle()")
            .substringBefore("private fun startWorkers")

        assertTrue(recycleBody.contains("queue.close()"))
        assertTrue(recycleBody.contains("scope.cancel()"))
        assertFalse(recycleBody.contains("launchIO"))
        assertFalse(recycleBody.contains("putPageListToCache"))
        assertTrue(source.contains("if (isRecycled) return@also"))
    }

    @Test
    fun `every HTTP page admission path uses the bounded queue`() {
        assertTrue(source.contains("BoundedReaderPageQueue(resourceConfig.queueCapacity)"))
        assertTrue(source.contains("queue.put(page, ReaderPageRequestPriority.RETRY)"))
        assertFalse(source.contains("PriorityBlockingQueue"))
        assertFalse(source.contains("queue.offer(PriorityPage"))
    }

    @Test
    fun `page cancellation is rethrown before ordinary error state is assigned`() {
        val loadMethod = source.substringAfter("private suspend fun internalLoadPage")
        val catchBody = loadMethod.substringAfter("} catch (e: Throwable) {")
        val cancellationRethrow = catchBody.indexOf("if (e is CancellationException)")
        val errorAssignment = catchBody.indexOf("page.status = Page.State.Error(e)")

        assertTrue(cancellationRethrow >= 0)
        assertTrue(errorAssignment > cancellationRethrow)
    }

    @Test
    fun `reader background cancels workers and foreground restarts the same bounded owner`() {
        assertTrue(source.contains("override fun onReaderBackground()"))
        assertTrue(source.contains("queue.pause()"))
        assertTrue(source.contains("scope.coroutineContext.cancelChildren()"))
        assertTrue(source.contains("override fun onReaderForeground()"))
        assertTrue(source.contains("queue.resume()"))
        assertTrue(source.contains("startWorkers()"))
        assertTrue(source.contains("requeueAfterCancellation"))
    }
}
