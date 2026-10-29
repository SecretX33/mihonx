package eu.kanade.domain.source.model

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class SourceFilterSnapshotTest {

    @Test
    fun `all filter types and nested groups survive serialization`() {
        val filters = filters()
        (filters[2] as Filter.Text).state = "Author"
        (filters[3] as Filter.CheckBox).state = true
        (filters[4] as Filter.TriState).state = Filter.TriState.STATE_EXCLUDE
        (filters[5] as Filter.Select<*>).state = 1
        (filters[6] as Filter.Sort).state = Filter.Sort.Selection(1, false)
        val nested = (filters[7] as Filter.Group<*>).state[0] as Filter.Group<*>
        (nested.state[0] as Filter.CheckBox).state = true
        val saved = SourceFilterSnapshot.capture(filters)
        val decoded = Json.decodeFromString<SourceFilterSnapshot>(Json.encodeToString(saved))
        val target = filters()

        decoded.restore(target) shouldBe true

        SourceFilterSnapshot.capture(target) shouldBe saved
        target[5].shouldBeInstanceOf<StatusFilter>()
        target[6].shouldBeInstanceOf<SortFilter>()
    }

    @Test
    fun `editing filters cannot change a captured snapshot`() {
        val filters = filters()
        val saved = SourceFilterSnapshot.capture(filters)
        (filters[3] as Filter.CheckBox).state = true
        val nested = (filters[7] as Filter.Group<*>).state[0] as Filter.Group<*>
        (nested.state[0] as Filter.CheckBox).state = true

        saved.restore(filters) shouldBe true

        SourceFilterSnapshot.capture(filters) shouldBe SourceFilterSnapshot.capture(filters())
    }

    @Test
    fun `null sort selection is preserved`() {
        val filters = filters()
        (filters[6] as Filter.Sort).state = null
        val target = filters()

        SourceFilterSnapshot.capture(filters).restore(target) shouldBe true

        (target[6] as Filter.Sort).state shouldBe null
    }

    @Test
    fun `changed filter structure never partially restores selections`() {
        val original = filters()
        (original[3] as Filter.CheckBox).state = true
        val saved = SourceFilterSnapshot.capture(original)
        val changedLists = listOf(
            FilterList(filters().dropLast(1)),
            FilterList(filters().reversed()),
            FilterList(filters().toMutableList().apply { this[5] = StatusFilter(arrayOf("Completed", "Any")) }),
            FilterList(filters().toMutableList().apply { this[5] = object : Filter.Text("Status") {} }),
            FilterList(filters().toMutableList().apply { this[6] = SortFilter(arrayOf("Title", "Views")) }),
            FilterList(
                filters().toMutableList().apply {
                    this[7] = object : Filter.Group<Filter<*>>("Genres", listOf(Filter.Header("Changed"))) {}
                },
            ),
        )

        changedLists.forEach { target ->
            val before = SourceFilterSnapshot.capture(target)
            saved.restore(target) shouldBe false
            SourceFilterSnapshot.capture(target) shouldBe before
        }
    }

    @Test
    fun `out of range and incomplete selections are rejected without mutation`() {
        val saved = SourceFilterSnapshot.capture(filters())
        val invalidFilters = listOf(
            2 to saved.filters[2].copy(text = null),
            3 to saved.filters[3].copy(checked = null),
            4 to saved.filters[4].copy(index = 3),
            5 to saved.filters[5].copy(index = -1),
            5 to saved.filters[5].copy(index = 2),
            6 to saved.filters[6].copy(index = 2),
            6 to saved.filters[6].copy(ascending = null),
        )

        invalidFilters.forEach { (index, invalid) ->
            val snapshot = saved.copy(filters = saved.filters.toMutableList().apply { this[index] = invalid })
            val target = filters()
            snapshot.restore(target) shouldBe false
            SourceFilterSnapshot.capture(target) shouldBe saved
        }
    }

    @Test
    fun `unknown snapshot versions are rejected`() {
        val filters = filters()
        val saved = SourceFilterSnapshot.capture(filters)

        saved.copy(version = 2).restore(filters) shouldBe false

        SourceFilterSnapshot.capture(filters) shouldBe saved
    }

    private fun filters() = FilterList(
        Filter.Header("Filters"),
        Filter.Separator(),
        object : Filter.Text("Author") {},
        object : Filter.CheckBox("Completed") {},
        object : Filter.TriState("Action") {},
        StatusFilter(),
        SortFilter(),
        object : Filter.Group<Filter<*>>(
            "Genres",
            listOf(
                object : Filter.Group<Filter<*>>(
                    "Included",
                    listOf(object : Filter.CheckBox("Drama") {}),
                ) {},
            ),
        ) {},
    )

    private class StatusFilter(values: Array<String> = arrayOf("Any", "Completed")) :
        Filter.Select<String>("Status", values)

    private class SortFilter(values: Array<String> = arrayOf("Views", "Title")) :
        Filter.Sort("Sort", values, Selection(0, true))
}
