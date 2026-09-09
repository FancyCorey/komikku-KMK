package eu.kanade.tachiyomi.data.backup.restore.restorers

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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.model.CrossSourceIdentityDecision
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionValue
import tachiyomi.domain.taste.model.CrossSourceIdentityPair
import tachiyomi.domain.taste.model.CrossSourceIdentityReasonCode
import tachiyomi.domain.taste.model.CrossSourceIdentityReviewState
import tachiyomi.domain.taste.model.CrossSourceRecordKey
import tachiyomi.domain.taste.repository.TasteRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

// KMK F2-02 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM) -->
/**
 * The device-side complement to [TasteBackupEndToEndRoundTripTest] (host, `app/src/test`): that
 * file proves the create -> real-protobuf-wire -> restore path through real repositories on an
 * in-memory database; this file proves the SAME identity-decision data survives the parts that
 * genuinely require a real Android runtime and cannot be exercised on the host at all --
 * [BackupCreator.backup] (real `Context`/`UniFile` file write), [eu.kanade.tachiyomi.data.backup
 * .BackupDecoder] (real `ContentResolver`/gzip-detection read), and [BackupRestorer.restore]'s real
 * orchestration entry point (real `BackupNotifier`, real coroutine `Job` sequencing), against the
 * app's own live Injekt-provided repositories -- not test doubles.
 *
 * KMK corrective slice A (2026-08-27): an independent review correctly found the PRIOR version of
 * this test unsafe by construction, not merely by intent. Its own doc comment claimed "no other
 * pair on this device is ever touched," which was true only narrowly for the identity-decision
 * table -- but it called `backupCreator.backup(uri, BackupOptions())` and
 * `backupRestorer.restore(uri, RestoreOptions())` with every default-enabled category (library
 * entries, categories, app settings, extension stores, source settings, saved searches/feeds, and
 * local tracker, in addition to the intended taste profile). Creation is read-only against the live
 * device, so that side was harmless, but the RESTORE call, with those categories enabled, could
 * restore library manga/categories into the live library, or overwrite live app/source
 * preferences, on whatever device happened to run it. Two independent fixes below, both required:
 * (1) [BackupOptions]/[RestoreOptions] are now constructed explicitly with EVERY category disabled
 * except `tasteProfile` -- the smallest category that can prove an identity-decision round trip at
 * all (identity decisions are gated as a whole by that one option; there is no finer-grained gate).
 * Confirmed by reading `BackupCreator.kt`/`BackupRestorer.kt`: every other field is checked with an
 * early `if (!options.X) return emptyList()` / `if (options.X) { ... }` guard before any repository
 * write, so this is a structural guarantee, not a hope -- see `BackupOptionsCategoryScopeSourceTest`
 * (host) for the source-guard proof, and the runtime before/after row-count check in this file's own
 * `try` block (see its own comment for the EXACT, deliberately narrow claim that check makes) for
 * one empirical data point on this run. (2) [DisposableTestEnvironmentGuard.assumeDisposableEnvironment]
 * is called FIRST, before any repository mutation, and SKIPS (via `org.junit.Assume`, never merely
 * documents) this test unless the device is a real emulator, the `.dev` debug application id, AND
 * (2026-08-28 hardening) a host-provisioned disposable-environment marker that only
 * `private/tools/provision_disposable_test_marker.ps1` ever writes -- see that guard's own KDoc for
 * why the third signal was added: the first two alone cannot distinguish the designated disposable
 * probe AVD from the preserved development emulator, since both are `.dev` emulators.
 *
 * Writes and reads a real `.tachibk`-shaped file in the app's own cache directory (never touches
 * external/SAF storage, so it needs no folder-picker interaction) and cleans it up unconditionally
 * in a `finally` block, alongside the scoped identity-decision cleanup.
 */
@RunWith(AndroidJUnit4::class)
class BackupIdentityDecisionDeviceRoundTripTest {

    /** The exact minimal category set this test needs -- see the class doc for why. */
    private fun minimalBackupOptions() = BackupOptions(
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

    private fun minimalRestoreOptions() = RestoreOptions(
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
    fun identityDecisionSurvivesRealDeviceBackupAndRestore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DisposableTestEnvironmentGuard.assumeDisposableEnvironment(context)

        val tasteRepository = Injekt.get<TasteRepository>()
        val getDecisions = Injekt.get<GetCrossSourceIdentityDecisions>()
        val mangaRepository = Injekt.get<MangaRepository>()
        val categoryRepository = Injekt.get<CategoryRepository>()

        val left = CrossSourceRecordKey(910_555_000_000_000_001L, "/f2-02-device-left")
        val right = CrossSourceRecordKey(910_555_000_000_000_002L, "/f2-02-device-right")
        val pair = CrossSourceIdentityDecisionPolicy.canonicalPair(left, right)
        val now = System.currentTimeMillis()

        // Empirical before/after evidence (item 6 of the corrective slice) alongside the structural
        // proof in BackupOptionsCategoryScopeSourceTest -- captured as plain row counts (cheap, no
        // need for a full-table hash) for the two tables this test's BackupCreator/BackupRestorer
        // machinery could touch if a category option ever regressed to enabled.
        //
        // KMK F2-02 hardening (2026-08-28): an independent review correctly found that "manga and
        // category row counts are unchanged" does NOT prove byte-identical preservation of every
        // preference, file, or unrelated taste-family row -- and this test never claimed the
        // broader thing, only the two specific counts below. Stated exactly and narrowly: this test
        // proves the MANGA table's row count and the CATEGORY table's row count are numerically
        // unchanged across a minimal-category (tasteProfile-only) backup/restore cycle. It does NOT
        // check: SharedPreferences/app-settings content, files outside the one .tachibk this test
        // itself creates and deletes, source settings, or any taste-family row other than the one
        // seeded identity-decision pair this test scopes all its own mutations to. Those remain
        // unverified by THIS specific test -- broadening it to a full preference/file fingerprint
        // was considered and deliberately deferred as disproportionate to this narrow gate: the
        // structural proof above (every other BackupOptions/RestoreOptions category is compile-time
        // `false`, and BackupCreator.kt/BackupRestorer.kt gate every category with an early return
        // before touching its owning repository) is what actually establishes that no unrelated
        // category's repository is invoked at all -- these two row counts are corroborating runtime
        // evidence for the two tables most plausibly reachable by a regression, not an independent
        // proof of the full claim on their own.
        val mangaCountBefore = mangaRepository.getAll().size
        val categoryCountBefore = categoryRepository.getAll().size

        val backupFile = File(context.cacheDir, "f2-02-device-e2e-$now.tachibk")
        val seeded = CrossSourceIdentityDecision(
            pair = pair,
            decision = CrossSourceIdentityDecisionValue.USER_CONFIRMED,
            decisionVersion = CrossSourceIdentityDecisionPolicy.CURRENT_DECISION_VERSION,
            evidenceVersion = CrossSourceIdentityDecisionPolicy.CURRENT_EVIDENCE_VERSION,
            reasonCodes = setOf(CrossSourceIdentityReasonCode.USER_CONFIRMATION),
            reviewState = CrossSourceIdentityReviewState.CURRENT,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        try {
            // Seed via the app's own live repository (same object production code writes through).
            // Uses deliberately unlikely-to-collide fixture source ids as defense in depth, but the
            // REAL safety boundary is minimalBackupOptions()/minimalRestoreOptions() above plus the
            // guard at the top of this test, not the id values themselves -- every subsequent
            // removal below is additionally scoped to exactly this one pair via
            // replaceCrossSourceIdentityDecision's compare-and-swap contract, never a
            // tombstone-all/delete-all call.
            tasteRepository.upsertCrossSourceIdentityDecisions(listOf(seeded))
            assertEquals(seeded, getDecisions.await(pair))

            // Real device-side backup creation: real Context, real UniFile file write, real gzip.
            // UniFile.fromUri(context, uri) (the non-auto-backup branch BackupCreator.backup uses)
            // expects the target to already exist as an openable file -- true for a real SAF
            // CreateDocument result, so the file is pre-created here to match that contract.
            check(backupFile.createNewFile()) { "could not pre-create the target backup file" }
            val backupCreator = BackupCreator(context = context, isAutoBackup = false)
            backupCreator.backup(Uri.fromFile(backupFile), minimalBackupOptions())
            assertTrue("backup file must exist after BackupCreator.backup()", backupFile.exists())
            assertTrue("backup file must be non-empty", backupFile.length() > 0)

            // Remove ONLY this one seeded pair before restoring (scoped compare-and-swap delete, not
            // a table-wide clear), so a successful post-restore read proves genuine restoration, not
            // merely that the row was already present -- and no other pair on this device is ever
            // touched.
            val removed = tasteRepository.replaceCrossSourceIdentityDecision(expected = seeded, replacement = null)
            assertTrue("scoped removal of only the seeded pair must succeed", removed)
            assertEquals(null, getDecisions.await(pair))

            // Real device-side restore: real BackupDecoder (ContentResolver read + gzip detection),
            // real BackupRestorer orchestration (the exact restoreTasteProfile -> restoreTasteProfileBundle
            // path production code uses), real BackupNotifier.
            val backupRestorer = BackupRestorer(context = context, notifier = BackupNotifier(context), isSync = false)
            val outcome = backupRestorer.restore(Uri.fromFile(backupFile), minimalRestoreOptions())
            assertTrue(
                "expected a clean restore outcome, got $outcome",
                outcome is BackupRestoreOutcome.Success,
            )

            val restored = getDecisions.await(pair)
            assertTrue("the identity decision must be restored", restored != null)
            assertEquals(CrossSourceIdentityDecisionValue.USER_CONFIRMED, restored!!.decision)
            assertEquals(CrossSourceIdentityReviewState.CURRENT, restored.reviewState)

            // Narrow, exact claim (see this test's own comment above where the "before" counts were
            // captured): with tasteProfile the only enabled category, the manga table's and the
            // category table's row COUNTS are unchanged by this run. This does not assert byte
            // identity of the rows themselves, nor anything about preferences, files, or other
            // taste-family tables -- see the comment above for why that broader claim is
            // deliberately not made by this test.
            assertEquals("manga row count must be unchanged by a tasteProfile-only backup/restore", mangaCountBefore, mangaRepository.getAll().size)
            assertEquals("category row count must be unchanged by a tasteProfile-only backup/restore", categoryCountBefore, categoryRepository.getAll().size)
        } finally {
            // Scoped cleanup only -- remove exactly the seeded pair (whichever value it currently
            // holds, restored or not), never anything else on the device.
            getDecisions.await(pair)?.let { current ->
                tasteRepository.replaceCrossSourceIdentityDecision(expected = current, replacement = null)
            }
            backupFile.delete()
        }
    }
}
// KMK <--
