package exh

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class GalleryAddPrivacySourceTest {
    private fun source(path: String): String = Files.readAllBytes(java.nio.file.Path.of(path)).toString(Charsets.UTF_8)

    @Test
    fun `all consumers use typed gallery failure reasons`() {
        val production = listOf(
            "src/main/java/exh/GalleryAdder.kt",
            "src/main/java/exh/ui/intercept/InterceptActivity.kt",
            "src/main/java/exh/ui/batchadd/BatchAddScreenModel.kt",
            "src/main/java/exh/favorites/FavoritesSyncHelper.kt",
        ).joinToString("\n") { source(it) }

        assertFalse("result.logMessage" in production)
        assertFalse("result.galleryUrl" in production)
        assertFalse("GalleryAddFail(" in production)
        assertFalse("InvalidGalleryFail(" in production)
    }

    @Test
    fun `intercept has one terminal failure renderer and generic evaluation labels`() {
        val intercept = source("src/main/java/exh/ui/intercept/InterceptActivity.kt")

        assertTrue("galleryAddFailureMessage(status.reason)" in intercept)
        assertTrue("is InterceptResult.Failure -> Unit" in intercept)
        assertTrue("EvaluationModeFormatter.sourceLabel(it.id)" in intercept)
        assertFalse(".setMessage(stringResource(SYMR.strings.could_not_open_entry" in intercept)
    }

    @Test
    fun `favorites UI applies evaluation mode redaction before identity rendering`() {
        val dialog = source(
            "src/main/java/eu/kanade/presentation/library/components/SyncFavoritesProgressDialog.kt",
        )

        assertTrue("rememberEvaluationModeEnabled()" in dialog)
        assertTrue("GalleryAddPresentationPolicy.safeTitle" in dialog)
        assertTrue("favorites_sync_unable_to_add_to_remote_safe" in dialog)
    }
}
