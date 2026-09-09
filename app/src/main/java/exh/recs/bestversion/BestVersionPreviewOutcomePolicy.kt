package exh.recs.bestversion

import exh.recs.RecommendationErrorKind
import java.net.URI

// KMK v0.8.16-fix1 -->
/**
 * Pure decision for whether a candidate's sampled preview pages should be shown as
 * [CandidatePreviewState.Loaded] or [CandidatePreviewState.PreviewError]. A candidate whose pages
 * could not resolve into a single usable [SampledPage] (e.g. every sampled index had a null image
 * URL) must not render a misleading success row with an empty thumbnail strip. A prepared-page count
 * is not proof that any thumbnail can be decoded.
 */
object BestVersionPreviewOutcomePolicy {
    fun hasUsablePreview(sampledPageCount: Int): Boolean = sampledPageCount > 0
}

/**
 * Typed image-level outcome used by Best Version before a candidate is presented as usable.
 * This is deliberately separate from [RecommendationErrorKind]: the latter describes a source
 * operation, while this type also covers an absent/incompatible URL and a later render failure.
 */
enum class BestVersionImageOutcome {
    Usable,
    SourceAbsent,
    Retryable,
    AuthenticationRequired,
    Incompatible,
    RenderFailed,
}

object BestVersionImageOutcomePolicy {
    fun selectUsableUrl(primaryUrl: String?, fallbackUrl: String?): String? =
        when {
            classifyResolvedUrl(primaryUrl) == BestVersionImageOutcome.Usable -> primaryUrl
            classifyResolvedUrl(fallbackUrl) == BestVersionImageOutcome.Usable -> fallbackUrl
            else -> null
        }

    fun classifyResolvedUrl(imageUrl: String?): BestVersionImageOutcome {
        if (imageUrl.isNullOrBlank()) return BestVersionImageOutcome.SourceAbsent
        return runCatching {
            URI(imageUrl).let { uri ->
                if (uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) {
                    BestVersionImageOutcome.Usable
                } else {
                    BestVersionImageOutcome.Incompatible
                }
            }
        }.getOrDefault(BestVersionImageOutcome.Incompatible)
    }

    fun fromRecommendationError(kind: RecommendationErrorKind): BestVersionImageOutcome = when (kind) {
        RecommendationErrorKind.Network,
        RecommendationErrorKind.Timeout,
        // KMK v0.8.21-fix2: a rate-limited request is retryable in the same sense as a transient
        // network failure -- retrying immediately may still fail, but the underlying condition is
        // not permanent the way an incompatible extension is.
        RecommendationErrorKind.RateLimit,
        // KMK v0.8.21-fix4: R2/AUG-05 correction -- a 5xx is the source's own server reporting a
        // transient failure (the reproduced case was a real HTTP 502), the same "may still fail but
        // isn't permanent" shape as Network/Timeout/RateLimit above, not an app-side or extension
        // defect.
        RecommendationErrorKind.ServerError,
        -> BestVersionImageOutcome.Retryable
        RecommendationErrorKind.ExtensionIncompatible,
        RecommendationErrorKind.FileAccess,
        -> BestVersionImageOutcome.Incompatible
        RecommendationErrorKind.Cancelled -> BestVersionImageOutcome.Retryable
        RecommendationErrorKind.Internal -> BestVersionImageOutcome.Retryable
        // KMK v0.8.21-fix2: the first real producer of AuthenticationRequired -- previously dead
        // code (see Plan B's AUG-B03 reconciliation) because nothing ever classified a failure as
        // authentication-specific. A 401/403 HttpException now reaches here via
        // RecommendationErrorClassifier.classify().
        RecommendationErrorKind.Authentication -> BestVersionImageOutcome.AuthenticationRequired
    }

    fun renderFailure(): BestVersionImageOutcome = BestVersionImageOutcome.RenderFailed

    fun toErrorReason(outcome: BestVersionImageOutcome): BestVersionErrorReason = when (outcome) {
        BestVersionImageOutcome.SourceAbsent -> BestVersionErrorReason.SourceUnavailable
        BestVersionImageOutcome.Retryable -> BestVersionErrorReason.Recommendation(RecommendationErrorKind.Network)
        // KMK v0.8.21-fix2: previously mapped to Network, which was untruthful -- an
        // authentication-required outcome now round-trips to the same Authentication kind that can
        // produce it (see fromRecommendationError above), instead of losing that distinction.
        BestVersionImageOutcome.AuthenticationRequired -> BestVersionErrorReason.Recommendation(RecommendationErrorKind.Authentication)
        BestVersionImageOutcome.Incompatible -> BestVersionErrorReason.Recommendation(RecommendationErrorKind.ExtensionIncompatible)
        BestVersionImageOutcome.RenderFailed -> BestVersionErrorReason.Recommendation(RecommendationErrorKind.Internal)
        BestVersionImageOutcome.Usable -> error("A usable image cannot be rendered as an error")
    }
}
// KMK <--
