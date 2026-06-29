package exh.recs.settings

// KMK --> v0.7.27: Best Version history management screen model
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.taste.interactor.DeleteMangaSourceQualitySignal
import tachiyomi.domain.taste.interactor.GetMangaSourceQualitySignals
import tachiyomi.domain.taste.model.MangaSourceQualitySignal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class QualitySignalHistoryScreenModel(
    private val getSignals: GetMangaSourceQualitySignals = Injekt.get(),
    private val deleteSignal: DeleteMangaSourceQualitySignal = Injekt.get(),
) : StateScreenModel<QualitySignalHistoryScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            val all = runCatching { getSignals.getAll() }.getOrElse { emptyList() }
            val groups = groupByOriginTitle(all)
            mutableState.update { it.copy(groups = groups, isLoading = false) }
        }
    }

    fun deleteRecord(id: Long) {
        screenModelScope.launchNonCancellable {
            deleteSignal.await(id)
            val updated = state.value.groups.map { group ->
                group.copy(records = group.records.filter { it.id != id }.toImmutableList())
            }.filter { it.records.isNotEmpty() }.toImmutableList()
            mutableState.update { it.copy(groups = updated) }
        }
    }

    fun requestDeleteAll() {
        mutableState.update { it.copy(showDeleteAllDialog = true) }
    }

    fun dismissDeleteAllDialog() {
        mutableState.update { it.copy(showDeleteAllDialog = false) }
    }

    fun confirmDeleteAll() {
        mutableState.update { it.copy(showDeleteAllDialog = false) }
        screenModelScope.launchNonCancellable {
            deleteSignal.awaitAll()
            mutableState.update { it.copy(groups = persistentListOf()) }
        }
    }

    private fun groupByOriginTitle(signals: List<MangaSourceQualitySignal>): ImmutableList<OriginGroup> {
        return signals
            .sortedByDescending { it.selectedAt }
            .groupBy { it.originTitle }
            .map { (title, recs) ->
                OriginGroup(
                    originTitle = title,
                    records = recs.toImmutableList(),
                )
            }
            .sortedByDescending { it.records.first().selectedAt }
            .toImmutableList()
    }

    @Immutable
    data class State(
        val groups: ImmutableList<OriginGroup> = persistentListOf(),
        val isLoading: Boolean = true,
        val showDeleteAllDialog: Boolean = false,
    )

    @Immutable
    data class OriginGroup(
        val originTitle: String,
        val records: ImmutableList<MangaSourceQualitySignal>,
    )
}
// KMK <--
