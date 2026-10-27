package eu.kanade.tachiyomi.ui.manga.cover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.presentation.browse.GlobalSearchScreen
import eu.kanade.presentation.util.Screen
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.screens.LoadingScreen

class CoverSearchScreen(private val mangaId: Long, private val query: String) : Screen() {

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<CoverSearchViewModel>(
            factory = CoverSearchViewModel.Factory,
            extras = CreationExtras { set(CoverSearchViewModel.INITIAL_QUERY_KEY, query) },
        )
        val state by viewModel.state.collectAsState()
        var selected by remember { mutableStateOf<Manga?>(null) }

        GlobalSearchScreen(
            state = state,
            navigateUp = navigator::pop,
            onChangeSearchQuery = viewModel::updateSearchQuery,
            onSearch = { viewModel.search() },
            onTogglePinnedOnly = viewModel::togglePinnedOnly,
            onToggleResults = viewModel::toggleFilterResults,
            getManga = { viewModel.getManga(it) },
            onClickSource = { navigator.push(CoverSourceSearchScreen(mangaId, it.id, state.searchQuery)) },
            onClickItem = { selected = it },
            onLongClickItem = { selected = it },
        )

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
