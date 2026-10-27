package eu.kanade.tachiyomi.ui.manga.cover

import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchViewModel

class CoverSearchViewModel(initialQuery: String) : SearchViewModel(State(searchQuery = initialQuery)) {

    init {
        search()
    }

    override fun getEnabledSources(): List<Source> = super.getEnabledSources()
        .filterIsInstance<HttpSource>()
        .filter { !state.value.pinnedOnly || "${it.id}" in pinnedSources }

    companion object {
        val INITIAL_QUERY_KEY = CreationExtras.Key<String>()

        val Factory = viewModelFactory {
            initializer { CoverSearchViewModel(get(INITIAL_QUERY_KEY)!!) }
        }
    }
}
