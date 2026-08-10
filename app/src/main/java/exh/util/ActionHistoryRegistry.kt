package exh.util

import android.content.Context
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.InstallStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import mihon.domain.migration.usecases.MigrateMangaUseCase
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.DeleteTrack
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29 -->
/** One row the Action History screen can render, regardless of which journal family produced it. */
data class ActionHistoryEntryDescriptor(
    val id: String,
    val timestamp: Long,
    val summary: (Context) -> String,
    val undo: (suspend () -> ActionHistoryUndoResult)?,
    /** A safe, artifact-verified forward action (uninstall/reinstall) -- see [ActionHistoryFollowUp]. */
    val followUp: ActionHistoryFollowUp? = null,
)

/** Unifies every journal family's own undo-result shape into one small type for Snackbar text. */
sealed interface ActionHistoryUndoResult {
    data class Taste(val outcome: EvaluationUndoOutcome) : ActionHistoryUndoResult
    data class Simple(val result: GroupUndoResult) : ActionHistoryUndoResult
}

/**
 * A safe, artifact-verified follow-up action for a package-operation row (uninstall this exact
 * extension after an install/update, reinstall this exact extension after an uninstall) --
 * deliberately a distinct type from [ActionHistoryEntryDescriptor.undo]. A follow-up is never a
 * restore of previous state; it is a fresh, explicit new operation ([PackageOperationFollowUpPolicy]
 * already verified is currently safe against live package state), so the V2 plan requires it never
 * be labeled or presented as "Undo".
 */
data class ActionHistoryFollowUp(
    val label: (Context) -> String,
    val trigger: suspend () -> ActionHistoryFollowUpResult,
)

sealed interface ActionHistoryFollowUpResult {
    data object Started : ActionHistoryFollowUpResult
    data object Failed : ActionHistoryFollowUpResult
}

/**
 * A single journal family's adapter into the Action History registry.
 *
 * Every family that records a receipt while Evaluation Mode is enabled must have exactly one
 * [ActionHistorySource] registered in [ActionHistoryRegistry.sources]. Before this pass,
 * `EvaluationModeActionHistoryScreen` enumerated each journal by name in two separate, hand-kept-in-
 * sync places: `buildRows()` (row rendering) and the Clear All confirmation (clearing). That
 * duplication is exactly the class of bug the V2 plan's "exhaustive `when` is not an equivalent
 * registry" finding was about (Corrective Pass 1 report, and the plan's §4.5): the exhaustive `when`
 * inside each summary function guarantees every *known enum value* has rendering text, but nothing
 * guaranteed a new *journal family* was wired into both `buildRows()` and Clear All -- it was possible
 * to add one and forget the other. Registering a family here once is now the single place that
 * decides whether it appears in the screen at all; `ActionHistoryRegistry.snapshot()`/`clearAll()` are
 * the only two entry points the screen calls, and both walk the same [sources] list.
 */
// KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: `ActionHistorySource`
// itself is deliberately kept a plain (unsealed) interface -- `ActionHistoryRegistryTest.FakeSource`
// implements it from the test module to unit-test `mergeHistorySources`/`clearHistorySources` in
// isolation, and Kotlin forbids implementing a sealed type from outside the module that declares it
// (app/src/test and app/src/main compile as separate modules in this Gradle project), which broke that
// fake the first time this was tried. Registration completeness for the real production families lives
// in [JournalFamily] below instead.
interface ActionHistorySource {
    // KMK Confirmed Blocker Remediation Corrective Completion Plan V3 2026-07-29 Phase C: a stable,
    // human-assigned identity for this family. `ActionHistoryRegistryTest` asserts against the exact set
    // of expected ids (and that no two families share one) instead of a bare `sources.size == 7` count,
    // which could not tell an omitted family apart from an accidentally-duplicated one.
    val familyId: String
    fun snapshot(): List<ActionHistoryEntryDescriptor>
    fun clear()
}

// KMK Confirmed Blocker Remediation Corrective Completion Plan V3 2026-07-29 Phase C (gap closure,
// second pass): the first V3 gap-closure attempt kept each journal family as a separate
// `private object : KnownActionHistorySource`, with a *separate* enum (`JournalFamily`) plus an
// exhaustive `sourceForFamily()` `when` mapping enum entries to those objects, and derived the
// registered list from `JournalFamily.entries.map(::sourceForFamily)`. That closed the original defect
// (an already-declared, already-`when`-matched adapter being left out of a separately hand-kept list --
// there was no longer a separate list to leave it out of), but it still left a seam: the adapter object
// and the enum entry were two different declarations that had to be kept in sync by a human writing a
// `when` branch connecting them.
//
// This version collapses that seam entirely: [JournalFamily] itself directly implements
// [ActionHistorySource], with each enum constant providing its own `familyId`/`snapshot()`/`clear()`
// body (standard Kotlin enum-with-per-constant-bodies). There is no longer a separate adapter object, no
// separate mapping function, and no `when` to keep in sync with anything -- the enum constant *is* the
// adapter. `ActionHistoryRegistry.sources` is derived directly from `JournalFamily.entries`, so any
// family that exists as a `JournalFamily` constant is, structurally, always registered; there is no code
// path that can declare a `JournalFamily` constant without it appearing in `sources`.
//
// What this still cannot prove, stated honestly: a `ActionHistorySource` implementation that is never
// declared as a `JournalFamily` constant at all -- i.e., someone writes a free-floating
// `private object : ActionHistorySource` elsewhere in this file, or a class, without ever adding it as an
// enum entry here -- would not automatically appear in `sources`. Making that structurally impossible
// would require enumerating "every type implementing ActionHistorySource in this file" without being
// told where to look, which needs reflection or classpath scanning -- a dependency this project does not
// have and which was assessed and declined as disproportionate (see the V2 report). Note this residual is
// no longer about an *already-declared, already-referenced* adapter being silently dropped (that failure
// mode is now structurally impossible); it is only about a *brand-new* family that a developer chooses
// not to route through this file's own registration mechanism in the first place -- a code-review-level
// concern for any future family, not a defect in how the 7 currently-declared families are registered.
private enum class JournalFamily : ActionHistorySource {
    TASTE {
        override val familyId: String = "taste"

        override fun snapshot(): List<ActionHistoryEntryDescriptor> =
            EvaluationModeUndoJournal.snapshot().map { entry ->
                ActionHistoryEntryDescriptor(
                    id = "taste:${entry.id}",
                    timestamp = entry.timestamp,
                    summary = { ctx -> evaluationJournalEntrySummary(ctx, entry) },
                    undo = {
                        val service = EvaluationModeUndoService()
                        val outcome = if (entry.bulkOperationId != null) {
                            service.undoBulk(entry.bulkOperationId)
                        } else {
                            service.undo(entry.id)
                        }
                        ActionHistoryUndoResult.Taste(outcome)
                    },
                )
            }

        override fun clear() = EvaluationModeUndoJournal.clear()
    },

    GROUP {
        override val familyId: String = "group"

        override fun snapshot(): List<ActionHistoryEntryDescriptor> =
            GroupUndoJournal.snapshot().map { entry ->
                ActionHistoryEntryDescriptor(
                    id = "group:${entry.id}",
                    timestamp = entry.timestamp,
                    summary = { ctx -> groupJournalEntrySummary(ctx, entry) },
                    undo = { ActionHistoryUndoResult.Simple(GroupUndoService().undo(entry.id).result) },
                )
            }

        override fun clear() = GroupUndoJournal.clear()
    },

    LIBRARY {
        override val familyId: String = "library"

        override fun snapshot(): List<ActionHistoryEntryDescriptor> =
            LibraryUndoJournal.snapshot().map { entry ->
                ActionHistoryEntryDescriptor(
                    id = "library:${entry.id}",
                    timestamp = entry.timestamp,
                    summary = { ctx -> libraryJournalEntrySummary(ctx, entry) },
                    undo = { ActionHistoryUndoResult.Simple(LibraryUndoService().undo(entry.id)) },
                )
            }

        override fun clear() = LibraryUndoJournal.clear()
    },

    PREFERENCE {
        override val familyId: String = "preference"

        override fun snapshot(): List<ActionHistoryEntryDescriptor> =
            PreferenceUndoJournal.snapshot().map { entry ->
                ActionHistoryEntryDescriptor(
                    id = "pref:${entry.id}",
                    timestamp = entry.timestamp,
                    summary = { ctx -> preferenceJournalEntrySummary(ctx, entry) },
                    undo = { ActionHistoryUndoResult.Simple(PreferenceUndoService().undo(entry.id)) },
                )
            }

        override fun clear() = PreferenceUndoJournal.clear()
    },

    CHAPTER {
        override val familyId: String = "chapter"

        override fun snapshot(): List<ActionHistoryEntryDescriptor> =
            ChapterUndoJournal.snapshot().map { entry ->
                ActionHistoryEntryDescriptor(
                    id = "chapter:${entry.id}",
                    timestamp = entry.timestamp,
                    summary = { ctx -> chapterJournalEntrySummary(ctx, entry) },
                    undo = { ActionHistoryUndoResult.Simple(ChapterUndoService().undo(entry.id)) },
                )
            }

        override fun clear() = ChapterUndoJournal.clear()
    },

    COVER {
        override val familyId: String = "cover"

        override fun snapshot(): List<ActionHistoryEntryDescriptor> =
            CustomCoverUndoJournal.snapshot().map { entry ->
                ActionHistoryEntryDescriptor(
                    id = "cover:${entry.id}",
                    timestamp = entry.timestamp,
                    summary = { ctx -> ctx.stringResource(KMR.strings.eval_undo_summary_custom_cover) },
                    undo = { ActionHistoryUndoResult.Simple(CustomCoverUndoService().undo(entry.id)) },
                )
            }

        // KMK v0.8.20-fix1: custom-cover entries own a real cached file on disk in addition to the
        // journal record -- clearing the journal without also cleaning up that file would leak it.
        override fun clear() {
            CustomCoverUndoJournal.snapshot().forEach { CustomCoverUndoStorage.cleanup(it) }
            CustomCoverUndoJournal.clear()
        }
    },

    NONUNDOABLE {
        override val familyId: String = "nonundoable"

        override fun snapshot(): List<ActionHistoryEntryDescriptor> =
            NonUndoableEventJournal.snapshot().map { event ->
                ActionHistoryEntryDescriptor(
                    id = "event:${event.id}",
                    timestamp = event.timestamp,
                    summary = { ctx -> nonUndoableEventSummary(ctx, event) },
                    undo = null,
                    followUp = packageFollowUpFor(event) ?: migrationFollowUpFor(event) ?: downloadFollowUpFor(event) ?: trackerFollowUpFor(event) ?: trackerBindingFollowUpFor(event),
                )
            }

        // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: correlates a
        // rendered NonUndoableEvent row back to its private PackageOperationReceipt twin (same shared id,
        // see EvaluationModeInstallEventRecorder.kt's [id] parameter docs) and, only if
        // PackageOperationFollowUpPolicy currently judges it safe against live package state, exposes a
        // real "Uninstall"/"Reinstall" follow-up action. A refused eligibility (package already changed,
        // artifact gone, etc.) simply means no follow-up is offered -- the row still renders as
        // not-undoable, exactly as before.
        private fun packageFollowUpFor(event: NonUndoableEvent): ActionHistoryFollowUp? {
            if (event.eventType !in setOf(
                    NonUndoableEventType.EXTENSION_INSTALLED,
                    NonUndoableEventType.EXTENSION_UPDATED,
                    NonUndoableEventType.EXTENSION_UNINSTALLED,
                )
            ) {
                return null
            }
            val receipt = PackageOperationJournal.snapshot().firstOrNull { it.id == event.id } ?: return null
            val extensionManager = actionHistoryFollowUpExtensionManagerProvider()
            val sourcePreferences = actionHistoryFollowUpSourcePreferencesProvider()

            return when (receipt.kind) {
                PackageOperationKind.INSTALL, PackageOperationKind.UPDATE -> {
                    val currentlyInstalled = extensionManager.installedExtensionsFlow.value.find { it.pkgName == receipt.packageName }
                    val eligibility = PackageOperationFollowUpPolicy.evaluateUninstallFollowUp(receipt, currentlyInstalled)
                    if (eligibility != PackageOperationFollowUpPolicy.UninstallFollowUp.Offered || currentlyInstalled == null) return null
                    ActionHistoryFollowUp(
                        label = { ctx -> ctx.stringResource(MR.strings.ext_uninstall) },
                        trigger = {
                            extensionManager.uninstallExtension(currentlyInstalled)
                            val removed = verifyAndRecordUninstall(
                                installedPackageNames = extensionManager.installedExtensionsFlow.map { installed -> installed.map { it.pkgName } },
                                pkgName = receipt.packageName,
                                isEvaluationModeEnabled = { sourcePreferences.evaluationMode().get() },
                                signatureHash = currentlyInstalled.signatureHash,
                                versionCode = currentlyInstalled.versionCode,
                            )
                            if (removed) ActionHistoryFollowUpResult.Started else ActionHistoryFollowUpResult.Failed
                        },
                    )
                }
                PackageOperationKind.UNINSTALL -> {
                    val currentlyInstalled = extensionManager.installedExtensionsFlow.value.find { it.pkgName == receipt.packageName }
                    val availableMatch = extensionManager.availableExtensionsFlow.value.find {
                        it.pkgName == receipt.packageName && it.signatureHash == receipt.signatureHash
                    }
                    val eligibility = PackageOperationFollowUpPolicy.evaluateReinstallFollowUp(receipt, currentlyInstalled, availableMatch)
                    if (eligibility != PackageOperationFollowUpPolicy.ReinstallFollowUp.Offered || availableMatch == null) return null
                    ActionHistoryFollowUp(
                        label = { ctx -> ctx.stringResource(MR.strings.ext_install) },
                        trigger = {
                            val newReceiptId = NonUndoableEvent.newId()
                            val finalStep = extensionManager.installExtension(availableMatch)
                                .recordUserInitiatedInstall(id = newReceiptId) { sourcePreferences.evaluationMode().get() }
                                .recordPackageOperationReceipt(
                                    kind = PackageOperationKind.INSTALL,
                                    packageName = availableMatch.pkgName,
                                    signatureHash = availableMatch.signatureHash,
                                    versionCode = availableMatch.versionCode,
                                    artifactUri = availableMatch.apkUrl,
                                    id = newReceiptId,
                                ) { sourcePreferences.evaluationMode().get() }
                                .first { it == InstallStep.Installed || it == InstallStep.Error }
                            if (finalStep == InstallStep.Installed) ActionHistoryFollowUpResult.Started else ActionHistoryFollowUpResult.Failed
                        },
                    )
                }
            }
        }

        // KMK Universal Action History Recovery Plan 2026-07-31: correlates a rendered
        // MIGRATION_COMPLETED event back to its private MigrationReceipt twin (same shared id) and
        // exposes a "Migrate back" follow-up -- a fresh reverse migration through the same
        // MigrateMangaUseCase, never a database rollback, never labeled "Undo". Unlike
        // packageFollowUpFor, MigrationFollowUpPolicy's eligibility cannot be pre-checked here:
        // ActionHistorySource.snapshot() is a synchronous call (it drives Compose row rendering) and
        // GetManga.await(...) is suspend, so there is no cheap synchronous manga read to gate the
        // offer on. The follow-up is therefore always offered once a receipt exists; the full,
        // authoritative eligibility check (fresh manga/source state, not a stale snapshot()-time
        // read) runs inside [trigger] itself, which safely refuses (Failed, no records written) when
        // ineligible.
        private fun migrationFollowUpFor(event: NonUndoableEvent): ActionHistoryFollowUp? {
            if (event.eventType != NonUndoableEventType.MIGRATION_COMPLETED) return null
            val receipt = MigrationReceiptJournal.forId(event.id) ?: return null
            val getManga = actionHistoryFollowUpGetMangaProvider()
            val sourceManager = actionHistoryFollowUpSourceManagerProvider()
            val sourcePreferences = actionHistoryFollowUpSourcePreferencesProvider()
            val migrateMangaUseCase = actionHistoryFollowUpMigrateMangaUseCaseProvider()

            return ActionHistoryFollowUp(
                label = { ctx -> ctx.stringResource(KMR.strings.eval_undo_followup_migrate_back) },
                trigger = trigger@{
                    // Re-resolved fresh at trigger time (not reused from a stale snapshot()-time
                    // read) so a conflict that appeared between rendering and tapping is still caught.
                    val originManga = getManga.await(receipt.originMangaId)
                    val targetManga = getManga.await(receipt.targetMangaId)
                    val originSourceInstalled = sourceManager.get(receipt.originSourceId) != null
                    val eligibility = MigrationFollowUpPolicy.evaluate(originManga, originSourceInstalled, targetManga)
                    if (eligibility != MigrationFollowUpPolicy.MigrateBackFollowUp.Offered ||
                        originManga == null || targetManga == null
                    ) {
                        return@trigger ActionHistoryFollowUpResult.Failed
                    }
                    when (
                        val outcome = migrateMangaUseCase(
                            current = targetManga,
                            target = originManga,
                            replace = receipt.replace,
                        )
                    ) {
                        is mihon.domain.migration.usecases.MigrationOutcome.Success -> {
                            if (sourcePreferences.evaluationMode().get()) {
                                val newId = NonUndoableEvent.newId()
                                NonUndoableEventJournal.record(
                                    NonUndoableEvent(
                                        id = newId,
                                        timestamp = System.currentTimeMillis(),
                                        eventType = NonUndoableEventType.MIGRATION_COMPLETED,
                                    ),
                                )
                                MigrationReceiptJournal.record(
                                    MigrationReceipt(
                                        id = newId,
                                        timestamp = System.currentTimeMillis(),
                                        originMangaId = targetManga.id,
                                        originSourceId = targetManga.source,
                                        targetMangaId = originManga.id,
                                        targetSourceId = originManga.source,
                                        replace = receipt.replace,
                                    ),
                                )
                            }
                            ActionHistoryFollowUpResult.Started
                        }
                        is mihon.domain.migration.usecases.MigrationOutcome.PartialFailure,
                        is mihon.domain.migration.usecases.MigrationOutcome.NotStarted,
                        -> ActionHistoryFollowUpResult.Failed
                    }
                },
            )
        }

        // KMK Universal Action History Recovery Plan 2026-08-01: correlates a rendered DOWNLOAD_DELETED
        // event back to its private DownloadReceipt twin and offers a "Re-download" follow-up -- a
        // fresh re-queue of exactly the originally-deleted chapter ids through the ordinary
        // DownloadManager.downloadChapters() enqueue path, never a restore. Like migrationFollowUpFor,
        // eligibility cannot be pre-checked here (snapshot() is synchronous, GetManga/GetChapter.await
        // are suspend), so the follow-up is always offered once a receipt exists; the authoritative
        // check runs inside trigger(). "Started" here means "successfully enqueued", not "downloaded" --
        // downloads are asynchronous and queue-based throughout this app, matching the ordinary Download
        // button's own UX.
        private fun downloadFollowUpFor(event: NonUndoableEvent): ActionHistoryFollowUp? {
            if (event.eventType != NonUndoableEventType.DOWNLOAD_DELETED) return null
            val receipt = DownloadReceiptJournal.forId(event.id) ?: return null
            val getManga = actionHistoryFollowUpGetMangaProvider()
            val getChapter = actionHistoryFollowUpGetChapterProvider()
            val sourceManager = actionHistoryFollowUpSourceManagerProvider()
            val downloadManager = actionHistoryFollowUpDownloadManagerProvider()

            return ActionHistoryFollowUp(
                label = { ctx -> ctx.stringResource(KMR.strings.eval_undo_followup_redownload) },
                trigger = trigger@{
                    // Re-resolved fresh at trigger time -- see migrationFollowUpFor's own comment for why.
                    val manga = getManga.await(receipt.mangaId)
                    val sourceInstalled = sourceManager.get(receipt.sourceId) != null
                    val resolvedChapters = receipt.chapterIds.mapNotNull { getChapter.await(it) }
                    val eligibility = DownloadFollowUpPolicy.evaluate(manga, sourceInstalled, resolvedChapters)
                    if (eligibility !is DownloadFollowUpPolicy.RedownloadFollowUp.Offered || manga == null) {
                        return@trigger ActionHistoryFollowUpResult.Failed
                    }
                    downloadManager.downloadChapters(manga, eligibility.resolvedChapters)
                    ActionHistoryFollowUpResult.Started
                },
            )
        }

        // KMK Universal Action History Recovery Plan 2026-08-01: correlates a tracker-write event
        // with its private typed receipt. The trigger re-resolves the local manga/track and login state
        // before making a fresh compensating sync; it never mutates the database directly and never
        // labels the operation "Undo".
        private fun trackerFollowUpFor(event: NonUndoableEvent): ActionHistoryFollowUp? {
            if (event.eventType != NonUndoableEventType.TRACKER_WRITE_COMPLETED) return null
            val receipt = TrackWriteReceiptJournal.forId(event.id) ?: return null
            val getManga = actionHistoryFollowUpGetMangaProvider()
            val getTracks = actionHistoryFollowUpGetTracksProvider()
            val trackerManager = actionHistoryFollowUpTrackerManagerProvider()
            val sourcePreferences = actionHistoryFollowUpSourcePreferencesProvider()
            val label = when (receipt.field) {
                TrackWriteField.STATUS -> KMR.strings.eval_undo_followup_tracker_status
                TrackWriteField.SCORE -> KMR.strings.eval_undo_followup_tracker_score
                TrackWriteField.CHAPTER_PROGRESS -> KMR.strings.eval_undo_followup_tracker_progress
                TrackWriteField.START_DATE -> KMR.strings.eval_undo_followup_tracker_start_date
                TrackWriteField.FINISH_DATE -> KMR.strings.eval_undo_followup_tracker_finish_date
                TrackWriteField.PRIVATE -> KMR.strings.eval_undo_followup_tracker_private
            }

            return ActionHistoryFollowUp(
                label = { ctx -> ctx.stringResource(label) },
                trigger = trigger@{
                    val manga = getManga.await(receipt.mangaId)
                    val track = manga?.let { getTracks.await(it.id).firstOrNull { item -> item.trackerId == receipt.trackerId } }
                    val tracker = trackerManager.get(receipt.trackerId)
                    val trackerLoggedIn = tracker != null && trackerManager.loggedInTrackersFlow().first().any { it.id == receipt.trackerId }
                    if (tracker == null || TrackFollowUpPolicy.evaluate(manga, track, trackerLoggedIn) !is TrackFollowUpPolicy.RestoreFollowUp.Offered) {
                        return@trigger ActionHistoryFollowUpResult.Failed
                    }
                    val currentTrack = track!!

                    try {
                        when (receipt.field) {
                            TrackWriteField.STATUS -> tracker.setRemoteStatus(
                                currentTrack.toDbTrack(),
                                receipt.previousStatus ?: return@trigger ActionHistoryFollowUpResult.Failed,
                            )
                            TrackWriteField.SCORE -> tracker.setRemoteScore(
                                currentTrack.toDbTrack(),
                                receipt.previousScore ?: return@trigger ActionHistoryFollowUpResult.Failed,
                            )
                            TrackWriteField.CHAPTER_PROGRESS -> tracker.setRemoteLastChapterRead(
                                currentTrack.toDbTrack(),
                                receipt.previousChapterProgress ?: return@trigger ActionHistoryFollowUpResult.Failed,
                            )
                            TrackWriteField.START_DATE -> tracker.setRemoteStartDate(
                                currentTrack.toDbTrack(),
                                receipt.previousStartDate ?: return@trigger ActionHistoryFollowUpResult.Failed,
                            )
                            TrackWriteField.FINISH_DATE -> tracker.setRemoteFinishDate(
                                currentTrack.toDbTrack(),
                                receipt.previousFinishDate ?: return@trigger ActionHistoryFollowUpResult.Failed,
                            )
                            TrackWriteField.PRIVATE -> tracker.setRemotePrivate(
                                currentTrack.toDbTrack(),
                                receipt.previousPrivate ?: return@trigger ActionHistoryFollowUpResult.Failed,
                            )
                        }
                        recordSuccessfulTrackWrite(
                            evaluationModeEnabled = sourcePreferences.evaluationMode().get(),
                            mangaId = receipt.mangaId,
                            trackerId = receipt.trackerId,
                            field = receipt.field,
                            previousStatus = currentTrack.status.takeIf { receipt.field == TrackWriteField.STATUS },
                            previousScore = tracker.displayScore(currentTrack).takeIf { receipt.field == TrackWriteField.SCORE },
                            previousChapterProgress = currentTrack.lastChapterRead.toInt().takeIf { receipt.field == TrackWriteField.CHAPTER_PROGRESS },
                            previousStartDate = currentTrack.startDate.takeIf { receipt.field == TrackWriteField.START_DATE },
                            previousFinishDate = currentTrack.finishDate.takeIf { receipt.field == TrackWriteField.FINISH_DATE },
                            previousPrivate = currentTrack.private.takeIf { receipt.field == TrackWriteField.PRIVATE },
                        )
                        ActionHistoryFollowUpResult.Started
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        ActionHistoryFollowUpResult.Failed
                    }
                },
            )
        }

        private fun trackerBindingFollowUpFor(event: NonUndoableEvent): ActionHistoryFollowUp? {
            if (event.eventType != NonUndoableEventType.TRACKER_BOUND) return null
            val receipt = TrackerBindingReceiptJournal.forId(event.id) ?: return null
            val tracker = actionHistoryFollowUpTrackerManagerProvider().get(receipt.trackerId)

            return ActionHistoryFollowUp(
                label = { ctx -> ctx.stringResource(KMR.strings.eval_undo_followup_tracker_unbind) },
                trigger = trigger@{
                    val currentTrack = actionHistoryFollowUpGetTracksProvider()
                        .await(receipt.mangaId)
                        .firstOrNull { it.trackerId == receipt.trackerId && it.remoteId == receipt.remoteId }
                    val decision = TrackerBindingFollowUpPolicy.evaluate(
                        currentTrack = currentTrack,
                        deletableTracker = tracker as? DeletableTracker,
                        trackerLoggedIn = tracker?.isLoggedIn == true,
                        expectedRemoteId = receipt.remoteId,
                    )
                    if (decision !is TrackerBindingFollowUpPolicy.Decision.Offered || tracker !is DeletableTracker) {
                        return@trigger ActionHistoryFollowUpResult.Failed
                    }
                    val matchedTrack = currentTrack ?: return@trigger ActionHistoryFollowUpResult.Failed

                    try {
                        tracker.delete(matchedTrack)
                        actionHistoryFollowUpDeleteTrackProvider().await(receipt.mangaId, receipt.trackerId)
                        NonUndoableEventJournal.record(
                            NonUndoableEvent(
                                id = NonUndoableEvent.newId(),
                                timestamp = System.currentTimeMillis(),
                                eventType = NonUndoableEventType.TRACKER_UNBOUND,
                            ),
                        )
                        ActionHistoryFollowUpResult.Started
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        ActionHistoryFollowUpResult.Failed
                    }
                },
            )
        }

        // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: PackageOperationJournal
        // is NonUndoableEventJournal's twin for package operations -- same events, but carrying private
        // identity/signature/version/artifact metadata for PackageOperationFollowUpPolicy instead of public
        // display text. It is deliberately not its own ActionHistorySource (its entries are not separately
        // rendered -- NonUndoableEventJournal already renders one row per package operation), but it must
        // still be cleared whenever Action History is cleared so no private package metadata outlives the
        // visible event it belongs to.
        override fun clear() {
            NonUndoableEventJournal.clear()
            PackageOperationJournal.clear()
            // KMK Universal Action History Recovery Plan 2026-07-31
            MigrationReceiptJournal.clear()
            // KMK Universal Action History Recovery Plan 2026-08-01
            DownloadReceiptJournal.clear()
            TrackWriteReceiptJournal.clear()
            TrackerBindingReceiptJournal.clear()
        }
    },
}

// KMK Confirmed Blocker Remediation Corrective Completion Plan V3 2026-07-29 Phase D item 3: a narrow,
// direct test seam for NONUNDOABLE's packageFollowUpFor()'s two dependencies -- deliberately NOT routed
// through Injekt.get() at test time, and deliberately module-level `internal` (not a member of the enum
// constant body itself) so a test in app/src/test can reach it without needing JournalFamily's constants
// to be non-private. The prior attempt at testing ActionHistoryFollowUp.trigger() registered a mock into
// Injekt itself and hit Injekt's process-wide singleton caching (the first resolved instance for a type is
// cached for the whole JVM process, so a fresh per-test mock was silently ignored after the first test
// resolved it) combined with a separate wrong assumption about onDispose() cancelling screenModelScope --
// together those caused a real ~1-hour Gradle daemon hang, and that trigger test was deleted rather than
// fixed. These providers bypass Injekt's cache entirely: a test sets them directly to its own mocks, and
// resets them to their real Injekt-backed defaults in @AfterEach -- no shared process-wide cache, no
// fixture-ordering hazard.
internal var actionHistoryFollowUpExtensionManagerProvider: () -> ExtensionManager = { Injekt.get() }
internal var actionHistoryFollowUpSourcePreferencesProvider: () -> SourcePreferences = { Injekt.get() }

// KMK Universal Action History Recovery Plan 2026-07-31: same test-seam-provider pattern as the two
// above, for migrationFollowUpFor()'s dependencies.
internal var actionHistoryFollowUpGetMangaProvider: () -> GetManga = { Injekt.get() }
internal var actionHistoryFollowUpSourceManagerProvider: () -> SourceManager = { Injekt.get() }
internal var actionHistoryFollowUpMigrateMangaUseCaseProvider: () -> MigrateMangaUseCase = { Injekt.get() }

// KMK Universal Action History Recovery Plan 2026-08-01: same test-seam-provider pattern as the three
// above, for downloadFollowUpFor()'s dependencies.
internal var actionHistoryFollowUpGetChapterProvider: () -> GetChapter = { Injekt.get() }
internal var actionHistoryFollowUpDownloadManagerProvider: () -> DownloadManager = { Injekt.get() }

// KMK Universal Action History Recovery Plan 2026-08-01: test seams for tracker compensating sync.
internal var actionHistoryFollowUpGetTracksProvider: () -> GetTracks = { Injekt.get() }
internal var actionHistoryFollowUpTrackerManagerProvider: () -> TrackerManager = { Injekt.get() }
internal var actionHistoryFollowUpDeleteTrackProvider: () -> DeleteTrack = { Injekt.get() }

/**
 * Every known [ActionHistorySource] implementation, derived directly from [JournalFamily]'s own
 * enumeration -- see that enum's doc comment for exactly what guarantee this does and does not provide.
 */
private fun allActionHistorySources(): List<ActionHistorySource> {
    val known: List<ActionHistorySource> = JournalFamily.entries
    // Retained as an independent runtime check: two different JournalFamily constants could still (by a
    // copy-paste mistake) be given the same familyId string, which this catches at startup rather than
    // silently.
    check(known.map { it.familyId }.distinct().size == known.size) {
        "ActionHistoryRegistry has a duplicate familyId among: ${known.map { it.familyId }}"
    }
    return known
}

/** Most-recent-first across every source, matching every individual journal's own contract. */
fun mergeHistorySources(sources: List<ActionHistorySource>): List<ActionHistoryEntryDescriptor> =
    sources.flatMap { it.snapshot() }.sortedByDescending { it.timestamp }

fun clearHistorySources(sources: List<ActionHistorySource>) {
    sources.forEach { it.clear() }
}

object ActionHistoryRegistry {
    val sources: List<ActionHistorySource> = allActionHistorySources()

    fun snapshot(): List<ActionHistoryEntryDescriptor> = mergeHistorySources(sources)

    fun clearAll() = clearHistorySources(sources)
}

// KMK: moved out of EvaluationModeActionHistoryScreen.kt so ActionHistorySource adapters above can
// reference them -- Kotlin top-level `private` is file-scoped, not package-scoped.
private fun evaluationJournalEntrySummary(context: Context, entry: EvaluationJournalEntry): String {
    val isBulk = entry.bulkOperationId != null
    val count = if (isBulk) EvaluationModeUndoJournal.entriesForBulk(entry.bulkOperationId!!).size.coerceAtLeast(1) else 1
    return when (entry.actionType) {
        EvaluationJournalActionType.RATE_LOVE -> context.stringResource(KMR.strings.eval_undo_summary_rated, count, context.stringResource(KMR.strings.rated_manga_rating_love))
        EvaluationJournalActionType.RATE_LIKE -> context.stringResource(KMR.strings.eval_undo_summary_rated, count, context.stringResource(KMR.strings.rated_manga_rating_like))
        EvaluationJournalActionType.RATE_DISLIKE -> context.stringResource(KMR.strings.eval_undo_summary_rated, count, context.stringResource(KMR.strings.rated_manga_rating_dislike))
        EvaluationJournalActionType.CLEAR_RATING -> context.stringResource(KMR.strings.eval_undo_summary_cleared, count)
        EvaluationJournalActionType.NOT_INTERESTED -> context.stringResource(KMR.strings.eval_undo_summary_not_interested, count)
    }
}

private fun groupJournalEntrySummary(context: Context, entry: GroupJournalEntry): String = when (entry.actionType) {
    GroupJournalActionType.MERGE -> context.stringResource(KMR.strings.eval_undo_summary_group_merge, entry.touchedKeys.size)
    GroupJournalActionType.REMOVE_FROM_GROUP -> context.stringResource(KMR.strings.eval_undo_summary_group_remove, entry.touchedKeys.size)
    GroupJournalActionType.UNGROUP -> context.stringResource(KMR.strings.eval_undo_summary_group_ungroup, entry.touchedKeys.size)
    GroupJournalActionType.SET_PRIMARY -> context.stringResource(KMR.strings.eval_undo_summary_group_primary)
}

private fun chapterJournalEntrySummary(context: Context, entry: ChapterJournalEntry): String = when (entry.actionType) {
    ChapterJournalActionType.READ -> context.stringResource(KMR.strings.eval_undo_summary_chapter_read)
    ChapterJournalActionType.UNREAD -> context.stringResource(KMR.strings.eval_undo_summary_chapter_unread)
    ChapterJournalActionType.BOOKMARK -> context.stringResource(KMR.strings.eval_undo_summary_chapter_bookmark)
    ChapterJournalActionType.UNBOOKMARK -> context.stringResource(KMR.strings.eval_undo_summary_chapter_unbookmark)
}

private fun libraryJournalEntrySummary(context: Context, entry: LibraryJournalEntry): String {
    val count = entry.bulkOperationId?.let { LibraryUndoJournal.entriesForBulk(it).size.coerceAtLeast(1) } ?: 1
    return when (entry.actionType) {
        LibraryJournalActionType.FAVORITE -> context.stringResource(KMR.strings.eval_undo_summary_library_favorite, count)
        LibraryJournalActionType.UNFAVORITE -> context.stringResource(KMR.strings.eval_undo_summary_library_unfavorite, count)
        LibraryJournalActionType.SET_CATEGORIES -> context.stringResource(KMR.strings.eval_undo_summary_library_categories, count)
    }
}

private fun preferenceJournalEntrySummary(context: Context, entry: PreferenceUndoEntry<*>): String {
    return when (entry.actionType) {
        PreferenceJournalActionType.RATED_MANGA_VISIBILITY -> context.stringResource(KMR.strings.eval_undo_summary_preference_display)
        PreferenceJournalActionType.HIDE_KNOWN_MANGA,
        PreferenceJournalActionType.MIN_CHAPTER_COUNT,
        PreferenceJournalActionType.ENRICHMENT_CAP,
        PreferenceJournalActionType.RESULT_BUDGET,
        PreferenceJournalActionType.GROUP_PREVIEW_BUDGET,
        PreferenceJournalActionType.RECOMMENDATION_LANGUAGES,
        PreferenceJournalActionType.SOURCE_ORDER,
        PreferenceJournalActionType.SOURCE_EXCLUSION,
        PreferenceJournalActionType.SAME_MANGA_MATCHING,
        PreferenceJournalActionType.BEST_VERSION_PREVIEW,
        PreferenceJournalActionType.SOURCE_PREFERENCE,
        -> context.stringResource(KMR.strings.eval_undo_summary_preference_setting)
        PreferenceJournalActionType.TAG_PREFERENCE -> context.stringResource(KMR.strings.eval_undo_summary_preference_tag)
        PreferenceJournalActionType.SUGGESTION_DISMISSAL,
        PreferenceJournalActionType.DISMISSED_SUGGESTIONS_CLEAR,
        -> context.stringResource(KMR.strings.eval_undo_summary_preference_suggestion)
        PreferenceJournalActionType.SOURCE_QUALITY_MARK,
        PreferenceJournalActionType.SOURCE_QUALITY_CLEAR_ALL,
        -> context.stringResource(KMR.strings.eval_undo_summary_preference_source_quality)
        PreferenceJournalActionType.READING_SCHEDULE -> context.stringResource(KMR.strings.eval_undo_summary_preference_schedule)
    }
}

private fun nonUndoableEventSummary(context: Context, event: NonUndoableEvent): String = when (event.eventType) {
    NonUndoableEventType.MIGRATION_COMPLETED -> context.stringResource(KMR.strings.eval_undo_summary_migration_completed)
    NonUndoableEventType.EXTENSION_INSTALLED -> context.stringResource(KMR.strings.eval_undo_summary_extension_installed)
    NonUndoableEventType.EXTENSION_UPDATED -> context.stringResource(KMR.strings.eval_undo_summary_extension_updated)
    NonUndoableEventType.EXTENSION_UNINSTALLED -> context.stringResource(KMR.strings.eval_undo_summary_extension_uninstalled)
    NonUndoableEventType.BACKUP_RESTORED -> context.stringResource(KMR.strings.eval_undo_summary_backup_restored)
    NonUndoableEventType.DOWNLOAD_DELETED -> context.stringResource(KMR.strings.eval_undo_summary_download_deleted)
    NonUndoableEventType.TRACKER_WRITE_COMPLETED -> context.stringResource(KMR.strings.eval_undo_summary_tracker_write)
    NonUndoableEventType.TRACKER_BOUND -> context.stringResource(KMR.strings.eval_undo_summary_tracker_bound)
    NonUndoableEventType.TRACKER_UNBOUND -> context.stringResource(KMR.strings.eval_undo_summary_tracker_unbound)
    NonUndoableEventType.SOURCE_EVALUATION_DATA_CLEARED -> context.stringResource(KMR.strings.eval_undo_summary_source_evaluation_data_cleared)
}
// KMK <--
