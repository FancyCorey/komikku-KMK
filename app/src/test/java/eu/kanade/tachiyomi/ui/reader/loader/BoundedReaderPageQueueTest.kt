package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class BoundedReaderPageQueueTest {

    @Test
    fun `queue never exceeds capacity`() {
        val queue = BoundedReaderPageQueue(capacity = 2)

        assertInstanceOf(
            ReaderPageQueueAdmission.Enqueued::class.java,
            queue.offer(page(1), ReaderPageRequestPriority.PRELOAD),
        )
        assertInstanceOf(
            ReaderPageQueueAdmission.Enqueued::class.java,
            queue.offer(page(2), ReaderPageRequestPriority.PRELOAD),
        )
        assertEquals(
            ReaderPageQueueAdmission.Full,
            queue.offer(page(3), ReaderPageRequestPriority.PRELOAD),
        )
        assertEquals(2, queue.queuedCount())
    }

    @Test
    fun `same page is scheduled only once`() {
        val queue = BoundedReaderPageQueue(capacity = 2)
        val page = page(1)

        assertInstanceOf(
            ReaderPageQueueAdmission.Enqueued::class.java,
            queue.offer(page, ReaderPageRequestPriority.PRELOAD),
        )
        assertEquals(
            ReaderPageQueueAdmission.AlreadyScheduled,
            queue.offer(page, ReaderPageRequestPriority.PRELOAD),
        )
        assertEquals(1, queue.queuedCount())
    }

    @Test
    fun `visible page evicts lower priority preload when full`() {
        val queue = BoundedReaderPageQueue(capacity = 2)
        val firstPreload = page(1)
        val secondPreload = page(2)
        val current = page(3)
        queue.offer(firstPreload, ReaderPageRequestPriority.PRELOAD)
        queue.offer(secondPreload, ReaderPageRequestPriority.PRELOAD)

        val admission = queue.offer(current, ReaderPageRequestPriority.CURRENT)

        val enqueued = assertInstanceOf(ReaderPageQueueAdmission.Enqueued::class.java, admission)
        assertSame(secondPreload, enqueued.evictedPage)
        assertEquals(2, queue.queuedCount())
        assertSame(current, queue.take()!!.page)
    }

    @Test
    fun `retry current and preload are consumed in priority and fifo order`() {
        val queue = BoundedReaderPageQueue(capacity = 4)
        val firstPreload = page(1)
        val secondPreload = page(2)
        val current = page(3)
        val retry = page(4)
        queue.offer(firstPreload, ReaderPageRequestPriority.PRELOAD)
        queue.offer(secondPreload, ReaderPageRequestPriority.PRELOAD)
        queue.offer(current, ReaderPageRequestPriority.CURRENT)
        queue.offer(retry, ReaderPageRequestPriority.RETRY)

        assertSame(retry, queue.take()!!.page)
        assertSame(current, queue.take()!!.page)
        assertSame(firstPreload, queue.take()!!.page)
        assertSame(secondPreload, queue.take()!!.page)
    }

    @Test
    fun `in flight page remains deduplicated until completion`() {
        val queue = BoundedReaderPageQueue(capacity = 1)
        val page = page(1)
        queue.offer(page, ReaderPageRequestPriority.CURRENT)
        val active = queue.take()!!

        assertEquals(
            ReaderPageQueueAdmission.AlreadyScheduled,
            queue.offer(page, ReaderPageRequestPriority.RETRY),
        )

        queue.complete(active)

        assertInstanceOf(
            ReaderPageQueueAdmission.Enqueued::class.java,
            queue.offer(page, ReaderPageRequestPriority.RETRY),
        )
    }

    @Test
    fun `removal is token scoped and closing rejects all later work`() {
        val queue = BoundedReaderPageQueue(capacity = 1)
        val admission = queue.offer(page(1), ReaderPageRequestPriority.CURRENT)
            as ReaderPageQueueAdmission.Enqueued

        assertEquals(true, queue.remove(admission.request))
        assertEquals(false, queue.remove(admission.request))
        assertEquals(0, queue.queuedCount())

        queue.close()

        assertEquals(
            ReaderPageQueueAdmission.Closed,
            queue.offer(page(2), ReaderPageRequestPriority.RETRY),
        )
        assertNull(queue.take())
    }

    @Test
    fun `blocking admission waits for capacity instead of dropping a visible page`() {
        val queue = BoundedReaderPageQueue(capacity = 1)
        val executor = Executors.newSingleThreadExecutor()
        val attempted = CountDownLatch(1)
        val waitingPage = page(2)
        queue.offer(page(1), ReaderPageRequestPriority.CURRENT)

        try {
            val waitingAdmission = executor.submit<ReaderPageQueueAdmission> {
                attempted.countDown()
                queue.put(waitingPage, ReaderPageRequestPriority.CURRENT)
            }
            assertTrue(attempted.await(1, TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java) {
                waitingAdmission.get(100, TimeUnit.MILLISECONDS)
            }
            assertEquals(
                ReaderPageQueueAdmission.AlreadyScheduled,
                queue.offer(waitingPage, ReaderPageRequestPriority.RETRY),
            )

            val first = queue.take()!!
            queue.complete(first)

            assertInstanceOf(
                ReaderPageQueueAdmission.Enqueued::class.java,
                waitingAdmission.get(1, TimeUnit.SECONDS),
            )
        } finally {
            queue.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `paused queue holds work until foreground resume`() {
        val queue = BoundedReaderPageQueue(capacity = 1)
        val executor = Executors.newSingleThreadExecutor()
        val page = page(3)
        queue.pause()
        queue.offer(page, ReaderPageRequestPriority.CURRENT)

        try {
            val waitingTake = executor.submit<ReaderPageQueueRequest?> { queue.take() }
            assertThrows(TimeoutException::class.java) {
                waitingTake.get(100, TimeUnit.MILLISECONDS)
            }

            queue.resume()

            assertSame(page, waitingTake.get(1, TimeUnit.SECONDS)!!.page)
        } finally {
            queue.close()
            executor.shutdownNow()
        }
    }

    private fun page(index: Int) = ReaderPage(index)
}
