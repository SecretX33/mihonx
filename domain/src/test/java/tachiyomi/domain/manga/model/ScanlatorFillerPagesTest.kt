package tachiyomi.domain.manga.model

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

class ScanlatorFillerPagesTest {
    @Test
    fun `visible indices skip both ends and keep one page for short chapters`() {
        ScanlatorFillerPages(beginning = 2, end = 1).visiblePageIndices(8) shouldBe 2..6
        ScanlatorFillerPages(beginning = 2, end = 1).visiblePageIndices(2) shouldBe 1..1
        ScanlatorFillerPages(beginning = 0, end = 5).visiblePageIndices(2) shouldBe 0..0
    }

    @Test
    fun `rules keep unrelated manga memo and can be removed`() {
        val sourceMemo = JsonObject(mapOf("source" to JsonPrimitive("metadata")))
        val rules = mapOf(
            "Alpha" to ScanlatorFillerPages(2, 1),
            "" to ScanlatorFillerPages(1, 0),
        )
        val saved = sourceMemo.withScanlatorFillerPages(rules)

        saved.scanlatorFillerPages() shouldBe rules
        saved["source"] shouldBe JsonPrimitive("metadata")
        saved.withScanlatorFillerPages(emptyMap()).scanlatorFillerPages() shouldBe emptyMap()
        sourceMemo.withPreservedScanlatorFillerPages(saved).scanlatorFillerPages() shouldBe rules
    }
}
