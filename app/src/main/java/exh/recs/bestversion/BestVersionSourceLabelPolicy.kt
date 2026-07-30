package exh.recs.bestversion

// KMK Confirmed Blocker Remediation Phase 3 2026-07-29 -->
/**
 * Pure decision for [BestVersionCompareScreenModel.sourceName]'s Evaluation Mode branch, extracted
 * so the privacy invariant ("never the raw source name when Evaluation Mode is enabled") is directly
 * unit-testable without instantiating the full screen model (which requires `SourceManager`,
 * `GetManga`, `GetChaptersByMangaId`, `NetworkToLocalManga`, `MigrateMangaUseCase`, and
 * `UpsertMangaSourceQualitySignal` -- no existing test in this codebase instantiates it directly).
 * [rawName] is lazy so a real source lookup is never performed when Evaluation Mode is enabled --
 * this is the "private UI shows the real extension, public evidence shows the obfuscated label"
 * behavior the plan requires, applied without an unnecessary `SourceManager.getOrStub` call.
 */
object BestVersionSourceLabelPolicy {
    fun resolve(evaluationModeEnabled: Boolean, sourceId: Long, rawName: () -> String): String =
        if (evaluationModeEnabled) {
            exh.util.EvaluationModeFormatter.sourceLabel(sourceId)
        } else {
            rawName()
        }
}
// KMK <--
