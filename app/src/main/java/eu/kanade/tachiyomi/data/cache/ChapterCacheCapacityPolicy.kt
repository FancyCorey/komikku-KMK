package eu.kanade.tachiyomi.data.cache

import eu.kanade.tachiyomi.ui.reader.ReaderEffectiveResourcePolicy
import kotlinx.coroutines.CancellationException

internal sealed interface ChapterCacheCapacityUpdate {
    val effectiveBytes: Long

    data class Applied(override val effectiveBytes: Long) : ChapterCacheCapacityUpdate
    data class Failed(override val effectiveBytes: Long) : ChapterCacheCapacityUpdate
}

/** Applies a normalized capacity without retaining a throwable or rewriting its persisted value. */
internal object ChapterCacheCapacityPolicy {
    fun apply(
        rawCacheSizeMb: String?,
        update: (Long) -> Unit,
    ): ChapterCacheCapacityUpdate {
        val effectiveBytes = ReaderEffectiveResourcePolicy.effectiveCacheSizeBytes(rawCacheSizeMb)
        return try {
            update(effectiveBytes)
            ChapterCacheCapacityUpdate.Applied(effectiveBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ChapterCacheCapacityUpdate.Failed(effectiveBytes)
        }
    }
}
