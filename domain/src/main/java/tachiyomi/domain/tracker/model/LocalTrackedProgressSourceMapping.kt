package tachiyomi.domain.tracker.model

import tachiyomi.domain.taste.model.AlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeMapping
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingState
import tachiyomi.domain.taste.model.AlternateSourceBridgeReviewState

data class LocalTrackedProgressSourceMapping(
    val targetSource: Long,
    val targetUrl: String,
    val targetChapterUrl: String,
)

/** Resolves an already-confirmed chapter URL pair without inferring by title or source URL. */
object LocalTrackedProgressSourceMappingPolicy {

    fun resolve(
        originSource: Long,
        originUrl: String,
        originChapterUrl: String,
        targetSource: Long,
        targetUrl: String,
        bridges: List<AlternateSourceBridge>,
        mappings: List<AlternateSourceBridgeMapping>,
    ): LocalTrackedProgressSourceMapping? {
        if (originSource <= 0L || targetSource <= 0L || originUrl.isBlank() || targetUrl.isBlank() ||
            originChapterUrl.isBlank() || (originSource == targetSource && originUrl == targetUrl)
        ) {
            return null
        }

        val candidates = bridges.asSequence()
            .filter { bridge ->
                bridge.deletedAt == null && bridge.reviewState == AlternateSourceBridgeReviewState.CURRENT &&
                    (
                        (
                            bridge.key.primary.source == originSource && bridge.key.primary.url == originUrl &&
                                bridge.key.alternate.source == targetSource && bridge.key.alternate.url == targetUrl
                            ) ||
                            (
                                bridge.key.alternate.source == originSource && bridge.key.alternate.url == originUrl &&
                                    bridge.key.primary.source == targetSource && bridge.key.primary.url == targetUrl
                                )
                        )
            }
            .flatMap { bridge ->
                mappings.asSequence()
                    .filter { mapping ->
                        mapping.key.bridge == bridge.key &&
                            mapping.deletedAt == null &&
                            mapping.state == AlternateSourceBridgeMappingState.CONFIRMED
                    }
                    .mapNotNull { mapping ->
                        val originIsPrimary = bridge.key.primary.source == originSource &&
                            bridge.key.primary.url == originUrl
                        val mappedOriginChapterUrl = if (originIsPrimary) {
                            mapping.primaryChapterUrl
                        } else {
                            mapping.alternateChapterUrl
                        }
                        if (mappedOriginChapterUrl != originChapterUrl) return@mapNotNull null
                        val targetChapterUrl = if (originIsPrimary) {
                            mapping.alternateChapterUrl
                        } else {
                            mapping.primaryChapterUrl
                        } ?: return@mapNotNull null
                        LocalTrackedProgressSourceMapping(targetSource, targetUrl, targetChapterUrl)
                    }
            }
            .distinct()
            .toList()

        return candidates.singleOrNull()
    }
}
