package eu.kanade.tachiyomi.data.download

import com.hippo.unifile.UniFile
import tachiyomi.domain.chapter.model.Chapter

/** Deletes known chapter directories and returns only chapters whose directory was removed. */
internal fun deleteChapterDirectories(chapterDirs: List<Pair<Chapter, UniFile>>): List<Chapter> =
    chapterDirs.filter { (_, directory) -> directory.delete() }.map { (chapter) -> chapter }
