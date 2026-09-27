package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.ScanlatorFillerPages

class ReaderFillerPagesTest {
    @Test
    fun `direct page requests clamp into visible physical pages`() {
        val chapter = ReaderChapter(Chapter.create(), ScanlatorFillerPages(beginning = 2, end = 1))
        val pages = (0..5).map(::ReaderPage)
        chapter.state = ReaderChapter.State.Loaded(pages)

        chapter.navigablePages shouldBe pages.subList(2, 5)
        chapter.pageAtOrNearest(0) shouldBe pages[2]
        chapter.pageAtOrNearest(3) shouldBe pages[3]
        chapter.pageAtOrNearest(5) shouldBe pages[4]
        chapter.isLastNavigablePage(pages[4]) shouldBe true
        chapter.isLastNavigablePage(pages[5]) shouldBe false
    }
}
