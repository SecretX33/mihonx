package eu.kanade.tachiyomi.ui.manga.cover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.GlobalSearchScreen
import eu.kanade.presentation.util.Screen
import tachiyomi.domain.manga.model.Manga

class CoverSearchScreen(private val mangaId: Long, private val query: String) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = assistedMetroViewModel<CoverSearchViewModel, CoverSearchViewModel.Factory> {
            create(initialQuery = query)
        }
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
