package eu.kanade.tachiyomi.ui.reader

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class ReaderChapterFilterTest {

    @Test
    fun `excluded chapters are removed from reader navigation`() {
        val selected = chapter(1)
        val excluded = chapter(2, excluded = true)
        val next = chapter(3)

        listOf(selected, excluded, next)
            .filterExcludedChaptersForReader(selected)
            .map { it.id }
            .shouldContainExactly(1L, 3L)
    }

    @Test
    fun `selected excluded chapter remains readable`() {
        val selected = chapter(1, excluded = true)
        val next = chapter(2)

        listOf(selected, next)
            .filterExcludedChaptersForReader(selected)
            .map { it.id }
            .shouldContainExactly(1L, 2L)
    }

    private fun chapter(id: Long, excluded: Boolean = false): Chapter {
        return Chapter.create().copy(id = id, excluded = excluded)
    }
}
