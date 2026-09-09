package exh.recs.matching

import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.model.CrossSourceIdentityDecision
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.CrossSourceRecordKey
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CrossSourceIdentityAuthorizationResolver(
    private val getDecisions: GetCrossSourceIdentityDecisions = Injekt.get(),
) {
    companion object {
        fun confirmedLinkGroupByKey(
            links: List<CrossSourceMangaLink>,
            decisions: List<CrossSourceIdentityDecision>,
        ): Map<String, String> {
            val linkByRecord = links.associateBy { CrossSourceRecordKey(it.source, it.url) }
            return buildMap {
                decisions.asSequence()
                    .filter(CrossSourceIdentityDecisionPolicy::isAuthoritativeConfirmation)
                    .forEach { decision ->
                        val first = linkByRecord[decision.pair.left] ?: return@forEach
                        val second = linkByRecord[decision.pair.right] ?: return@forEach
                        if (first.groupId != second.groupId) return@forEach
                        put("${first.source}|${first.url}", first.groupId)
                        put("${second.source}|${second.url}", second.groupId)
                    }
            }
        }
    }

    suspend fun isConfirmed(
        firstSource: Long,
        firstUrl: String,
        secondSource: Long,
        secondUrl: String,
    ): Boolean {
        val pair = CrossSourceIdentityDecisionPolicy.canonicalPair(
            CrossSourceRecordKey(firstSource, firstUrl),
            CrossSourceRecordKey(secondSource, secondUrl),
        )
        return CrossSourceIdentityDecisionPolicy.isAuthoritativeConfirmation(getDecisions.await(pair))
    }

    suspend fun confirmedGroupMembers(
        originSource: Long,
        originUrl: String,
        members: List<CrossSourceMangaLink>,
    ): List<CrossSourceMangaLink> = buildList {
        members.forEach { member ->
            if (
                (member.source == originSource && member.url == originUrl) ||
                isConfirmed(originSource, originUrl, member.source, member.url)
            ) {
                add(member)
            }
        }
    }
}
