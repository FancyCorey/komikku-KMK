package exh.perf

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreOutcome
import eu.kanade.tachiyomi.data.backup.restore.BackupRestorer
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.testutil.DisposableTestEnvironmentGuard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.repository.TasteRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/** Device proof that one rating and its confirmed identity decision survive taste-profile restore. */
@RunWith(AndroidJUnit4::class)
class RatingGroupBackupDeviceAcceptanceTest {

    private fun backupOptions() = BackupOptions(
        libraryEntries = false,
        categories = false,
        chapters = false,
        tracking = false,
        history = false,
        readEntries = false,
        appSettings = false,
        extensionStores = false,
        sourceSettings = false,
        privateSettings = false,
        customInfo = false,
        savedSearchesFeeds = false,
        tasteProfile = true,
        localTracker = false,
    )

    private fun restoreOptions() = RestoreOptions(
        libraryEntries = false,
        categories = false,
        appSettings = false,
        extensionStores = false,
        sourceSettings = false,
        savedSearchesFeeds = false,
        tasteProfile = true,
        localTracker = false,
    )

    @Test
    fun ratingAndConfirmedDecisionSurviveTasteProfileRoundTrip() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DisposableTestEnvironmentGuard.assumeDisposableEnvironment(context)

        val tasteRepository = Injekt.get<TasteRepository>()
        val selectedTaste = tasteRepository.getAllMangaTastes().firstOrNull { it.rating != 0 }
        assertNotNull("seeded profile must contain a rating", selectedTaste)
        val selectedDecision = Injekt.get<GetCrossSourceIdentityDecisions>().awaitAll()
            .firstOrNull { CrossSourceIdentityDecisionPolicy.isAuthoritativeConfirmation(it) }
        assertNotNull("seeded profile must contain a confirmed identity decision", selectedDecision)

        val backupFile = File(context.cacheDir, "device-rating-group-${System.currentTimeMillis()}.tachibk")
        try {
            check(backupFile.createNewFile()) { "could not create backup file" }
            BackupCreator(context = context, isAutoBackup = false).backup(Uri.fromFile(backupFile), backupOptions())
            assertTrue("backup must be non-empty", backupFile.length() > 0)

            val taste = selectedTaste!!
            val decision = selectedDecision!!
            tasteRepository.deleteMangaTaste(taste.source, taste.url)
            assertTrue(tasteRepository.replaceCrossSourceIdentityDecision(expected = decision, replacement = null))
            assertEquals(null, tasteRepository.getMangaTaste(taste.source, taste.url))
            assertEquals(null, Injekt.get<GetCrossSourceIdentityDecisions>().await(decision.pair))

            val outcome = BackupRestorer(
                context = context,
                notifier = BackupNotifier(context),
                isSync = false,
            ).restore(Uri.fromFile(backupFile), restoreOptions())
            assertTrue("expected clean restore, got $outcome", outcome is BackupRestoreOutcome.Success)
            assertEquals(taste.rating, tasteRepository.getMangaTaste(taste.source, taste.url)?.rating)
            assertEquals(decision, Injekt.get<GetCrossSourceIdentityDecisions>().await(decision.pair))
        } finally {
            val taste = selectedTaste!!
            tasteRepository.deleteMangaTaste(taste.source, taste.url)
            tasteRepository.upsertMangaTaste(taste)
            selectedDecision?.let { decision ->
                if (Injekt.get<GetCrossSourceIdentityDecisions>().await(decision.pair) == null) {
                    tasteRepository.upsertCrossSourceIdentityDecisions(listOf(decision))
                }
            }
            backupFile.delete()
        }
    }
}
