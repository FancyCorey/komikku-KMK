package eu.kanade.tachiyomi.ui.reader

internal data class ReaderEffectiveResourceConfig(
    val workerCount: Int,
    val preloadSize: Int,
    val cacheSizeMb: Long,
    val cacheSizeBytes: Long,
    val archiveReaderMode: Int,
    val queueCapacity: Int,
)

/**
 * Interprets persisted reader resource preferences without rewriting them.
 *
 * Settings can be restored from old backups or written outside the current UI, so callers must use
 * this effective configuration instead of assuming that raw values remain in the UI's range.
 */
internal object ReaderEffectiveResourcePolicy {
    const val DEFAULT_WORKER_COUNT = 2
    const val DEFAULT_PRELOAD_SIZE = 10
    const val DEFAULT_CACHE_SIZE_MB = 75L

    const val MIN_WORKER_COUNT = 1
    const val MAX_WORKER_COUNT = 4
    const val MIN_PRELOAD_SIZE = 4
    const val MAX_PRELOAD_SIZE = 20
    const val MIN_CACHE_SIZE_MB = 50L
    const val MAX_CACHE_SIZE_MB = 5000L

    private const val LOAD_FROM_FILE = 0
    private const val LOAD_INTO_MEMORY = 1
    private const val CACHE_TO_DISK = 2
    private const val BYTES_PER_MB = 1024L * 1024L

    fun resolve(
        rawWorkerCount: Int,
        rawPreloadSize: Int,
        rawCacheSizeMb: String?,
        rawArchiveReaderMode: Int,
    ): ReaderEffectiveResourceConfig {
        val workerCount = rawWorkerCount.coerceIn(MIN_WORKER_COUNT, MAX_WORKER_COUNT)
        val preloadSize = rawPreloadSize.coerceIn(MIN_PRELOAD_SIZE, MAX_PRELOAD_SIZE)
        val cacheSizeMb = effectiveCacheSizeMb(rawCacheSizeMb)

        return ReaderEffectiveResourceConfig(
            workerCount = workerCount,
            preloadSize = preloadSize,
            cacheSizeMb = cacheSizeMb,
            cacheSizeBytes = cacheSizeMb * BYTES_PER_MB,
            archiveReaderMode = normalizeArchiveReaderMode(rawArchiveReaderMode),
            queueCapacity = workerCount + preloadSize,
        )
    }

    fun effectiveCacheSizeMb(rawCacheSizeMb: String?): Long {
        return rawCacheSizeMb
            ?.trim()
            ?.toLongOrNull()
            ?.coerceIn(MIN_CACHE_SIZE_MB, MAX_CACHE_SIZE_MB)
            ?: DEFAULT_CACHE_SIZE_MB
    }

    fun effectiveCacheSizeBytes(rawCacheSizeMb: String?): Long {
        return effectiveCacheSizeMb(rawCacheSizeMb) * BYTES_PER_MB
    }

    private fun normalizeArchiveReaderMode(rawArchiveReaderMode: Int): Int {
        return when (rawArchiveReaderMode) {
            LOAD_FROM_FILE,
            LOAD_INTO_MEMORY,
            CACHE_TO_DISK,
            -> rawArchiveReaderMode
            else -> LOAD_FROM_FILE
        }
    }
}
