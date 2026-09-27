package tachiyomi.domain.manga.model

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test

class ScanlatorFillerPagesTest {
    @Test
    fun `visible indices skip both ends and keep one page for short chapters`() {
        ScanlatorFillerPages(beginning = 2, end = 1).visiblePageIndices(8) shouldBe 2..6
        ScanlatorFillerPages(beginning = 2, end = 1).visiblePageIndices(2) shouldBe 1..1
        ScanlatorFillerPages(beginning = 0, end = 5).visiblePageIndices(2) shouldBe 0..0
    }

    @Test
    fun `display numbers subtract the effective beginning offset`() {
        val fillers = ScanlatorFillerPages(beginning = 2, end = 1)
        fillers.displayPageNumber(2, 8) shouldBe 1L
        fillers.displayPageNumber(6, 8) shouldBe 5L
        fillers.displayPageNumber(1) shouldBe 1L
        fillers.displayPageNumber(1, 2) shouldBe 1L
    }

    @Test
    fun `rules serialize separately from manga memo`() {
        val rules = mapOf(
            "Alpha" to ScanlatorFillerPages(2, 1),
            "" to ScanlatorFillerPages(1, 0),
        )
        val saved = rules.toScanlatorFillerPagesJson()

        saved.toScanlatorFillerPages() shouldBe rules
        emptyMap<String, ScanlatorFillerPages>().toScanlatorFillerPagesJson() shouldBe JsonObject(emptyMap())
    }
}
