package eu.kanade.tachiyomi.ui.reader.loader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ArchiveReaderSessionTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `memory budget derives from ordinary heap class and clamps`() {
        val mib = ArchiveReaderResourcePolicy.MEBIBYTE
        assertEquals(16L * mib, ArchiveReaderResourcePolicy.memoryBudget(64L * mib))
        assertEquals(32L * mib, ArchiveReaderResourcePolicy.memoryBudget(256L * mib))
        assertEquals(64L * mib, ArchiveReaderResourcePolicy.memoryBudget(1024L * mib))
        assertEquals(16L * mib, ArchiveReaderResourcePolicy.memoryBudget(Long.MIN_VALUE))
    }

    @Test
    fun `accounting rejects overflow and invalid values`() {
        assertTrue(ArchiveReaderResourcePolicy.canAdd(4, 6, 10))
        assertFalse(ArchiveReaderResourcePolicy.canAdd(5, 6, 10))
        assertFalse(ArchiveReaderResourcePolicy.canAdd(Long.MAX_VALUE, 1, Long.MAX_VALUE))
        assertFalse(ArchiveReaderResourcePolicy.canAdd(-1, 1, 10))
    }

    @Test
    fun `disk budget honors validated size and hard cap`() {
        val mib = ArchiveReaderResourcePolicy.MEBIBYTE
        assertEquals(0, ArchiveReaderResourcePolicy.diskBudget(-1))
        assertEquals(75L * mib, ArchiveReaderResourcePolicy.diskBudget(75L * mib))
        assertEquals(512L * mib, ArchiveReaderResourcePolicy.diskBudget(5000L * mib))
        assertTrue(
            ArchiveReaderResourcePolicy.preservesFreeSpace(
                ArchiveReaderResourcePolicy.DISK_FREE_SPACE_RESERVE + 1,
                1,
            ),
        )
        assertFalse(
            ArchiveReaderResourcePolicy.preservesFreeSpace(
                ArchiveReaderResourcePolicy.DISK_FREE_SPACE_RESERVE,
                1,
            ),
        )
    }

    @Test
    fun `trim policy excludes ui hidden but includes low critical and background pressure`() {
        assertFalse(ArchiveReaderResourcePolicy.shouldReleaseForTrimLevel(5))
        assertTrue(ArchiveReaderResourcePolicy.shouldReleaseForTrimLevel(10))
        assertTrue(ArchiveReaderResourcePolicy.shouldReleaseForTrimLevel(15))
        assertFalse(ArchiveReaderResourcePolicy.shouldReleaseForTrimLevel(20))
        assertTrue(ArchiveReaderResourcePolicy.shouldReleaseForTrimLevel(40))
        assertTrue(ArchiveReaderResourcePolicy.shouldReleaseForTrimLevel(80))
    }

    @Test
    fun `enumeration is ordered by source and starts no page reads`() = runBlocking {
        val source = FakeArchiveSource(linkedMapOf("2.jpg" to bytes(2), "10.jpg" to bytes(10)))
        val session = session(source, ArchiveReaderSessionMode.MEMORY, memoryBudget = 8)

        assertEquals(listOf("2.jpg", "10.jpg"), session.entryNames())
        assertEquals(0, source.openCount)
        session.recycle()
    }

    @Test
    fun `many-page enumeration remains metadata-only`() = runBlocking {
        val entries = LinkedHashMap<String, ByteArray>()
        repeat(500) { entries["$it.jpg"] = bytes(it) }
        val source = FakeArchiveSource(entries)
        val session = session(source, ArchiveReaderSessionMode.DISK)

        assertEquals(500, session.entryNames().size)
        assertEquals(0, source.openCount)
        assertFalse(tempDir.resolve("cache").exists())
        session.recycle()
    }

    @Test
    fun `memory cache reuses the retained array without a second source read`() {
        val source = FakeArchiveSource(linkedMapOf("a" to bytes(1, 2, 3)))
        val session = session(source, ArchiveReaderSessionMode.MEMORY, memoryBudget = 8)

        assertArrayEquals(bytes(1, 2, 3), session.openPage(0, "a").use { it.readBytes() })
        assertArrayEquals(bytes(1, 2, 3), session.openPage(0, "a").use { it.readBytes() })
        assertEquals(1, source.openCount)
        assertEquals(3, session.retainedMemoryBytesForTest())
        session.recycle()
    }

    @Test
    fun `memory cache evicts in access order with exact accounting`() {
        val source = FakeArchiveSource(linkedMapOf("a" to bytes(1, 1, 1), "b" to bytes(2, 2, 2)))
        val session = session(source, ArchiveReaderSessionMode.MEMORY, memoryBudget = 5)

        session.openPage(0, "a").close()
        session.openPage(1, "b").close()
        session.openPage(0, "a").close()

        assertEquals(3, source.openCount)
        assertEquals(3, session.retainedMemoryBytesForTest())
        session.recycle()
    }

    @Test
    fun `rapid duplicate memory requests perform one archive read`() {
        val source = FakeArchiveSource(
            linkedMapOf("a" to bytes(1, 2, 3)),
            streamFactory = { data ->
                Thread.sleep(20)
                ByteArrayInputStream(data)
            },
        )
        val session = session(source, ArchiveReaderSessionMode.MEMORY, memoryBudget = 8)
        val executor = Executors.newFixedThreadPool(6)
        try {
            val futures = List(12) {
                executor.submit<ByteArray> { session.openPage(0, "a").use { it.readBytes() } }
            }
            futures.forEach { assertArrayEquals(bytes(1, 2, 3), it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, source.openCount)
            assertEquals(3, session.retainedMemoryBytesForTest())
        } finally {
            executor.shutdownNow()
            session.recycle()
        }
    }

    @Test
    fun `oversized memory page falls back directly and notifies once`() {
        val notices = mutableListOf<ArchiveReaderDegradation>()
        val source = FakeArchiveSource(linkedMapOf("a" to bytes(1, 2, 3, 4, 5)))
        val session = session(source, ArchiveReaderSessionMode.MEMORY, memoryBudget = 4, notices = notices)

        assertArrayEquals(bytes(1, 2, 3, 4, 5), session.openPage(0, "a").use { it.readBytes() })
        assertArrayEquals(bytes(1, 2, 3, 4, 5), session.openPage(0, "a").use { it.readBytes() })

        assertEquals(4, source.openCount)
        assertEquals(listOf(ArchiveReaderDegradation.MEMORY_LIMIT), notices)
        assertEquals(0, session.retainedMemoryBytesForTest())
        session.recycle()
    }

    @Test
    fun `memory pressure clears bytes disables admission and notifies once`() {
        val notices = mutableListOf<ArchiveReaderDegradation>()
        val source = FakeArchiveSource(linkedMapOf("a" to bytes(1, 2, 3)))
        val session = session(source, ArchiveReaderSessionMode.MEMORY, memoryBudget = 8, notices = notices)
        session.openPage(0, "a").close()

        session.onMemoryPressure()
        session.onMemoryPressure()
        session.openPage(0, "a").close()

        assertEquals(0, session.retainedMemoryBytesForTest())
        assertEquals(2, source.openCount)
        assertEquals(listOf(ArchiveReaderDegradation.MEMORY_LIMIT), notices)
        session.recycle()
    }

    @Test
    fun `disk mode extracts only requested page and reuses complete file`() = runBlocking {
        val source = FakeArchiveSource(linkedMapOf("a" to bytes(1, 2, 3), "b" to bytes(4, 5)))
        val session = session(source, ArchiveReaderSessionMode.DISK, diskBudget = 20)

        assertEquals(listOf("a", "b"), session.entryNames())
        assertEquals(0, source.openCount)
        assertArrayEquals(bytes(1, 2, 3), session.openPage(0, "a").use { it.readBytes() })
        assertArrayEquals(bytes(1, 2, 3), session.openPage(0, "a").use { it.readBytes() })

        assertEquals(1, source.openCount)
        assertEquals(3, session.retainedDiskBytesForTest())
        assertFalse(tempDir.walkTopDown().any { it.extension == "part" })
        session.recycle()
        assertFalse(tempDir.resolve("cache").exists())
    }

    @Test
    fun `disk cache evicts deterministically before admitting next page`() {
        val source = FakeArchiveSource(linkedMapOf("a" to bytes(1, 1, 1), "b" to bytes(2, 2, 2)))
        val session = session(source, ArchiveReaderSessionMode.DISK, diskBudget = 5)

        session.openPage(0, "a").close()
        session.openPage(1, "b").close()
        session.openPage(0, "a").close()

        assertEquals(3, source.openCount)
        assertEquals(3, session.retainedDiskBytesForTest())
        session.recycle()
    }

    @Test
    fun `rapid duplicate disk requests perform one atomic extraction`() {
        val source = FakeArchiveSource(
            linkedMapOf("a" to bytes(1, 2, 3)),
            streamFactory = { data ->
                Thread.sleep(20)
                ByteArrayInputStream(data)
            },
        )
        val session = session(source, ArchiveReaderSessionMode.DISK, diskBudget = 8)
        val executor = Executors.newFixedThreadPool(6)
        try {
            val futures = List(12) {
                executor.submit<ByteArray> { session.openPage(0, "a").use { it.readBytes() } }
            }
            futures.forEach { assertArrayEquals(bytes(1, 2, 3), it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, source.openCount)
            assertEquals(3, session.retainedDiskBytesForTest())
            assertFalse(tempDir.walkTopDown().any { it.extension == "part" })
        } finally {
            executor.shutdownNow()
            session.recycle()
        }
    }

    @Test
    fun `low disk falls back directly removes partial and disables later admission`() {
        val notices = mutableListOf<ArchiveReaderDegradation>()
        val source = FakeArchiveSource(linkedMapOf("a" to bytes(1, 2, 3)))
        val session = session(
            source,
            ArchiveReaderSessionMode.DISK,
            diskBudget = 10,
            usableSpace = ArchiveReaderResourcePolicy.DISK_FREE_SPACE_RESERVE,
            notices = notices,
        )

        assertArrayEquals(bytes(1, 2, 3), session.openPage(0, "a").use { it.readBytes() })
        session.openPage(0, "a").close()

        assertEquals(3, source.openCount)
        assertEquals(0, session.retainedDiskBytesForTest())
        assertEquals(listOf(ArchiveReaderDegradation.STORAGE_LIMIT), notices)
        assertFalse(tempDir.walkTopDown().any { it.extension == "part" })
        session.recycle()
    }

    @Test
    fun `unusable cache directory degrades to direct streaming`() {
        val notices = mutableListOf<ArchiveReaderDegradation>()
        val source = FakeArchiveSource(linkedMapOf("a" to bytes(1, 2, 3)))
        val cachePath = tempDir.resolve("not-a-directory").also { it.writeText("fixture") }
        val session = session(
            source,
            ArchiveReaderSessionMode.DISK,
            diskBudget = 10,
            notices = notices,
            cacheDirectory = cachePath,
        )

        assertArrayEquals(bytes(1, 2, 3), session.openPage(0, "a").use { it.readBytes() })
        assertEquals(listOf(ArchiveReaderDegradation.STORAGE_LIMIT), notices)
        assertEquals(1, source.openCount)
        session.recycle()
    }

    @Test
    fun `cancellation during extraction propagates and removes partial files`() {
        val source = FakeArchiveSource(
            linkedMapOf("a" to bytes(1)),
            streamFactory = { throw CancellationException("cancelled") },
        )
        val session = session(source, ArchiveReaderSessionMode.DISK, diskBudget = 10)

        assertThrows(CancellationException::class.java) { session.openPage(0, "a") }
        assertFalse(tempDir.walkTopDown().any { it.extension == "part" })
        session.recycle()
    }

    @Test
    fun `recycle cancels in-flight extraction before closing source`() {
        val readStarted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val source = FakeArchiveSource(
            linkedMapOf("a" to bytes(1, 2, 3)),
            streamFactory = {
                object : InputStream() {
                    override fun read(): Int {
                        readStarted.countDown()
                        releaseRead.await(5, TimeUnit.SECONDS)
                        return -1
                    }
                }
            },
        )
        val session = session(source, ArchiveReaderSessionMode.DISK, diskBudget = 8)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val read = executor.submit<Throwable?> {
                try {
                    session.openPage(0, "a").close()
                    null
                } catch (error: Throwable) {
                    error
                }
            }
            assertTrue(readStarted.await(5, TimeUnit.SECONDS))
            val recycle = executor.submit { session.recycle() }
            val cancellationDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (session.isWorkActiveForTest() && System.nanoTime() < cancellationDeadline) {
                Thread.yield()
            }
            assertFalse(session.isWorkActiveForTest())
            releaseRead.countDown()
            recycle.get(5, TimeUnit.SECONDS)

            assertTrue(read.get(5, TimeUnit.SECONDS) is CancellationException)
            assertTrue(source.closed)
            assertFalse(tempDir.resolve("cache").exists())
        } finally {
            releaseRead.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `ordinary direct stream failure remains an error without degradation notice`() {
        val notices = mutableListOf<ArchiveReaderDegradation>()
        val source = FakeArchiveSource(
            linkedMapOf("a" to bytes(1)),
            streamFactory = { throw IOException("fixture failure") },
        )
        val session = session(source, ArchiveReaderSessionMode.DIRECT, notices = notices)

        assertThrows(IOException::class.java) { session.openPage(0, "a") }
        assertTrue(notices.isEmpty())
        session.recycle()
    }

    @Test
    fun `archive read failure in disk mode remains an error rather than false fallback`() {
        val notices = mutableListOf<ArchiveReaderDegradation>()
        val source = FakeArchiveSource(
            linkedMapOf("a" to bytes(1)),
            streamFactory = { throw IOException("fixture failure") },
        )
        val session = session(source, ArchiveReaderSessionMode.DISK, diskBudget = 10, notices = notices)

        assertThrows(IOException::class.java) { session.openPage(0, "a") }
        assertTrue(notices.isEmpty())
        assertFalse(tempDir.walkTopDown().any { it.extension == "part" })
        session.recycle()
    }

    @Test
    fun `wrong password fails before page enumeration`() {
        val source = FakeArchiveSource(linkedMapOf(), wrongPassword = true)
        assertThrows(IllegalStateException::class.java) {
            session(source, ArchiveReaderSessionMode.DIRECT)
        }
        assertEquals(0, source.listCount)
    }

    @Test
    fun `recycle closes direct streams and source and refuses new work`() {
        val source = FakeArchiveSource(linkedMapOf("a" to bytes(1, 2, 3)))
        val session = session(source, ArchiveReaderSessionMode.DIRECT)
        val stream = session.openPage(0, "a")

        session.recycle()

        assertTrue(source.closed)
        assertEquals(1, source.streamCloseCount)
        assertThrows(IllegalStateException::class.java) { session.openPage(0, "a") }
    }

    private fun session(
        source: FakeArchiveSource,
        mode: ArchiveReaderSessionMode,
        memoryBudget: Long = 8,
        diskBudget: Long = 20,
        usableSpace: Long = ArchiveReaderResourcePolicy.DISK_FREE_SPACE_RESERVE + 1024,
        notices: MutableList<ArchiveReaderDegradation> = mutableListOf(),
        cacheDirectory: File = tempDir.resolve("cache"),
    ): ArchiveReaderSession {
        return ArchiveReaderSession(
            source = source,
            mode = mode,
            memoryBudgetBytes = memoryBudget,
            diskBudgetBytes = diskBudget,
            cacheDirectory = cacheDirectory,
            usableSpace = { usableSpace },
            dispatcher = Dispatchers.Unconfined,
            onDegraded = notices::add,
        )
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private class FakeArchiveSource(
        private val entries: LinkedHashMap<String, ByteArray>,
        override val wrongPassword: Boolean? = false,
        private val streamFactory: ((ByteArray) -> InputStream)? = null,
    ) : ArchiveReaderDataSource {
        var listCount = 0
        var openCount = 0
        var streamCloseCount = 0
        var closed = false

        override fun imageEntryNames(): List<String> {
            listCount++
            return entries.keys.toList()
        }

        override fun openEntry(name: String): InputStream {
            openCount++
            val bytes = checkNotNull(entries[name])
            val delegate = streamFactory?.invoke(bytes) ?: ByteArrayInputStream(bytes)
            return object : FilterInputStream(delegate) {
                private var counted = false

                override fun close() {
                    if (!counted) {
                        counted = true
                        streamCloseCount++
                    }
                    super.close()
                }
            }
        }

        override fun close() {
            closed = true
        }
    }
}
