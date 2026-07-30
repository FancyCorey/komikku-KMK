package mihon.domain.migration.usecases

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.rethrowIfFatal
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.toChapterUpdate
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import java.time.Instant

// KMK Confirmed Blocker Remediation Phase 4 2026-07-29 -->
/**
 * Not a transaction: this performs a remote target refresh, chapter/history copy, category
 * replacement, optional enhanced-tracker migration, optional download deletion, cover copy, and a
 * final manga-row update in sequence. There is no local database transaction wrapping the whole
 * operation, and several steps are irreversible external side effects (`REMOVE_DOWNLOAD` deletes
 * real files; enhanced-tracker migration calls a remote service) that this use case never attempts
 * to undo.
 *
 * See [MigrationOutcome] for the confirmed defect this class was changed to fix: [invoke] previously
 * returned `Unit`, and a non-fatal exception partway through was silently caught and discarded
 * (rethrown only if [rethrowIfFatal] classified it as fatal), so a caller had no way to distinguish
 * a genuine success from a silent partial failure. It now returns [MigrationOutcome], which reports
 * exactly which flag-gated steps completed before any failure -- but this is a truthful *report*,
 * not an undo: none of the side effects those completed steps produced are reversed by this class.
 *
 * Deliberately out of scope for this fix (see the implementation plan's Phase 4 §8, items 2 and 4):
 * staged local-state retention for a supported partial reversal, and an explicit `reverseMigration`
 * use case for "migrate back." Both would require a new bounded snapshot/staging store for chapter/
 * history/category pre-state, which is a materially larger, separate feature with its own conflict-
 * check and retention-bound design -- not implemented here. A caller that needs to send a user back
 * to their original source must perform a fresh, ordinary migration in the other direction with its
 * own preflight; that is not something this class needs to special-case.
 */
class MigrateMangaUseCase(
    private val sourcePreferences: SourcePreferences,
    private val trackerManager: TrackerManager,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val updateManga: UpdateManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val updateChapter: UpdateChapter,
    private val getCategories: GetCategories,
    private val setMangaCategories: SetMangaCategories,
    private val getTracks: GetTracks,
    private val insertTrack: InsertTrack,
    private val coverCache: CoverCache,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    // KMK -->
    private val getHistory: GetHistory,
    private val upsertHistory: UpsertHistory,
    // KMK <--
) {
    private val enhancedServices by lazy { trackerManager.trackers.filterIsInstance<EnhancedTracker>() }

    suspend operator fun invoke(
        current: Manga,
        target: Manga,
        replace: Boolean,
        // KMK -->
        presetFlags: Set<MigrationFlag>? = null,
        // KMK <--
        // SY -->
        throttleFunc: suspend () -> Unit = {},
        // SY <--
        // KMK Confirmed Blocker Remediation Phase 4 -->
    ): MigrationOutcome {
        val targetSource = sourceManager.get(target.source) ?: return MigrationOutcome.NotStarted(null)
        val currentSource = sourceManager.get(current.source)
        val flags = /* KMK --> */ presetFlags ?: /* KMK <-- */ sourcePreferences.migrationFlags().get()
        val completedFlags = mutableSetOf<MigrationFlag>()
        // KMK Confirmed Blocker Remediation follow-up Phase 1 2026-07-29: a requested flag whose
        // step was determined not applicable (e.g. CUSTOM_COVER requested but the manga has none) is
        // truthfully reported as skipped, distinct from either completed or failed.
        val skippedFlags = mutableSetOf<MigrationFlag>()
        var currentStep: MigrationFlag? = null
        var finalUpdateStarted = false

        try {
            updateMangaFromRemote(
                manga = target,
                fetchChapters = true,
                // SY -->
                throttleFunc = throttleFunc,
                // SY <--
            ).getOrThrow()

            // Update chapters read, bookmark and dateFetch
            if (MigrationFlag.CHAPTER in flags) {
                currentStep = MigrationFlag.CHAPTER
                val prevMangaChapters = getChaptersByMangaId.await(current.id)
                val mangaChapters = getChaptersByMangaId.await(target.id)

                val maxChapterRead = prevMangaChapters
                    .filter { it.read }
                    .maxOfOrNull { it.chapterNumber }

                // SY -->
                val historyUpdates = mutableListOf<HistoryUpdate>()
                val prevHistoryList = getHistory.await(current.id)
                    // SY <--
                    // KMK -->
                    .associateBy { it.chapterId }
                // KMK <--

                val updatedMangaChapters = mangaChapters.map { mangaChapter ->
                    var updatedChapter = mangaChapter
                    if (updatedChapter.isRecognizedNumber) {
                        val prevChapter = prevMangaChapters
                            .find { it.isRecognizedNumber && it.chapterNumber == updatedChapter.chapterNumber }

                        if (prevChapter != null) {
                            updatedChapter = updatedChapter.copy(
                                // SY -->
                                // If chapters match then mark new manga's chapters read/unread as old one
                                read = prevChapter.read,
                                // SY <--
                                dateFetch = prevChapter.dateFetch,
                                bookmark = prevChapter.bookmark,
                                lastPageRead = prevChapter.lastPageRead,
                            )
                            // SY -->
                            // KMK -->
                            prevHistoryList[prevChapter.id]?.let { prevHistory ->
                                // KMK <--
                                historyUpdates += HistoryUpdate(
                                    mangaChapter.id,
                                    prevHistory.readAt ?: return@let,
                                    prevHistory.readDuration,
                                )
                            }
                            // SY <--
                        }
                        // KMK -->
                        // If chapters which only present on new manga then mark read up to latest read chapter number
                        else /* KMK <-- */ if (maxChapterRead != null && updatedChapter.chapterNumber <= maxChapterRead) {
                            updatedChapter = updatedChapter.copy(read = true)
                        }
                    }

                    updatedChapter
                }

                val chapterUpdates = updatedMangaChapters.map { it.toChapterUpdate() }
                updateChapter.awaitAll(chapterUpdates)
                // SY -->
                upsertHistory.awaitAll(historyUpdates)
                // SY <--
                completedFlags += MigrationFlag.CHAPTER
            }

            // Update categories
            if (MigrationFlag.CATEGORY in flags) {
                currentStep = MigrationFlag.CATEGORY
                val categoryIds = getCategories.await(current.id).map { it.id }
                setMangaCategories.await(target.id, categoryIds)
                completedFlags += MigrationFlag.CATEGORY
            }

            // Update track
            // SY -->
            if (MigrationFlag.TRACK in flags) {
                currentStep = MigrationFlag.TRACK
                // SY <--
                getTracks.await(current.id).mapNotNull { track ->
                    val updatedTrack = track.copy(mangaId = target.id)

                    val service = enhancedServices
                        .firstOrNull { it.isTrackFrom(updatedTrack, current, currentSource) }

                    if (service != null) {
                        service.migrateTrack(updatedTrack, target, targetSource)
                    } else {
                        updatedTrack
                    }
                }
                    .takeIf { it.isNotEmpty() }
                    ?.let { insertTrack.awaitAll(it) }
                completedFlags += MigrationFlag.TRACK
            }

            // Delete downloaded
            if (MigrationFlag.REMOVE_DOWNLOAD in flags) {
                if (currentSource != null) {
                    currentStep = MigrationFlag.REMOVE_DOWNLOAD
                    downloadManager.deleteManga(current, currentSource)
                    completedFlags += MigrationFlag.REMOVE_DOWNLOAD
                } else {
                    // KMK Confirmed Blocker Remediation follow-up Phase 1: requested but the
                    // current source could not be resolved -- there is nothing to delete from,
                    // truthfully a skip rather than a silent no-op folded into "completed".
                    skippedFlags += MigrationFlag.REMOVE_DOWNLOAD
                }
            }

            // Update custom cover (recheck if custom cover exists)
            if (MigrationFlag.CUSTOM_COVER in flags) {
                if (current.hasCustomCover()) {
                    currentStep = MigrationFlag.CUSTOM_COVER
                    coverCache.setCustomCoverToCache(target, coverCache.getCustomCoverFile(current.id).inputStream())
                    completedFlags += MigrationFlag.CUSTOM_COVER
                } else {
                    // KMK Confirmed Blocker Remediation follow-up Phase 1: requested but the
                    // source manga has no custom cover to copy -- truthfully a skip.
                    skippedFlags += MigrationFlag.CUSTOM_COVER
                }
            }

            val currentMangaUpdate = MangaUpdate(
                id = current.id,
                favorite = false,
                dateAdded = 0,
            )
                .takeIf { replace }
            // KMK Confirmed Blocker Remediation follow-up Phase 1: NOTES is only meaningfully
            // applied when there is something to copy -- mirrors MigrateMangaDialog's own
            // `MigrationFlag.NOTES -> current.notes.isNotBlank()` applicability check, tracked here
            // (not changing the write itself) so a requested-but-blank NOTES flag is truthfully
            // reported as skipped rather than silently absent from completedFlags.
            val notesApplicable = current.notes.isNotBlank()
            if (MigrationFlag.NOTES in flags && !notesApplicable) {
                skippedFlags += MigrationFlag.NOTES
            }
            val targetMangaUpdate = MangaUpdate(
                id = target.id,
                favorite = true,
                chapterFlags = current.chapterFlags
                    // KMK -->
                    .takeIf { MigrationFlag.EXTRA in flags },
                // KMK <--
                viewerFlags = current.viewerFlags
                    // KMK -->
                    .takeIf { MigrationFlag.EXTRA in flags },
                // KMK <--
                dateAdded = if (replace) current.dateAdded else Instant.now().toEpochMilli(),
                notes = if (MigrationFlag.NOTES in flags) current.notes else null,
            )

            currentStep = null
            // KMK Confirmed Blocker Remediation follow-up Phase 1: this is the step that actually
            // commits favorite/category-visible state (and NOTES/EXTRA, which have no earlier
            // flag-gated block of their own) -- a failure here must be distinguishable from a
            // failure in one of the earlier per-flag steps, see MigrationOutcome.PartialFailure.
            finalUpdateStarted = true
            updateManga.awaitAll(listOfNotNull(currentMangaUpdate, targetMangaUpdate))
            if (MigrationFlag.NOTES in flags && notesApplicable) completedFlags += MigrationFlag.NOTES
            if (MigrationFlag.EXTRA in flags) completedFlags += MigrationFlag.EXTRA
            return MigrationOutcome.Success(
                requestedFlags = flags,
                completedFlags = completedFlags,
                skippedFlags = skippedFlags,
            )
        } catch (e: Throwable) {
            // KMK v0.8.10-fix9: also rethrow fatal VM/system errors, not just cancellation -- this
            // catch previously swallowed OutOfMemoryError/StackOverflowError/etc. along with ordinary
            // per-migration failures.
            rethrowIfFatal(e)
            // KMK Confirmed Blocker Remediation Phase 4 (extended by the follow-up pass): a
            // non-fatal exception here previously vanished with no signal to the caller. Now
            // reported truthfully -- completedFlags/skippedFlags list exactly which flag-gated
            // steps already ran or were determined not applicable (and therefore, for completed
            // steps, already produced real side effects this class does not undo) before the
            // failure. See MigrationOutcomeTest for the pure decision this delegates to.
            return MigrationOutcome.fromFailure(
                requestedFlags = flags,
                completedFlags = completedFlags,
                skippedFlags = skippedFlags,
                currentStep = currentStep,
                finalUpdateStarted = finalUpdateStarted,
                cause = e,
            )
        }
    }
}
