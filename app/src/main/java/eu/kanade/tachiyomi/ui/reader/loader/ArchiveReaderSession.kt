package eu.kanade.tachiyomi.ui.reader.loader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.coroutineContext

internal enum class ArchiveReaderSessionMode {
    DIRECT,
    MEMORY,
    DISK,
}

enum class ArchiveReaderDegradation {
    MEMORY_LIMIT,
    STORAGE_LIMIT,
}

internal interface ArchiveReaderDataSource {
    val wrongPassword: Boolean?

    fun imageEntryNames(): List<String>

    fun openEntry(name: String): InputStream

    fun close()
}

internal object ArchiveReaderResourcePolicy {
    const val MEBIBYTE = 1024L * 1024L
    const val MIN_MEMORY_BUDGET = 16L * MEBIBYTE
    const val MAX_MEMORY_BUDGET = 64L * MEBIBYTE
    const val MAX_DISK_BUDGET = 512L * MEBIBYTE
    const val DISK_FREE_SPACE_RESERVE = 256L * MEBIBYTE
    private const val RUNNING_LOW_TRIM_LEVEL = 10
    private const val RUNNING_CRITICAL_TRIM_LEVEL = 15
    private const val BACKGROUND_TRIM_LEVEL = 40

    fun memoryBudget(memoryClassBytes: Long): Long {
        return (memoryClassBytes.coerceAtLeast(0L) / 8L)
            .coerceIn(MIN_MEMORY_BUDGET, MAX_MEMORY_BUDGET)
    }

    fun diskBudget(validatedReaderCacheSizeBytes: Long): Long {
        return validatedReaderCacheSizeBytes.coerceIn(0L, MAX_DISK_BUDGET)
    }

    fun canAdd(currentBytes: Long, addedBytes: Long, budgetBytes: Long): Boolean {
        if (currentBytes < 0L || addedBytes < 0L || budgetBytes < 0L) return false
        return addedBytes <= budgetBytes && currentBytes <= budgetBytes - addedBytes
    }

    fun preservesFreeSpace(usableBytes: Long, addedBytes: Long): Boolean {
        if (usableBytes < 0L || addedBytes < 0L) return false
        return addedBytes <= usableBytes && usableBytes - addedBytes >= DISK_FREE_SPACE_RESERVE
    }

    fun shouldReleaseForTrimLevel(level: Int): Boolean {
        return level == RUNNING_LOW_TRIM_LEVEL ||
            level == RUNNING_CRITICAL_TRIM_LEVEL ||
            level >= BACKGROUND_TRIM_LEVEL
    }
}

/**
 * Demand-driven archive session. It owns all cached bytes, temporary files, streams, and work.
 */
internal class ArchiveReaderSession(
    private val source: ArchiveReaderDataSource,
    private val mode: ArchiveReaderSessionMode,
    private val memoryBudgetBytes: Long,
    private val diskBudgetBytes: Long,
    private val cacheDirectory: File,
    private val usableSpace: () -> Long = { cacheDirectory.usableSpace },
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onDegraded: (ArchiveReaderDegradation) -> Unit = {},
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + dispatcher)
    private val sourceMutex = Mutex()
    private val stateLock = Any()
    private val memoryCache = LinkedHashMap<Int, ByteArray>(16, 0.75f, true)
    private val diskCache = LinkedHashMap<Int, File>(16, 0.75f, true)
    private val directStreams = linkedSetOf<InputStream>()

    private var retainedMemoryBytes = 0L
    private var retainedDiskBytes = 0L
    private var memoryAdmissionEnabled = true
    private var diskAdmissionEnabled = true
    private var degradationNotified = false
    private var recycled = false

    init {
        if (source.wrongPassword == true) error("Incorrect archive password")
    }

    suspend fun entryNames(): List<String> = runOwned {
        sourceMutex.withLock {
            ensureActive()
            source.imageEntryNames()
        }
    }

    fun openPage(index: Int, entryName: String): InputStream = runBlocking {
        runOwned {
            when (mode) {
                ArchiveReaderSessionMode.DIRECT -> openTrackedDirect(entryName)
                ArchiveReaderSessionMode.MEMORY -> openMemory(index, entryName)
                ArchiveReaderSessionMode.DISK -> openDisk(index, entryName)
            }
        }
    }

    fun onMemoryPressure() {
        synchronized(stateLock) {
            if (recycled) return
            memoryCache.clear()
            retainedMemoryBytes = 0L
            memoryAdmissionEnabled = false
        }
        if (mode == ArchiveReaderSessionMode.MEMORY) notifyDegraded(ArchiveReaderDegradation.MEMORY_LIMIT)
    }

    fun recycle() {
        synchronized(stateLock) {
            if (recycled) return
            recycled = true
            memoryAdmissionEnabled = false
            diskAdmissionEnabled = false
        }
        runBlocking {
            job.cancelAndJoin()
        }
        val streams = synchronized(stateLock) {
            directStreams.toList().also { directStreams.clear() }
        }
        streams.forEach { runCatching { it.close() } }
        runCatching { source.close() }
        synchronized(stateLock) {
            memoryCache.clear()
            diskCache.clear()
            retainedMemoryBytes = 0L
            retainedDiskBytes = 0L
        }
        runCatching { cacheDirectory.deleteRecursively() }
    }

    internal fun retainedMemoryBytesForTest(): Long = synchronized(stateLock) { retainedMemoryBytes }

    internal fun retainedDiskBytesForTest(): Long = synchronized(stateLock) { retainedDiskBytes }

    internal fun isWorkActiveForTest(): Boolean = job.isActive

    private suspend fun openMemory(index: Int, entryName: String): InputStream {
        val directBeforeRead = synchronized(stateLock) {
            checkNotRecycled()
            memoryCache[index]?.let { return ByteArrayInputStream(it) }
            !memoryAdmissionEnabled
        }
        if (directBeforeRead) return openTrackedDirect(entryName)

        val bytes = sourceMutex.withLock {
            val cachedAfterWait = synchronized(stateLock) {
                checkNotRecycled()
                memoryCache[index]
            }
            if (cachedAfterWait != null) return@withLock cachedAfterWait
            val admissionEnabled = synchronized(stateLock) { memoryAdmissionEnabled }
            if (!admissionEnabled) return@withLock null
            val loaded = readBounded(entryName, memoryBudgetBytes) ?: return@withLock null
            coroutineContext.ensureActive()
            val admitted = synchronized(stateLock) {
                checkNotRecycled()
                while (!ArchiveReaderResourcePolicy.canAdd(retainedMemoryBytes, loaded.size.toLong(), memoryBudgetBytes)) {
                    val eldest = memoryCache.entries.firstOrNull() ?: break
                    memoryCache.remove(eldest.key)
                    retainedMemoryBytes -= eldest.value.size.toLong()
                }
                if (!memoryAdmissionEnabled ||
                    !ArchiveReaderResourcePolicy.canAdd(retainedMemoryBytes, loaded.size.toLong(), memoryBudgetBytes)
                ) {
                    false
                } else {
                    memoryCache[index] = loaded
                    retainedMemoryBytes += loaded.size.toLong()
                    true
                }
            }
            loaded.takeIf { admitted }
        }
        if (bytes == null) {
            notifyDegraded(ArchiveReaderDegradation.MEMORY_LIMIT)
            return openTrackedDirect(entryName)
        }
        return ByteArrayInputStream(bytes)
    }

    private suspend fun openDisk(index: Int, entryName: String): InputStream {
        val directBeforeExtract = synchronized(stateLock) {
            checkNotRecycled()
            diskCache[index]?.takeIf(File::isFile)?.let { return it.inputStream() }
            !diskAdmissionEnabled
        }
        if (directBeforeExtract) return openTrackedDirect(entryName)

        val cacheDirectoryReady = synchronized(stateLock) {
            checkNotRecycled()
            cacheDirectory.isDirectory || cacheDirectory.mkdirs()
        }
        if (!cacheDirectoryReady) {
            disableDiskAdmission()
            return openTrackedDirect(entryName)
        }
        val target = File(cacheDirectory, "$index.page")
        val partial = File(cacheDirectory, "$index.part")
        try {
            val cachedFile = sourceMutex.withLock {
                synchronized(stateLock) {
                    checkNotRecycled()
                    diskCache[index]?.takeIf(File::isFile)?.let { return@withLock it }
                    if (!diskAdmissionEnabled) return@withLock null
                }
                partial.delete()
                source.openEntry(entryName).use { input ->
                    val output = try {
                        partial.outputStream().buffered()
                    } catch (_: IOException) {
                        return@withLock null
                    }
                    try {
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var written = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            val nextWritten = written + count.toLong()
                            if (nextWritten < written || !makeDiskRoom(nextWritten, count.toLong())) {
                                return@withLock null
                            }
                            try {
                                output.write(buffer, 0, count)
                            } catch (_: IOException) {
                                return@withLock null
                            }
                            written = nextWritten
                        }
                        try {
                            output.flush()
                        } catch (_: IOException) {
                            return@withLock null
                        }
                    } finally {
                        runCatching { output.close() }
                    }
                }
                coroutineContext.ensureActive()
                target.delete()
                if (!partial.renameTo(target)) return@withLock null
                synchronized(stateLock) {
                    checkNotRecycled()
                    diskCache[index] = target
                    retainedDiskBytes += target.length()
                }
                target
            }
            if (cachedFile == null || !cachedFile.isFile) {
                partial.delete()
                disableDiskAdmission()
                return openTrackedDirect(entryName)
            }
            return cachedFile.inputStream()
        } catch (e: CancellationException) {
            partial.delete()
            target.delete()
            throw e
        } catch (e: Exception) {
            partial.delete()
            target.delete()
            throw e
        }
    }

    private suspend fun readBounded(entryName: String, limit: Long): ByteArray? {
        if (limit <= 0L || limit >= Int.MAX_VALUE) return null
        source.openEntry(entryName).use { input ->
            val output = ByteArrayOutputStream(minOf(DEFAULT_BUFFER_SIZE.toLong(), limit).toInt())
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                total += count.toLong()
                if (total > limit) return null
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }

    private fun makeDiskRoom(newFileBytes: Long, nextWriteBytes: Long): Boolean {
        synchronized(stateLock) {
            checkNotRecycled()
            if (!diskAdmissionEnabled || newFileBytes > diskBudgetBytes) return false
            while (!ArchiveReaderResourcePolicy.canAdd(retainedDiskBytes, newFileBytes, diskBudgetBytes) ||
                !ArchiveReaderResourcePolicy.preservesFreeSpace(safeUsableSpace(), nextWriteBytes)
            ) {
                val eldest = diskCache.entries.firstOrNull() ?: return false
                diskCache.remove(eldest.key)
                val length = eldest.value.length()
                if (!eldest.value.delete()) return false
                retainedDiskBytes = (retainedDiskBytes - length).coerceAtLeast(0L)
            }
            return true
        }
    }

    private fun safeUsableSpace(): Long = runCatching(usableSpace).getOrDefault(-1L)

    private suspend fun openTrackedDirect(entryName: String): InputStream {
        val stream = sourceMutex.withLock {
            coroutineContext.ensureActive()
            source.openEntry(entryName)
        }
        val tracked = object : FilterInputStream(stream) {
            private var closed = false

            override fun close() {
                if (closed) return
                closed = true
                try {
                    super.close()
                } finally {
                    synchronized(stateLock) { directStreams.remove(this) }
                }
            }
        }
        synchronized(stateLock) {
            if (recycled) {
                tracked.close()
                error("Archive loader has been recycled")
            }
            directStreams += tracked
        }
        return tracked
    }

    private fun disableDiskAdmission() {
        synchronized(stateLock) { diskAdmissionEnabled = false }
        notifyDegraded(ArchiveReaderDegradation.STORAGE_LIMIT)
    }

    private fun notifyDegraded(reason: ArchiveReaderDegradation) {
        val shouldNotify = synchronized(stateLock) {
            if (degradationNotified || recycled) false else true.also { degradationNotified = true }
        }
        if (shouldNotify) onDegraded(reason)
    }

    private suspend fun <T> runOwned(block: suspend CoroutineScope.() -> T): T {
        synchronized(stateLock) { checkNotRecycled() }
        return withContext(scope.coroutineContext, block)
    }

    private fun checkNotRecycled() {
        check(!recycled) { "Archive loader has been recycled" }
    }
}
