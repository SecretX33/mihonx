package tachiyomi.domain.manga.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

data class ScanlatorFillerPages(val beginning: Int = 0, val end: Int = 0) {
    init {
        require(beginning >= 0 && end >= 0)
    }

    fun visiblePageIndices(pageCount: Int): IntRange {
        require(pageCount > 0)
        val first = beginning.coerceAtMost(pageCount - 1)
        val last = pageCount - 1 - end.coerceAtMost(pageCount - 1 - first)
        return first..last
    }
}

private const val FILLER_PAGES_KEY = "mihonx_scanlator_filler_pages"

fun scanlatorFillerKey(scanlator: String?): String = scanlator?.takeUnless { it.isBlank() } ?: ""

fun JsonObject.scanlatorFillerPages(): Map<String, ScanlatorFillerPages> {
    val stored = get(FILLER_PAGES_KEY) as? JsonObject ?: return emptyMap()
    return stored.mapNotNull { (scanlator, value) ->
        val counts = value as? JsonObject ?: return@mapNotNull null
        val beginning = (counts["beginning"] as? JsonPrimitive)?.intOrNull ?: 0
        val end = (counts["end"] as? JsonPrimitive)?.intOrNull ?: 0
        if (beginning < 0 || end < 0 || beginning == 0 && end == 0) {
            null
        } else {
            scanlator to ScanlatorFillerPages(beginning, end)
        }
    }.toMap()
}

fun JsonObject.withScanlatorFillerPages(rules: Map<String, ScanlatorFillerPages>): JsonObject {
    val normalized = rules.filterValues { it.beginning > 0 || it.end > 0 }
    return JsonObject(toMutableMap().apply {
        if (normalized.isEmpty()) {
            remove(FILLER_PAGES_KEY)
        } else {
            put(
                FILLER_PAGES_KEY,
                JsonObject(normalized.mapValues { (_, counts) ->
                    JsonObject(mapOf("beginning" to JsonPrimitive(counts.beginning), "end" to JsonPrimitive(counts.end)))
                }),
            )
        }
    })
}

fun JsonObject.withPreservedScanlatorFillerPages(from: JsonObject): JsonObject {
    val value = from[FILLER_PAGES_KEY] ?: return this
    return JsonObject(toMutableMap().apply { put(FILLER_PAGES_KEY, value) })
}
