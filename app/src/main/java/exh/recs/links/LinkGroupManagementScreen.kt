package exh.recs.links

// KMK --> v0.7.30
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

class LinkGroupManagementScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { LinkGroupManagementScreenModel() }
        val state by screenModel.state.collectAsState()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.link_group_management_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when (val s = state) {
                is LinkGroupManagementScreenModel.State.Loading -> Box(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is LinkGroupManagementScreenModel.State.Empty -> Box(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(KMR.strings.link_group_management_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(MaterialTheme.padding.medium),
                    )
                }

                is LinkGroupManagementScreenModel.State.Error -> Box(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = s.error.message ?: stringResource(KMR.strings.link_group_management_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(MaterialTheme.padding.medium),
                    )
                }

                is LinkGroupManagementScreenModel.State.Success -> {
                    LazyColumn(contentPadding = contentPadding) {
                        items(s.groups, key = { it.groupId }) { group ->
                            LinkGroupCard(
                                group = group,
                                onDeleteGroup = { screenModel.deleteGroup(group.groupId) },
                                onDeleteLink = { source, url -> screenModel.deleteLink(source, url) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkGroupCard(
    group: LinkGroup,
    onDeleteGroup: () -> Unit,
    onDeleteLink: (Long, String) -> Unit,
) {
    var expanded by rememberSaveable(group.groupId) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.padding.small)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.primaryTitle,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(KMR.strings.link_group_management_source_count, group.links.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                    )
                }
                IconButton(onClick = onDeleteGroup) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(KMR.strings.link_group_management_delete_group),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (expanded) {
                group.links.forEach { link ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = MaterialTheme.padding.medium),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = link.title,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { onDeleteLink(link.source, link.url) },
                        ) {
                            Text(
                                text = stringResource(KMR.strings.link_group_management_remove_link),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
// KMK <--
