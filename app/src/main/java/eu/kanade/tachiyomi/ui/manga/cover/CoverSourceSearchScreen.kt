package eu.kanade.tachiyomi.ui.manga.cover

import androidx.compose.material3.Icon
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.BrowseSourceContent
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilterDialog
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.core.common.Constants
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

data class CoverSourceSearchScreen(
    private val mangaId: Long,
    private val sourceId: Long,
    private val query: String?,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val uriHandler = LocalUriHandler.current
        val viewModel = assistedMetroViewModel<BrowseSourceViewModel, BrowseSourceViewModel.Factory> {
            create(sourceId = sourceId, listingQuery = query, openWithDefaultFilters = false)
        }
        val state by viewModel.state.collectAsState()
        val source = state.source
        if (source == null) {
            LoadingScreen()
            return
        }
        val snackbarHostState = remember { SnackbarHostState() }
        var selected by remember { mutableStateOf<Manga?>(null) }

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    searchQuery = state.toolbarQuery ?: "",
                    onChangeSearchQuery = viewModel::setToolbarQuery,
                    onClickCloseSearch = navigator::pop,
                    onSearch = viewModel::search,
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                if (state.filters.isNotEmpty()) {
                    SmallExtendedFloatingActionButton(
                        text = { Text(stringResource(MR.strings.action_filter)) },
                        icon = { Icon(MaterialSymbols.Rounded.FilterList, contentDescription = null) },
                        onClick = viewModel::openFilterSheet,
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            BrowseSourceContent(
                source = source,
                mangaList = viewModel.mangaPagerFlowFlow.collectAsLazyPagingItems(),
                columns = viewModel.getColumnsPreference(LocalConfiguration.current.orientation),
                displayMode = viewModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = {
                    val source = source as? HttpSource ?: return@BrowseSourceContent
                    navigator.push(WebViewScreen(source.getHomeUrl(), source.name, source.id))
                },
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onLocalSourceHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onMangaClick = { selected = it },
                onMangaLongClick = { selected = it },
            )
        }

        if (state.dialog is BrowseSourceViewModel.Dialog.Filter) {
            SourceFilterDialog(
                onDismissRequest = { viewModel.setDialog(null) },
                filters = state.filters,
                onReset = viewModel::resetFilters,
                onFilter = { viewModel.search(filters = state.filters) },
                onUpdate = viewModel::setFilters,
            )
        }
        selected?.let { target ->
            CoverLinkConfirmation(
                mangaId = mangaId,
                target = target,
                onDismissRequest = { selected = null },
                onComplete = { navigator.popUntil { it !is CoverSearchScreen && it !is CoverSourceSearchScreen } },
            )
        }
    }
}
