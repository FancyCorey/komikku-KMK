package eu.kanade.tachiyomi.ui.reader

import java.io.IOException
import java.util.NoSuchElementException

/** Bounded categories for reader startup logging; throwable payloads are intentionally excluded. */
internal object ReaderInitialChapterLoadFailurePolicy {
    enum class Category {
        MISSING_CHAPTER,
        IO,
        UNKNOWN,
    }

    fun category(error: Throwable): Category = when {
        error is NoSuchElementException -> Category.MISSING_CHAPTER
        error is IOException -> Category.IO
        else -> Category.UNKNOWN
    }
}
