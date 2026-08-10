package eu.kanade.tachiyomi.ui.reader.timer

// KMK v0.8.4 -->
/**
 * Pure encode/decode between [ReaderTimerSession] and a flat set of Bundle-safe primitives, for
 * storage as individual `SavedStateHandle` entries (matching this repo's existing convention —
 * see `ReaderViewModel`'s `chapter_id`/`page_index` fields). No Parcelable/Serializable object is
 * ever persisted.
 *
 * [decode] never throws: any missing, malformed, or out-of-range value falls back to a fresh
 * [ReaderTimerSession] (IDLE) rather than crashing reader restoration.
 */
object ReaderTimerStateCodec {

    const val KEY_PHASE = "reader_timer_phase"
    const val KEY_TOTAL_MS = "reader_timer_total_ms"
    const val KEY_ELAPSED_MS = "reader_timer_elapsed_ms"
    const val KEY_WARNING_MINUTES = "reader_timer_warning_minutes"
    const val KEY_FINISH_CHAPTER = "reader_timer_finish_chapter"
    const val KEY_ALLOW_EXTRA = "reader_timer_allow_extra"
    const val KEY_FIRED_WARNINGS = "reader_timer_fired_warnings"
    const val KEY_EXTRA_USED = "reader_timer_extra_used"
    const val KEY_PAUSED_FROM = "reader_timer_paused_from"
    const val KEY_PAUSE_REASON = "reader_timer_pause_reason"

    data class Encoded(
        val phase: String,
        val totalMs: Long,
        val elapsedMs: Long,
        val warningMinutesCsv: String,
        val finishChapter: Boolean,
        val allowExtra: Boolean,
        val firedWarningsCsv: String,
        val extraUsed: Boolean,
        val pausedFrom: String?,
        val pauseReason: String?,
    )

    fun encode(session: ReaderTimerSession): Encoded = Encoded(
        phase = session.phase.name,
        totalMs = session.totalDurationMs,
        elapsedMs = session.elapsedActiveMs,
        warningMinutesCsv = session.warningPolicy.minutesBeforeExpiry.sorted().joinToString(","),
        finishChapter = session.gracePolicy.finishCurrentChapter,
        allowExtra = session.gracePolicy.allowExtraChapter,
        firedWarningsCsv = session.firedWarningMinutes.sorted().joinToString(","),
        extraUsed = session.extraChapterUsed,
        pausedFrom = session.pausedFromPhase?.name,
        pauseReason = session.pauseReason?.name,
    )

    /**
     * Decodes persisted primitives back into a session. [lastResumeMonotonicMs] is intentionally
     * never persisted/restored — a restored session is always frozen (see
     * [ReaderTimerEvent.ProcessRestored]) since no ticks occurred while the process was dead.
     */
    fun decode(
        phase: String?,
        totalMs: Long?,
        elapsedMs: Long?,
        warningMinutesCsv: String?,
        finishChapter: Boolean?,
        allowExtra: Boolean?,
        firedWarningsCsv: String?,
        extraUsed: Boolean?,
        pausedFrom: String?,
        pauseReason: String?,
    ): ReaderTimerSession {
        val parsedPhase = phase?.let { runCatching { ReaderTimerPhase.valueOf(it) }.getOrNull() } ?: return ReaderTimerSession()
        val total = totalMs ?: return ReaderTimerSession()
        val elapsed = elapsedMs ?: return ReaderTimerSession()
        if (total < 0 || elapsed < 0) return ReaderTimerSession()

        val warningMinutes = parseCsvInts(warningMinutesCsv)
        val firedWarnings = parseCsvInts(firedWarningsCsv)
        val pausedFromPhase = pausedFrom?.let { runCatching { ReaderTimerPhase.valueOf(it) }.getOrNull() }
        val reason = pauseReason?.let { runCatching { ReaderTimerPauseReason.valueOf(it) }.getOrNull() }

        // A PAUSED session must have recorded which phase to resume into; otherwise the record is
        // inconsistent (malformed) and we fall back to IDLE rather than resume into a broken state.
        if (parsedPhase == ReaderTimerPhase.PAUSED && (pausedFromPhase == null || reason == null)) {
            return ReaderTimerSession()
        }

        return ReaderTimerSession(
            phase = parsedPhase,
            totalDurationMs = total,
            elapsedActiveMs = elapsed,
            lastResumeMonotonicMs = null,
            warningPolicy = ReaderTimerWarningPolicy.validate(warningMinutes),
            gracePolicy = ReaderTimerGracePolicy(
                finishCurrentChapter = finishChapter ?: true,
                allowExtraChapter = allowExtra ?: false,
            ),
            firedWarningMinutes = firedWarnings.intersect(ReaderTimerWarningPolicy.SUPPORTED_MINUTES),
            extraChapterUsed = extraUsed ?: false,
            pausedFromPhase = pausedFromPhase,
            pauseReason = reason,
        )
    }

    private fun parseCsvInts(csv: String?): Set<Int> =
        csv?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: emptySet()
}
// KMK <--
