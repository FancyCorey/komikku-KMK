package eu.kanade.tachiyomi.ui.reader.loader

import android.app.ActivityManager
import android.app.Application
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.ReaderEffectiveResourcePolicy
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import mihon.core.archive.ArchiveReader
import tachiyomi.core.common.util.system.ImageUtil
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.InputStream

/** Loader used to load a chapter from an archive file. */
internal class ArchivePageLoader(
    reader: ArchiveReader,
    onDegraded: (ArchiveReaderDegradation) -> Unit = {},
) : PageLoader() {
    private val context: Application by injectLazy()
    private val readerPreferences: ReaderPreferences by injectLazy()
    private val source = AndroidArchiveReaderDataSource(reader)
    private val sessionDelegate = lazy {
        val effective = ReaderEffectiveResourcePolicy.resolve(
            rawWorkerCount = readerPreferences.readerThreads().get(),
            rawPreloadSize = readerPreferences.preloadSize().get(),
            rawCacheSizeMb = readerPreferences.cacheSize().get(),
            rawArchiveReaderMode = readerPreferences.archiveReaderMode().get(),
        )
        val memoryClassBytes = context.getSystemService(ActivityManager::class.java)?.memoryClass?.toLong()
            ?.times(ArchiveReaderResourcePolicy.MEBIBYTE)
            ?: Runtime.getRuntime().maxMemory()
        ArchiveReaderSession(
            source = source,
            mode = effective.archiveReaderMode.toSessionMode(),
            memoryBudgetBytes = ArchiveReaderResourcePolicy.memoryBudget(memoryClassBytes),
            diskBudgetBytes = ArchiveReaderResourcePolicy.diskBudget(effective.cacheSizeBytes),
            cacheDirectory = File(
                context.externalCacheDir ?: context.cacheDir,
                "reader_${reader.archiveHashCode}_${System.nanoTime()}",
            ),
            onDegraded = onDegraded,
        )
    }
    private val session by sessionDelegate

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        return session.entryNames().mapIndexed { index, entryName ->
            ReaderPage(index).apply {
                stream = { session.openPage(index, entryName) }
                status = Page.State.Ready
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    override fun onMemoryPressure() {
        session.onMemoryPressure()
    }

    override fun recycle() {
        super.recycle()
        if (sessionDelegate.isInitialized()) session.recycle() else source.close()
    }

    private fun Int.toSessionMode(): ArchiveReaderSessionMode = when (this) {
        ReaderPreferences.ArchiveReaderMode.LOAD_INTO_MEMORY -> ArchiveReaderSessionMode.MEMORY
        ReaderPreferences.ArchiveReaderMode.CACHE_TO_DISK -> ArchiveReaderSessionMode.DISK
        else -> ArchiveReaderSessionMode.DIRECT
    }
}

private class AndroidArchiveReaderDataSource(
    private val reader: ArchiveReader,
) : ArchiveReaderDataSource {
    override val wrongPassword: Boolean?
        get() = reader.wrongPassword

    override fun imageEntryNames(): List<String> = reader.useEntries { entries ->
        entries
            .filter { entry ->
                entry.isFile && ImageUtil.isImage(entry.name) { reader.getInputStream(entry.name)!! }
            }
            .map { it.name }
            .sortedWith { first, second -> first.compareToCaseInsensitiveNaturalOrder(second) }
            .toList()
    }

    override fun openEntry(name: String): InputStream = checkNotNull(reader.getInputStream(name))

    override fun close() = reader.close()
}
