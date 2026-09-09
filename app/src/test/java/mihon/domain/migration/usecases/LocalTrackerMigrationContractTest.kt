package mihon.domain.migration.usecases

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Pins the local-tracking guarantees of the existing manga migration seam. */
class LocalTrackerMigrationContractTest {

    private val source = File("src/main/java/mihon/domain/migration/usecases/MigrateMangaUseCase.kt")
        .also { check(it.exists()) { "expected migration use case source" } }
        .readText()
    private val progressPolicySource = File("src/main/java/mihon/domain/migration/usecases/LocalTrackerMigrationProgressPolicy.kt")
        .also { check(it.exists()) { "expected migration progress policy source" } }
        .readText()

    @Test
    fun `track migration carries local source relationships without merging owners`() {
        assertTrue(source.contains("private val localTrackerRepository: LocalTrackerRepository"))
        assertTrue(source.contains("getWorkIdBySourceUrl(current.source, current.url)"))
        assertTrue(source.contains("getWorkIdBySourceUrl(target.source, target.url)"))
        assertTrue(source.contains("Local tracker target source is already owned by another work"))
        assertTrue(source.contains("localTrackerRepository.migrateSourceRelationship"))
        assertTrue(source.contains("localTrackerRepository.getSourceProgress"))
        assertTrue(source.contains("LocalTrackedWorkSourceProgress"))
        assertTrue(source.contains("LocalTrackerMigrationProgressPolicy.exactTargetChapter"))
        assertTrue(source.contains("LocalTrackedSourceProgressPolicy.accepts"))
        assertTrue(progressPolicySource.contains("chapter.isRecognizedNumber && chapter.chapterNumber == chapterNumber"))
        assertTrue(source.contains("inheritedFromSource = current.source"))
        assertTrue(source.contains("confirmation = LocalTrackedWorkSourceConfirmation.USER_CONFIRMED"))
        assertTrue(source.contains("getTracks.await(current.id)"))
    }
}
