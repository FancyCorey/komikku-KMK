package exh.recs.matching

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.model.CrossSourceIdentityPair
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID

enum class CrossSourceIdentityReviewFilter { CONFIRMED, REJECTED, LEGACY, NEEDS_REVIEW }

data class CrossSourceIdentityReviewRow(
    val token: String,
    val filter: CrossSourceIdentityReviewFilter,
    val firstTitle: String,
    val secondTitle: String,
    val firstSource: String?,
    val secondSource: String?,
)

class CrossSourceIdentityReviewScreenModel(
    private val getDecisions: GetCrossSourceIdentityDecisions = Injekt.get(),
    private val getLinks: GetCrossSourceMangaLinks = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val controller: CrossSourceIdentityDecisionController = CrossSourceIdentityDecisionController(),
) : StateScreenModel<CrossSourceIdentityReviewScreenModel.State>(State()) {

    companion object {
        const val MAX_REVIEW_ROWS = 500
    }

    private val pairByToken = mutableMapOf<String, CrossSourceIdentityPair>()

    init {
        reload()
    }

    fun selectFilter(filter: CrossSourceIdentityReviewFilter) {
        mutableState.update { it.copy(selectedFilter = filter, inspectedToken = null) }
    }

    fun inspect(token: String) {
        if (token in pairByToken) mutableState.update { it.copy(inspectedToken = token) }
    }

    fun dismissInspection() {
        mutableState.update { it.copy(inspectedToken = null) }
    }

    fun mutate(token: String, mutation: CrossSourceIdentityMutation) {
        val pair = pairByToken[token] ?: return
        screenModelScope.launch {
            mutableState.update { it.copy(isMutating = true, feedback = null) }
            val result = controller.mutate(pair, mutation)
            mutableState.update { it.copy(isMutating = false, inspectedToken = null, feedback = result) }
            reload()
        }
    }

    fun reload() {
        screenModelScope.launch {
            mutableState.update { it.copy(isLoading = true) }
            try {
                val decisions = getDecisions.awaitAll()
                val links = getLinks.awaitAll()
                val activeByPair = decisions.filter { it.deletedAt == null }.associateBy { it.pair }
                val pairs = buildList {
                    decisions.filter { it.deletedAt == null }.forEach { decision ->
                        add(decision.pair to CrossSourceIdentityReviewPolicy.filterFor(decision))
                    }
                    CrossSourceIdentityReviewPolicy.legacyPairs(
                        links,
                        activeByPair.keys,
                        MAX_REVIEW_ROWS,
                    ).forEach { pair ->
                        add(pair to CrossSourceIdentityReviewFilter.LEGACY)
                    }
                }.distinctBy { it.first }.take(MAX_REVIEW_ROWS)

                pairByToken.clear()
                val rows = pairs.map { (pair, filter) -> buildRow(pair, filter) }
                mutableState.update { it.copy(rows = rows, isLoading = false, failed = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pairByToken.clear()
                mutableState.update { it.copy(rows = emptyList(), isLoading = false, failed = true) }
            }
        }
    }

    private suspend fun buildRow(
        pair: CrossSourceIdentityPair,
        filter: CrossSourceIdentityReviewFilter,
    ): CrossSourceIdentityReviewRow {
        val token = UUID.randomUUID().toString()
        pairByToken[token] = pair
        val evaluationMode = sourcePreferences.evaluationMode().get()
        val display = if (evaluationMode) {
            CrossSourceIdentityReviewPolicy.display(true, null, null, null, null)
        } else {
            CrossSourceIdentityReviewPolicy.display(
                evaluationMode = false,
                firstTitle = safely { getManga.await(pair.left.url, pair.left.source)?.title },
                secondTitle = safely { getManga.await(pair.right.url, pair.right.source)?.title },
                firstSource = safely { sourceManager.get(pair.left.source)?.name },
                secondSource = safely { sourceManager.get(pair.right.source)?.name },
            )
        }
        return CrossSourceIdentityReviewRow(
            token = token,
            filter = filter,
            firstTitle = display.firstTitle,
            secondTitle = display.secondTitle,
            firstSource = display.firstSource,
            secondSource = display.secondSource,
        )
    }

    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    data class State(
        val rows: List<CrossSourceIdentityReviewRow> = emptyList(),
        val selectedFilter: CrossSourceIdentityReviewFilter = CrossSourceIdentityReviewFilter.CONFIRMED,
        val inspectedToken: String? = null,
        val isLoading: Boolean = true,
        val isMutating: Boolean = false,
        val failed: Boolean = false,
        val feedback: CrossSourceIdentityMutationResult? = null,
    ) {
        val filteredRows: List<CrossSourceIdentityReviewRow>
            get() = rows.filter { it.filter == selectedFilter }
        val inspectedRow: CrossSourceIdentityReviewRow?
            get() = rows.firstOrNull { it.token == inspectedToken }
    }
}
