package exh.util

import exh.recs.TestInjektSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

// KMK -->
/**
 * Direct tests for the pure [MigrationFollowUpPolicy.evaluate] decision -- mirrors
 * [PackageOperationFollowUpPolicyTest]'s own shape: no mocking needed, every branch is a plain input
 * combination.
 */
class MigrationFollowUpPolicyTest {

    companion object {
        // KMK v0.7.44: see TestInjektSupport -- this test constructs favorite=true Manga instances.
        @JvmStatic
        @BeforeAll
        fun registerCustomMangaInfoBinding() = TestInjektSupport.ensureCustomMangaInfoBound()
    }

    private fun manga(favorite: Boolean = false) = Manga.create().copy(id = 1L, source = 1L, url = "/m", favorite = favorite)

    @Test
    fun `offers a follow-up when origin exists, its source is installed, target exists, and origin is not favorite`() {
        val result = MigrationFollowUpPolicy.evaluate(
            originManga = manga(favorite = false),
            originSourceInstalled = true,
            targetManga = manga(favorite = true),
        )

        assertEquals(MigrationFollowUpPolicy.MigrateBackFollowUp.Offered, result)
    }

    @Test
    fun `refuses when the origin manga row no longer exists`() {
        val result = MigrationFollowUpPolicy.evaluate(
            originManga = null,
            originSourceInstalled = true,
            targetManga = manga(),
        )

        assertEquals(
            MigrationFollowUpPolicy.MigrateBackFollowUp.Unavailable(MigrationFollowUpPolicy.MigrateBackFollowUp.Reason.ORIGIN_MANGA_NOT_FOUND),
            result,
        )
    }

    @Test
    fun `refuses when the origin source is not installed`() {
        val result = MigrationFollowUpPolicy.evaluate(
            originManga = manga(),
            originSourceInstalled = false,
            targetManga = manga(),
        )

        assertEquals(
            MigrationFollowUpPolicy.MigrateBackFollowUp.Unavailable(MigrationFollowUpPolicy.MigrateBackFollowUp.Reason.ORIGIN_SOURCE_NOT_INSTALLED),
            result,
        )
    }

    @Test
    fun `refuses when the target manga row no longer exists`() {
        val result = MigrationFollowUpPolicy.evaluate(
            originManga = manga(),
            originSourceInstalled = true,
            targetManga = null,
        )

        assertEquals(
            MigrationFollowUpPolicy.MigrateBackFollowUp.Unavailable(MigrationFollowUpPolicy.MigrateBackFollowUp.Reason.TARGET_MANGA_NOT_FOUND),
            result,
        )
    }

    @Test
    fun `refuses when the origin is already a favorite -- something else already changed its state`() {
        val result = MigrationFollowUpPolicy.evaluate(
            originManga = manga(favorite = true),
            originSourceInstalled = true,
            targetManga = manga(),
        )

        assertEquals(
            MigrationFollowUpPolicy.MigrateBackFollowUp.Unavailable(MigrationFollowUpPolicy.MigrateBackFollowUp.Reason.ORIGIN_ALREADY_FAVORITE),
            result,
        )
    }

    @Test
    fun `checks are evaluated in order -- a missing origin wins over a missing target`() {
        val result = MigrationFollowUpPolicy.evaluate(
            originManga = null,
            originSourceInstalled = false,
            targetManga = null,
        )

        assertEquals(
            MigrationFollowUpPolicy.MigrateBackFollowUp.Unavailable(MigrationFollowUpPolicy.MigrateBackFollowUp.Reason.ORIGIN_MANGA_NOT_FOUND),
            result,
        )
    }
}
// KMK <--
