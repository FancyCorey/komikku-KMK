package exh.recs.bestversion

// KMK v0.8.16-fix1 -->
/**
 * Pure decision for whether a candidate's sampled preview pages should be shown as
 * [CandidatePreviewState.Loaded] or [CandidatePreviewState.PreviewError]. A candidate whose pages
 * could not resolve into a single usable [SampledPage] (e.g. every sampled index had a null image
 * URL) must not render a misleading success row with an empty thumbnail strip -- see the ADB UI audit
 * (`UI_AUDIT_NOTES.md`) that found "N/N pages loaded" could be shown next to broken placeholders.
 */
object BestVersionPreviewOutcomePolicy {
    fun hasUsablePreview(sampledPageCount: Int): Boolean = sampledPageCount > 0
}
// KMK <--
