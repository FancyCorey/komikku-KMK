package mihon.domain.migration.usecases

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceProgress

/** Selects only a target chapter whose recognized number exactly matches migrated local progress. */
internal object LocalTrackerMigrationProgressPolicy {
    fun exactTargetChapter(
        progress: LocalTrackedWorkSourceProgress?,
        targetChapters: List<Chapter>,
    ): Chapter? {
        val chapterNumber = progress?.chapterNumber?.takeIf { it.isFinite() } ?: return null
        return targetChapters.asSequence()
            .filter { chapter ->
                chapter.isRecognizedNumber && chapter.chapterNumber == chapterNumber
            }
            .distinctBy(Chapter::url)
            .singleOrNull()
    }
}
