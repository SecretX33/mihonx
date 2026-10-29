package eu.kanade.domain.source.model

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

@Serializable
data class SourceFilterSnapshot(
    val version: Int = 1,
    val filters: List<FilterState>,
) {

    fun canRestore(target: FilterList): Boolean = version == 1 && canRestore(filters, target)

    fun restore(target: FilterList): Boolean {
        if (!canRestore(target)) return false
        filters.zip(target).forEach { (saved, filter) -> saved.applyTo(filter) }
        return true
    }

    @Serializable
    data class FilterState(
        val kind: Kind,
        val name: String,
        val options: List<String> = emptyList(),
        val text: String? = null,
        val checked: Boolean? = null,
        val index: Int? = null,
        val ascending: Boolean? = null,
        val children: List<FilterState> = emptyList(),
    ) {
        fun canRestore(filter: Filter<*>): Boolean {
            val current = capture(filter)
            if (kind != current.kind || name != current.name || options != current.options) return false

            return when (kind) {
                Kind.HEADER, Kind.SEPARATOR -> true
                Kind.TEXT -> text != null
                Kind.CHECKBOX -> checked != null
                Kind.TRISTATE -> index in 0..2
                Kind.SELECT -> index != null && index in options.indices
                Kind.SORT -> if (index == null) ascending == null else index in options.indices && ascending != null
                Kind.GROUP -> canRestore(children, (filter as Filter.Group<*>).state.filterIsInstance<Filter<*>>())
            }
        }

        fun applyTo(filter: Filter<*>) {
            when (filter) {
                is Filter.Header, is Filter.Separator -> Unit
                is Filter.Text -> filter.state = text!!
                is Filter.CheckBox -> filter.state = checked!!
                is Filter.TriState -> filter.state = index!!
                is Filter.Select<*> -> filter.state = index!!
                is Filter.Sort -> filter.state = index?.let { Filter.Sort.Selection(it, ascending!!) }
                is Filter.Group<*> -> children.zip(filter.state.filterIsInstance<Filter<*>>())
                    .forEach { (saved, child) -> saved.applyTo(child) }
            }
        }
    }

    @Serializable
    enum class Kind {
        HEADER,
        SEPARATOR,
        TEXT,
        CHECKBOX,
        TRISTATE,
        SELECT,
        SORT,
        GROUP,
    }

    companion object {
        fun capture(filters: FilterList): SourceFilterSnapshot = SourceFilterSnapshot(filters = filters.map(::capture))

        private fun capture(filter: Filter<*>): FilterState = when (filter) {
            is Filter.Header -> FilterState(Kind.HEADER, filter.name)
            is Filter.Separator -> FilterState(Kind.SEPARATOR, filter.name)
            is Filter.Text -> FilterState(Kind.TEXT, filter.name, text = filter.state)
            is Filter.CheckBox -> FilterState(Kind.CHECKBOX, filter.name, checked = filter.state)
            is Filter.TriState -> FilterState(Kind.TRISTATE, filter.name, index = filter.state)
            is Filter.Select<*> -> FilterState(
                Kind.SELECT,
                filter.name,
                options = filter.values.map { it.toString() },
                index = filter.state,
            )
            is Filter.Sort -> FilterState(
                Kind.SORT,
                filter.name,
                options = filter.values.toList(),
                index = filter.state?.index,
                ascending = filter.state?.ascending,
            )
            is Filter.Group<*> -> FilterState(
                Kind.GROUP,
                filter.name,
                children = filter.state.filterIsInstance<Filter<*>>().map(::capture),
            )
        }

        private fun canRestore(saved: List<FilterState>, target: List<Filter<*>>): Boolean =
            saved.size == target.size && saved.zip(target).all { (state, filter) -> state.canRestore(filter) }
    }
}
