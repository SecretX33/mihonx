package eu.kanade.tachiyomi.ui.reader.model

import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.loader.PageLoader
import kotlinx.coroutines.flow.MutableStateFlow
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.ScanlatorFillerPages

data class ReaderChapter(val chapter: Chapter, val fillerPages: ScanlatorFillerPages = ScanlatorFillerPages()) {

    val stateFlow = MutableStateFlow<State>(State.Wait)
    var state: State
        get() = stateFlow.value
        set(value) {
            stateFlow.value = value
        }

    val pages: List<ReaderPage>?
        get() = (state as? State.Loaded)?.pages

    val navigablePages: List<ReaderPage>?
        get() = pages?.let { pages ->
            val indices = fillerPages.visiblePageIndices(pages.size)
            pages.subList(indices.first, indices.last + 1)
        }

    fun pageAtOrNearest(index: Int): ReaderPage? {
        val pages = navigablePages ?: return null
        return pages.getOrNull((index - pages.first().index).coerceIn(0, pages.lastIndex))
    }

    fun isLastNavigablePage(page: Page): Boolean = navigablePages?.lastOrNull() === page

    var pageLoader: PageLoader? = null

    var requestedPage: Int = 0

    private var references = 0

    constructor(
        chapter: tachiyomi.domain.chapter.model.Chapter,
        fillerPages: ScanlatorFillerPages = ScanlatorFillerPages(),
    ) : this(chapter.toDbChapter(), fillerPages)

    fun ref() {
        references++
    }

    fun unref() {
        references--
        if (references == 0) {
            if (pageLoader != null) {
                logcat { "Recycling chapter ${chapter.name}" }
            }
            pageLoader?.recycle()
            pageLoader = null
            state = State.Wait
        }
    }

    sealed interface State {
        data object Wait : State
        data object Loading : State
        data class Error(val error: Throwable) : State
        data class Loaded(val pages: List<ReaderPage>) : State
    }
}
