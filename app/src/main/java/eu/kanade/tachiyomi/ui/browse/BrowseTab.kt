package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.core.preference.asState
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.extension.extensionsTab
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import eu.kanade.tachiyomi.ui.browse.feed.feedTab
import eu.kanade.tachiyomi.ui.browse.migration.sources.migrateSourceTab
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.sourcesTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import exh.recs.personalRecommendationsTab
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object BrowseTab : Tab {
    private fun readResolve(): Any = BrowseTab

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_browse_enter)
            return TabOptions(
                index = 3u,
                title = stringResource(MR.strings.browse),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(GlobalSearchScreen())
    }

    private val switchToExtensionTabChannel = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    fun showExtension() {
        switchToExtensionTabChannel.trySend(Unit)
    }

    // KMK v0.8.18-fix1: mirrors showExtension() above -- lets a caller outside BrowseTab's own
    // composition (Source Evaluation's app-bar action, the collection quick-access panel) jump to the
    // For You sub-tab once BrowseTab is the selected tab and its Content() is composed. A no-op if the
    // For You tab is hidden (hideForYouTab) -- callers must check that preference themselves before
    // offering this action, since there is nothing to switch to in that case.
    private val switchToForYouTabChannel = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    fun showForYou() {
        switchToForYouTabChannel.trySend(Unit)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        // SY -->
        val hideFeedTab by remember { Injekt.get<UiPreferences>().hideFeedTab().asState(scope) }
        val feedTabInFront by remember { Injekt.get<UiPreferences>().feedTabInFront().asState(scope) }
        // SY <--
        // KMK -->
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val hideMigrateTab by remember { uiPreferences.hideMigrateTab().asState(scope) }
        val hideForYouTab by remember { uiPreferences.hideForYouTab().asState(scope) }
        // KMK <--

        // Hoisted for extensions tab's search bar
        val extensionsScreenModel = rememberScreenModel { ExtensionsScreenModel() }
        val extensionsState by extensionsScreenModel.state.collectAsState()

        // KMK -->
        val feedScreenModel = rememberScreenModel { FeedScreenModel() }
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        // KMK <--

        // SY -->
        // KMK --> build tab list dynamically so Migrate and For You can be hidden independently
        val tabs = buildList {
            if (feedTabInFront && !hideFeedTab) {
                add(feedTab(feedScreenModel, bulkFavoriteScreenModel))
            }
            add(sourcesTab())
            if (!hideFeedTab && !feedTabInFront) {
                add(feedTab(feedScreenModel, bulkFavoriteScreenModel))
            }
            add(extensionsTab(extensionsScreenModel))
            if (!hideMigrateTab) {
                add(migrateSourceTab())
            }
            if (!hideForYouTab) {
                add(personalRecommendationsTab())
            }
        }.toImmutableList()
        // KMK <--
        // SY <--

        val state = rememberPagerState { tabs.size }

        TabbedScreen(
            titleRes = MR.strings.browse,
            tabs = tabs,
            state = state,
            searchQuery = extensionsState.searchQuery,
            onChangeSearchQuery = extensionsScreenModel::search,
            // KMK -->
            feedScreenModel = feedScreenModel,
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            // KMK <--
        )
        LaunchedEffect(Unit) {
            switchToExtensionTabChannel.receiveAsFlow()
                // KMK --> extensions index: always after Sources (and Feed if visible and not in front)
                // hideFeedTab → [Sources, Extensions, ...] → index 1
                // else        → [Feed?, Sources, Feed?, Extensions, ...] → index 2
                .collectLatest { state.scrollToPage(if (hideFeedTab) 1 else 2) }
            // KMK <--
        }

        // KMK v0.8.18-fix1: For You index mirrors the tabs buildList order above -- Sources,
        // (Feed if visible), Extensions, (Migrate if visible), then For You last (unless hidden, in
        // which case the tab doesn't exist and showForYou() below is a no-op since the page index
        // computed here would be out of range and scrollToPage would simply have nothing extra to do).
        LaunchedEffect(Unit) {
            switchToForYouTabChannel.receiveAsFlow()
                .collectLatest {
                    if (!hideForYouTab) {
                        state.scrollToPage(tabs.size - 1)
                    }
                }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true

            // AM (DISCORD) -->
            with(DiscordRPCService) {
                discordScope.launchIO { setScreen(context, DiscordScreen.BROWSE) }
            }
            // <-- AM (DISCORD)
        }
    }
}
