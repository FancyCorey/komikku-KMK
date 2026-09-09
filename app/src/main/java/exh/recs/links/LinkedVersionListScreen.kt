package exh.recs.links

// KMK --> v0.8.0
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import exh.recs.loved.RatedMangaKey
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale

/**
 * Focused version-list view for one confirmed cross-source link group. Loads directly from
 * persisted group data by [groupId] — does not depend on the rated screen's in-memory state.
 */
class LinkedVersionListScreen(private val groupId: String) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel(tag = "linked_versions_$groupId") {
            LinkedVersionListScreenModel(groupId = groupId)
        }
        val state by screenModel.state.collectAsState()
        var showUngroupConfirm by rememberSaveable { mutableStateOf(false) }
        // KMK --> v0.8.1-fix1: confirm before removing a version from the group. RatedMangaKey isn't
        // a Saveable type, so store its primitive fields instead (per plan §Part A).
        var pendingRemoveSource by rememberSaveable { mutableStateOf<Long?>(null) }
        var pendingRemoveUrl by rememberSaveable { mutableStateOf<String?>(null) }
        val pendingRemoveTitle = (state as? LinkedVersionListScreenModel.State.Success)
            ?.rows
            ?.firstOrNull { it.key.source == pendingRemoveSource && it.key.url == pendingRemoveUrl }
            ?.title
        // KMK <--

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.linked_version_list_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (state is LinkedVersionListScreenModel.State.Success) {
                            AppBarActions(
                                persistentListOf(
                                    AppBar.Action(
                                        title = stringResource(KMR.strings.linked_version_list_ungroup),
                                        icon = Icons.Outlined.LinkOff,
                                        onClick = { showUngroupConfirm = true },
                                    ),
                                ),
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
            when (val s = state) {
                is LinkedVersionListScreenModel.State.Loading -> Box(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is LinkedVersionListScreenModel.State.Empty -> Box(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(KMR.strings.linked_version_list_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(MaterialTheme.padding.medium),
                    )
                }

                is LinkedVersionListScreenModel.State.Error -> Box(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(KMR.strings.linked_version_list_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(MaterialTheme.padding.medium),
                    )
                }

                is LinkedVersionListScreenModel.State.Success -> {
                    LazyColumn(contentPadding = contentPadding) {
                        // KMK --> v0.8.1-fix1: make it explicit that the star sets the primary
                        // version, since "Set Primary Version" is not a separate rated-item-menu
                        // action — it is only reachable here (see plan §Part D / §Finding 4).
                        item {
                            Text(
                                text = stringResource(KMR.strings.linked_version_list_primary_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        // KMK <--
                        items(s.rows, key = { "${it.key.source}|${it.key.url}" }) { row ->
                            LinkedVersionRowCard(
                                row = row,
                                onOpen = { row.mangaId?.let { navigator.push(MangaScreen(it, true)) } },
                                onSetPrimary = { screenModel.setPrimary(row.key) },
                                onRemove = {
                                    pendingRemoveSource = row.key.source
                                    pendingRemoveUrl = row.key.url
                                },
                            )
                        }
                    }
                }
            }
        }

        if (showUngroupConfirm) {
            AlertDialog(
                onDismissRequest = { showUngroupConfirm = false },
                title = { Text(stringResource(KMR.strings.rated_manga_action_ungroup)) },
                text = { Text(stringResource(KMR.strings.rated_manga_confirm_ungroup)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showUngroupConfirm = false
                            screenModel.ungroup()
                            navigator.pop()
                        },
                    ) { Text(stringResource(KMR.strings.rated_manga_confirm_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showUngroupConfirm = false }) {
                        Text(stringResource(KMR.strings.rated_manga_confirm_cancel))
                    }
                },
            )
        }

        // KMK --> v0.8.1-fix1: confirmation before removing a linked version. Removing a version
        // only deletes its manga_cross_source_link row(s) — it never clears rating, favorite,
        // history, or manga data (LinkedVersionListScreenModel.removeFromGroup only calls
        // DeleteCrossSourceMangaLink).
        val pendingSource = pendingRemoveSource
        val pendingUrl = pendingRemoveUrl
        if (pendingSource != null && pendingUrl != null) {
            AlertDialog(
                onDismissRequest = {
                    pendingRemoveSource = null
                    pendingRemoveUrl = null
                },
                title = { Text(stringResource(KMR.strings.rated_manga_action_remove_from_group)) },
                text = {
                    Text(
                        if (pendingRemoveTitle != null) {
                            stringResource(KMR.strings.linked_version_list_confirm_remove_titled, pendingRemoveTitle)
                        } else {
                            stringResource(KMR.strings.linked_version_list_confirm_remove)
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            screenModel.removeFromGroup(RatedMangaKey(pendingSource, pendingUrl))
                            pendingRemoveSource = null
                            pendingRemoveUrl = null
                        },
                    ) { Text(stringResource(KMR.strings.rated_manga_confirm_confirm)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingRemoveSource = null
                            pendingRemoveUrl = null
                        },
                    ) { Text(stringResource(KMR.strings.rated_manga_confirm_cancel)) }
                },
            )
        }
        // KMK <--
    }
}

@Composable
private fun LinkedVersionRowCard(
    row: LinkedVersionRow,
    onOpen: () -> Unit,
    onSetPrimary: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (row.isPrimary) FontWeight.Bold else FontWeight.Normal,
                    )
                    if (row.isPrimary) {
                        Text(
                            text = " · ${stringResource(KMR.strings.linked_version_list_primary_marker)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = buildString {
                        append(row.sourceName ?: stringResource(KMR.strings.source_evaluation_unknown_source))
                        if (row.lang.isNotBlank()) append(" (${row.lang.uppercase(Locale.ROOT)})")
                        append(" · ")
                        append(
                            if (row.isInstalled) {
                                stringResource(KMR.strings.linked_version_list_installed)
                            } else {
                                stringResource(KMR.strings.linked_version_list_missing)
                            },
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = buildString {
                        append(ratingLabel(row.rating))
                        if (row.isFavorite) append(" · ★")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row {
                if (row.mangaId != null) {
                    TextButton(onClick = onOpen) {
                        Text(stringResource(KMR.strings.linked_version_list_open))
                    }
                }
                IconButton(onClick = onSetPrimary, enabled = !row.isPrimary) {
                    Icon(
                        imageVector = if (row.isPrimary) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = stringResource(KMR.strings.linked_version_list_set_primary),
                        tint = if (row.isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(KMR.strings.linked_version_list_remove),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ratingLabel(rating: MangaRating?): String = when (rating) {
    MangaRating.LOVE -> stringResource(KMR.strings.rated_manga_rating_love)
    MangaRating.LIKE -> stringResource(KMR.strings.rated_manga_rating_like)
    MangaRating.DISLIKE -> stringResource(KMR.strings.rated_manga_rating_dislike)
    MangaRating.NOT_INTERESTED -> stringResource(KMR.strings.rec_mark_seen)
    null -> stringResource(KMR.strings.rated_manga_rating_none)
}
// KMK <--
