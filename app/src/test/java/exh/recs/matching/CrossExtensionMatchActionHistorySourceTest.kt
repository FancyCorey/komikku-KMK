package exh.recs.matching

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class CrossExtensionMatchActionHistorySourceTest {
    @Test
    fun `comparison route resolves the persisted selection mode instead of legacy boolean only`() {
        val source = File("src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt").readText()
        assertTrue(source.contains("sourcePreferences.sameMangaMatchPreselectionMode().get()"))
        assertTrue(source.contains("preselectionMode = preselectionMode"))
        assertTrue(source.contains("preselectResults = preselectionMode != SameMangaPreselectionMode.NONE"))
    }

    @Test
    fun `a full comparison search clears selections from the prior pass`() {
        val source = File("src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt").readText()
        val searchBlock = source.substringAfter("private fun search(")
            .substringBefore("fun updateSearchQuery")

        assertTrue(searchBlock.contains("selectedKeys = if (requestedSources == null) emptySet() else current.selectedKeys"))
        assertTrue(
            searchBlock.contains(
                "manuallyDeselectedKeys = if (requestedSources == null) emptySet() else current.manuallyDeselectedKeys",
            ),
        )
    }

    @Test
    fun `comparison route reapplies selection when the persisted default changes`() {
        val source = File("src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt").readText()

        assertTrue(source.contains("sameMangaMatchPreselectionMode().changes()"))
        assertTrue(source.contains("refreshSelectionForCurrentResults(preselectionMode)"))
        assertTrue(source.contains("current.copy(selectedKeys = selectedKeys)"))
        assertTrue(source.contains("manuallyDeselected = state.value.manuallyDeselectedKeys"))
    }

    @Test
    fun `rating route builds before write and commits only after batch success`() {
        val source = File("src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt").readText()
        // KMK v0.8.21-fix3: R1 correction -- CrossExtensionMatchMode.MarkSeen no longer exists
        // (collapsed into Rating(NOT_INTERESTED)); the Rating branch is now bounded by the next
        // real branch, Favorite.
        val ratingBlock = source.substringAfter("is CrossExtensionMatchMode.Rating")
            .substringBefore("CrossExtensionMatchMode.Favorite")
        assertTrue(ratingBlock.contains("buildRatingChange"))
        assertTrue(ratingBlock.contains("setMangaTasteBatch.await(targets, m.rating)"))
        assertTrue(ratingBlock.contains("commit(journalEntries)"))
        assertTrue(ratingBlock.indexOf("buildRatingChange") < ratingBlock.indexOf("setMangaTasteBatch.await"))
        assertTrue(ratingBlock.indexOf("setMangaTasteBatch.await") < ratingBlock.indexOf("commit(journalEntries)"))
    }

    @Test
    fun `apply resolves selected candidates against current local identity before mutation`() {
        val source = File("src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt").readText()
        val applyBlock = source.substringAfter("fun applyRating(onComplete: () -> Unit)")
            .substringBefore("private fun identityPair")

        assertTrue(applyBlock.contains("getMangaInteractor.await(manga.url, manga.source) ?: manga"))
        assertTrue(applyBlock.contains("distinctBy { MangaIdentityKey(it.source, it.url) }"))
        assertTrue(
            applyBlock.indexOf("getMangaInteractor.await(manga.url, manga.source)") <
                applyBlock.indexOf("setMangaTasteBatch.await(targets, m.rating)"),
        )
    }

    @Test
    fun `rating propagation waits until the newly confirmed group is persisted`() {
        val source = File("src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt").readText()
        val linksIndex = source.indexOf("upsertCrossSourceMangaLinks.await(links)")
        val propagationIndex = source.indexOf("confirmedGroupLocalTrackingPropagator.propagateIfAnyTracked")

        assertTrue(linksIndex >= 0)
        assertTrue(propagationIndex >= 0)
        assertTrue(
            linksIndex < propagationIndex,
            "newly selected rating versions must join the confirmed group before local tracking fans out",
        )
    }

    @Test
    fun `local tracking refreshes source context for an already existing target work`() {
        val source = File("src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt").readText()
        val localBlock = source.substringAfter("is CrossExtensionMatchMode.LocalTracking")
            .substringBefore("is CrossExtensionMatchMode.Rating")
        val existingBranch = localBlock.substringAfter("if (existingId != null)")
            .substringBefore("} else {")
        assertTrue(existingBranch.contains("localTrackerRepository.upsertSource"))
        assertTrue(existingBranch.contains("source = manga.source"))
        assertTrue(existingBranch.contains("url = manga.url"))
    }

    @Test
    fun `local tracking applies the shared propagation owner after writing confirmed targets`() {
        val source = File("src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt").readText()
        val localStart = source.indexOf("is CrossExtensionMatchMode.LocalTracking")
        val propagation = "confirmedGroupLocalTrackingPropagator.propagateIfAnyTracked"
        val propagationIndices = Regex(Regex.escape(propagation)).findAll(source).map { it.range.first }.toList()
        val propagationIndex = propagationIndices.firstOrNull() ?: -1
        val firstSourceWriteIndex = source.indexOf("upsertSource", localStart)

        assertTrue(localStart >= 0)
        assertTrue(propagationIndices.size >= 2)
        assertTrue(source.indexOf("sourcePreferences.confirmedTrackedVersionLocalTrackingPropagationEnabled().get()", propagationIndex) >= 0)
        assertTrue(propagationIndex > localStart)
        assertTrue(propagationIndex < propagationIndices[1])
        assertTrue(
            firstSourceWriteIndex < propagationIndex,
            "confirmed local-tracking mappings must exist before propagation consolidates the work",
        )
    }
}
