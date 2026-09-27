package tachiyomi.domain.manga.model

import android.annotation.SuppressLint
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

@SuppressLint("UnsafeOptInUsageError")
@Serializable
@Immutable
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

    fun displayPageNumber(pageIndex: Long, pageCount: Int? = null): Long {
        val first = pageCount?.takeIf { it > 0 }?.let { visiblePageIndices(it).first } ?: beginning
        return (pageIndex - first + 1).coerceAtLeast(1)
    }
}

fun scanlatorFillerKey(scanlator: String?): String = scanlator?.takeUnless { it.isBlank() } ?: ""

fun JsonObject.toScanlatorFillerPages(): Map<String, ScanlatorFillerPages> {
    return mapNotNull { (scanlator, value) ->
        val counts = value as? JsonObject ?: return@mapNotNull null
        val beginning = (counts["beginning"] as? JsonPrimitive)?.intOrNull ?: 0
        val end = (counts["end"] as? JsonPrimitive)?.intOrNull ?: 0
        if (beginning < 0 || end < 0 || (beginning == 0 && end == 0)) {
            null
        } else {
            scanlator to ScanlatorFillerPages(beginning, end)
        }
    }.toMap()
}

fun Map<String, ScanlatorFillerPages>.toScanlatorFillerPagesJson(): JsonObject {
    return JsonObject(
        filterValues { it.beginning > 0 || it.end > 0 }.mapValues { (_, counts) ->
            JsonObject(
                mapOf(
                    "beginning" to JsonPrimitive(counts.beginning),
                    "end" to JsonPrimitive(counts.end),
                ),
            )
        },
    )
}
