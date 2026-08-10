package eu.kanade.tachiyomi.data.export

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.manga.model.Manga

object LibraryExporter {

    data class ExportOptions(
        val includeTitle: Boolean,
        val includeAuthor: Boolean,
        val includeArtist: Boolean,
    )

    // KMK -->
    // KMK: `exportToCsv` previously called its
    // `onExportComplete` success callback unconditionally, even when `openOutputStream(uri)` returned
    // null (a real, truthful destination-open failure) -- reporting "library exported" for a write
    // that never happened. Now returns a typed result the caller must inspect instead of an
    // always-fired callback, and rethrows `CancellationException` rather than letting a cancelled
    // write silently report either outcome.
    sealed interface ExportOutcome {
        data object Success : ExportOutcome
        data object WriteFailed : ExportOutcome
    }

    suspend fun exportToCsv(
        context: Context,
        uri: Uri,
        favorites: List<Manga>,
        options: ExportOptions,
    ): ExportOutcome = withContext(Dispatchers.IO) {
        try {
            val wrote = context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val csvData = generateCsvData(favorites, options)
                outputStream.write(csvData.toByteArray())
            } != null
            if (wrote) ExportOutcome.Success else ExportOutcome.WriteFailed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ExportOutcome.WriteFailed
        }
    }
    // KMK <--

    private val escapeRequired = listOf("\r", "\n", "\"", ",")

    private fun generateCsvData(favorites: List<Manga>, options: ExportOptions): String {
        val columnSize = listOf(
            options.includeTitle,
            options.includeAuthor,
            options.includeArtist,
        )
            .count { it }

        val rows = buildList(favorites.size) {
            favorites.forEach { manga ->
                buildList(columnSize) {
                    if (options.includeTitle) add(manga.title)
                    if (options.includeAuthor) add(manga.author)
                    if (options.includeArtist) add(manga.artist)
                }
                    .let(::add)
            }
        }
        return rows.joinToString("\r\n") { columns ->
            columns.joinToString(",") columns@{ column ->
                if (column.isNullOrBlank()) return@columns ""
                if (escapeRequired.any { column.contains(it) }) {
                    column.replace("\"", "\"\"").let { "\"$it\"" }
                } else {
                    column
                }
            }
        }
    }
}
