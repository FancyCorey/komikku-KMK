package exh.recs.loved

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import exh.recs.SeenMangaKey
import exh.recs.SeenRecommendationMangaStore
import exh.util.EvaluationModeJournalRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Local, reversible destination for manga marked Not Interested from recommendation flows. */
class NotInterestedMangaScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { NotInterestedMangaScreenModel() }
        val state by screenModel.state.collectAsState()
        var pendingRemoval by remember { mutableStateOf<NotInterestedEntry?>(null) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.not_interested_manga_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when (val current = state) {
                NotInterestedState.Loading -> androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                NotInterestedState.Empty -> androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(KMR.strings.not_interested_manga_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is NotInterestedState.Error -> androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(KMR.strings.not_interested_manga_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                is NotInterestedState.Success -> LazyColumn(contentPadding = contentPadding) {
                    items(current.entries, key = { it.key.serialize() }) { entry ->
                        ListItem(
                            modifier = Modifier.clickable { navigator.push(MangaScreen(entry.manga.id, true)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Outlined.VisibilityOff,
                                    contentDescription = stringResource(KMR.strings.rec_mark_seen),
                                )
                            },
                            headlineContent = { Text(entry.manga.title) },
                            supportingContent = { Text(stringResource(KMR.strings.rec_mark_seen)) },
                            trailingContent = {
                                IconButton(onClick = { pendingRemoval = entry }) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = stringResource(MR.strings.action_remove),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        pendingRemoval?.let { entry ->
            AlertDialog(
                onDismissRequest = { pendingRemoval = null },
                title = { Text(stringResource(KMR.strings.not_interested_manga_remove_title)) },
                text = { Text(stringResource(KMR.strings.not_interested_manga_remove_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingRemoval = null
                        screenModel.remove(entry)
                    }) { Text(stringResource(MR.strings.action_remove)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRemoval = null }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
    }
}

private data class NotInterestedEntry(
    val key: SeenMangaKey,
    val manga: Manga,
)

private sealed interface NotInterestedState {
    data object Loading : NotInterestedState
    data object Empty : NotInterestedState
    data class Success(val entries: List<NotInterestedEntry>) : NotInterestedState
    data object Error : NotInterestedState
}

private class NotInterestedMangaScreenModel(
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getMangaTaste: tachiyomi.domain.taste.interactor.GetMangaTaste = Injekt.get(),
) : StateScreenModel<NotInterestedState>(NotInterestedState.Loading) {

    init {
        screenModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val entries = SeenRecommendationMangaStore
                .parse(sourcePreferences.seenRecommendationMangaKeys().get())
                .mapNotNull { key -> getManga.await(key.url, key.sourceId)?.let { NotInterestedEntry(key, it) } }
            mutableState.value = entries.takeUnless { it.isEmpty() }?.let(NotInterestedState::Success)
                ?: NotInterestedState.Empty
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            mutableState.value = NotInterestedState.Error
        }
    }

    fun remove(entry: NotInterestedEntry) {
        screenModelScope.launch {
            try {
                val current = SeenRecommendationMangaStore.parse(sourcePreferences.seenRecommendationMangaKeys().get())
                if (entry.key !in current) return@launch
                val journalEntries = EvaluationModeJournalRecorder.buildNotInterestedRemoval(
                    sourcePreferences,
                    getMangaTaste,
                    listOf(entry.manga),
                )
                sourcePreferences.seenRecommendationMangaKeys().set(
                    SeenRecommendationMangaStore.serialize(SeenRecommendationMangaStore.remove(current, entry.key)),
                )
                EvaluationModeJournalRecorder.commit(journalEntries)
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.value = NotInterestedState.Error
            }
        }
    }
}
