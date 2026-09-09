package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.ui.manga.ChapterList
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterLinePreference

/** Applies the user's explicit same-number chapter-line preference without guessing source identity. */
object ChapterLineContinuityPolicy {

    fun collapse(
        chapters: List<Chapter>,
        preference: ChapterLinePreference? = null,
    ): List<Chapter> =
        chapters.groupBy { it.chapterNumber }.values.map { variants ->
            preferredVariant(variants, preference)
                ?: variants.firstOrNull { it.read }
                ?: variants.maxByOrNull { it.lastPageRead }
                ?: variants.first()
        }

    fun collapseItems(
        items: List<ChapterList.Item>,
        preference: ChapterLinePreference? = null,
    ): List<ChapterList.Item> =
        items.groupBy { it.chapter.chapterNumber }.values.map { variants ->
            preferredVariant(variants, preference)
                ?: variants.firstOrNull { it.chapter.read }
                ?: variants.maxByOrNull { it.chapter.lastPageRead }
                ?: variants.first()
        }

    private fun preferredVariant(
        variants: List<Chapter>,
        preference: ChapterLinePreference?,
    ): Chapter? = preference?.let { selected ->
        variants.firstOrNull { it.matches(selected) }
    }

    private fun preferredVariant(
        variants: List<ChapterList.Item>,
        preference: ChapterLinePreference?,
    ): ChapterList.Item? = preference?.let { selected ->
        variants.firstOrNull { it.chapter.matches(selected) }
    }

    private fun Chapter.matches(preference: ChapterLinePreference): Boolean {
        val preferredScanlator = preference.preferredScanlator?.normalizeScanlator()
        return if (preferredScanlator != null) {
            scanlator?.normalizeScanlator() == preferredScanlator
        } else {
            url == preference.anchorChapterUrl
        }
    }

    private fun String.normalizeScanlator(): String =
        trim().replace(Regex("\\s+"), " ").lowercase()
}
