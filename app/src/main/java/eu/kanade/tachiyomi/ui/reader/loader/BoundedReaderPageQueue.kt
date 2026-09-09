package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import java.util.IdentityHashMap
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal enum class ReaderPageRequestPriority(val value: Int) {
    PRELOAD(0),
    CURRENT(1),
    RETRY(2),
}

internal class ReaderPageQueueRequest private constructor(
    val page: ReaderPage,
    val priority: ReaderPageRequestPriority,
    private val sequence: Long,
) : Comparable<ReaderPageQueueRequest> {

    override fun compareTo(other: ReaderPageQueueRequest): Int {
        val priorityOrder = other.priority.value.compareTo(priority.value)
        return if (priorityOrder != 0) priorityOrder else sequence.compareTo(other.sequence)
    }

    companion object {
        private val sequenceGenerator = AtomicLong(0)

        fun create(page: ReaderPage, priority: ReaderPageRequestPriority) =
            ReaderPageQueueRequest(page, priority, sequenceGenerator.incrementAndGet())
    }
}

internal sealed interface ReaderPageQueueAdmission {
    data class Enqueued(
        val request: ReaderPageQueueRequest,
        val evictedPage: ReaderPage? = null,
    ) : ReaderPageQueueAdmission

    data object AlreadyScheduled : ReaderPageQueueAdmission
    data object Full : ReaderPageQueueAdmission
    data object Closed : ReaderPageQueueAdmission
}

/** A loader-owned, bounded priority queue. All state transitions are atomic under [lock]. */
internal class BoundedReaderPageQueue(private val capacity: Int) {
    private val lock = ReentrantLock()
    private val notEmpty = lock.newCondition()
    private val notFull = lock.newCondition()
    private val queue = PriorityQueue<ReaderPageQueueRequest>()
    private val queuedByPage = IdentityHashMap<ReaderPage, ReaderPageQueueRequest>()
    private val activePages = IdentityHashMap<ReaderPage, Unit>()
    private val waitingPages = IdentityHashMap<ReaderPage, Unit>()
    private var closed = false
    private var paused = false

    init {
        require(capacity > 0)
    }

    fun offer(page: ReaderPage, priority: ReaderPageRequestPriority): ReaderPageQueueAdmission = lock.withLock {
        admitLocked(page, priority, ownsWaitingReservation = false)
    }

    @Throws(InterruptedException::class)
    fun put(page: ReaderPage, priority: ReaderPageRequestPriority): ReaderPageQueueAdmission {
        lock.lockInterruptibly()
        var ownsWaitingReservation = false
        try {
            while (true) {
                when (val result = admitLocked(page, priority, ownsWaitingReservation)) {
                    ReaderPageQueueAdmission.Full -> {
                        if (!ownsWaitingReservation) {
                            if (waitingPages.containsKey(page)) {
                                return ReaderPageQueueAdmission.AlreadyScheduled
                            }
                            waitingPages[page] = Unit
                            ownsWaitingReservation = true
                        }
                        notFull.await()
                    }
                    else -> return result
                }
            }
        } finally {
            if (ownsWaitingReservation) waitingPages.remove(page)
            lock.unlock()
        }
    }

    @Throws(InterruptedException::class)
    fun take(): ReaderPageQueueRequest? {
        lock.lockInterruptibly()
        try {
            while (paused || queue.isEmpty()) {
                if (closed) return null
                notEmpty.await()
            }
            return queue.remove().also { request ->
                queuedByPage.remove(request.page)
                activePages[request.page] = Unit
                notFull.signal()
            }
        } finally {
            lock.unlock()
        }
    }

    fun complete(request: ReaderPageQueueRequest) = lock.withLock {
        activePages.remove(request.page)
    }

    fun remove(request: ReaderPageQueueRequest): Boolean = lock.withLock {
        if (queuedByPage[request.page] !== request) return@withLock false
        queuedByPage.remove(request.page)
        queue.remove(request).also { removed ->
            if (removed) notFull.signal()
        }
    }

    fun close() = lock.withLock {
        if (closed) return@withLock
        closed = true
        queue.clear()
        queuedByPage.clear()
        activePages.clear()
        waitingPages.clear()
        notEmpty.signalAll()
        notFull.signalAll()
    }

    fun pause() = lock.withLock {
        if (closed) return@withLock
        paused = true
    }

    fun resume() = lock.withLock {
        if (closed) return@withLock
        paused = false
        notEmpty.signalAll()
    }

    internal fun queuedCount(): Int = lock.withLock { queue.size }

    private fun admitLocked(
        page: ReaderPage,
        priority: ReaderPageRequestPriority,
        ownsWaitingReservation: Boolean,
    ): ReaderPageQueueAdmission {
        if (closed) return ReaderPageQueueAdmission.Closed
        if (activePages.containsKey(page)) return ReaderPageQueueAdmission.AlreadyScheduled
        if (!ownsWaitingReservation && waitingPages.containsKey(page)) {
            return ReaderPageQueueAdmission.AlreadyScheduled
        }

        val existing = queuedByPage[page]
        if (existing != null) {
            if (existing.priority.value >= priority.value) {
                return ReaderPageQueueAdmission.AlreadyScheduled
            }
            queue.remove(existing)
            queuedByPage.remove(page)
        }

        var evictedPage: ReaderPage? = null
        if (queue.size >= capacity) {
            val lowestPriority = queue.maxOrNull() ?: return ReaderPageQueueAdmission.Full
            if (lowestPriority.priority.value >= priority.value) {
                return ReaderPageQueueAdmission.Full
            }
            queue.remove(lowestPriority)
            queuedByPage.remove(lowestPriority.page)
            evictedPage = lowestPriority.page
        }

        val request = ReaderPageQueueRequest.create(page, priority)
        queue.add(request)
        queuedByPage[page] = request
        notEmpty.signal()
        return ReaderPageQueueAdmission.Enqueued(request, evictedPage)
    }
}
