package exh.perf

import android.app.ActivityManager
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupFileWriter
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.testutil.DisposableTestEnvironmentGuard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Measures the current full-backup cost against the disposable performance fixture.
 *
 * This is evidence only: it does not change serialization, compression, or retention policy. The
 * test writes one temporary backup to the app cache and deletes it unconditionally. The disposable
 * guard runs before any production repository access.
 */
@RunWith(AndroidJUnit4::class)
class BackupCostDeviceMeasurementTest {

    @Test
    fun fullBackupReportsSizeAndElapsedTime() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            DisposableTestEnvironmentGuard.assumeDisposableEnvironment(context)

            val backupFile = File(context.cacheDir, "dod-02-backup-cost-${System.nanoTime()}.tachibk")
            check(backupFile.createNewFile()) { "could not create disposable backup output" }

            try {
                val beforePssKb = targetPssKb(context)
                val startedAt = System.nanoTime()
                BackupCreator(context = context, isAutoBackup = false)
                    .backup(Uri.fromFile(backupFile), BackupOptions())
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                val afterPssKb = targetPssKb(context)

                assertTrue("backup output must be non-empty", backupFile.length() > 0)
                Log.i(
                    TAG,
                    "Backup cost measurement: bytes=${backupFile.length()} elapsedMs=$elapsedMs " +
                        "targetPssBeforeKb=$beforePssKb targetPssAfterKb=$afterPssKb",
                )
            } finally {
                check(!backupFile.exists() || backupFile.delete()) {
                    "could not delete disposable backup output"
                }
            }
        }
    }

    @Test
    fun autoBackupRotationRetainsAtMostFourMatchingFiles() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            DisposableTestEnvironmentGuard.assumeDisposableEnvironment(context)

            val directory = File(context.cacheDir, "dod-02-auto-backup-${System.nanoTime()}")
            check(directory.mkdirs()) { "could not create disposable backup directory" }
            val prefix = "${context.packageName}_"
            val seededNames = listOf(
                "${prefix}2020-01-01_00-00.tachibk",
                "${prefix}2020-01-02_00-00.tachibk",
                "${prefix}2020-01-03_00-00.tachibk",
                "${prefix}2020-01-04_00-00.tachibk",
            )
            seededNames.forEach { name ->
                File(directory, name).writeText("disposable-placeholder")
            }

            try {
                BackupCreator(context = context, isAutoBackup = true)
                    .backup(Uri.fromFile(directory), BackupOptions())

                val retained = directory.listFiles()
                    .orEmpty()
                    .filter { it.name.startsWith(prefix) && it.name.endsWith(".tachibk") }
                assertEquals("auto-backup rotation must retain four files", 4, retained.size)
                assertFalse("oldest matching backup should be rotated out", File(directory, seededNames.first()).exists())
                assertTrue("new auto-backup must be present", retained.any { it.length() > 0 })
                Log.i(TAG, "Auto-backup retention measurement: matchingFiles=${retained.size}")
            } finally {
                directory.deleteRecursively()
            }
        }
    }

    @Test
    fun interruptedWriteDeletesPartialBackupOutput() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            DisposableTestEnvironmentGuard.assumeDisposableEnvironment(context)

            val backupFile = File(context.cacheDir, "dod-02-interrupted-backup-${System.nanoTime()}.tachibk")
            check(backupFile.createNewFile()) { "could not create disposable backup output" }
            var injectedFailure = false

            try {
                val failingWriter = BackupFileWriter { file, _ ->
                    file.openOutputStream().use { output ->
                        output.write(byteArrayOf(0x1, 0x2, 0x3))
                    }
                    injectedFailure = true
                    error("injected disposable interrupted-write failure")
                }
                runCatching {
                    BackupCreator(
                        context = context,
                        isAutoBackup = false,
                        fileWriter = failingWriter,
                    ).backup(Uri.fromFile(backupFile), BackupOptions())
                }.onSuccess {
                    error("injected backup write must fail")
                }

                assertTrue("failure injector must run", injectedFailure)
                assertFalse("partial backup output must be deleted after failure", backupFile.exists())
                Log.i(TAG, "Interrupted-write measurement: partial output cleaned up")
            } finally {
                if (backupFile.exists()) {
                    check(backupFile.delete()) {
                        "could not clean disposable interrupted-write output"
                    }
                }
            }
        }
    }

    private fun targetPssKb(context: android.content.Context): Int? {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val process = activityManager.runningAppProcesses
            ?.firstOrNull { it.processName == context.packageName }
            ?: return null
        return activityManager.getProcessMemoryInfo(intArrayOf(process.pid)).firstOrNull()?.totalPss
    }

    private companion object {
        const val TAG = "DOD02BackupCost"
    }
}
