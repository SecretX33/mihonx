package tachiyomi.domain.manga.model

import mihon.domain.common.PartialUpdate
import kotlin.time.Instant

sealed interface CustomMangaInfo {
    data object KeepAll : CustomMangaInfo

    data object ClearAll : CustomMangaInfo

    data class Set(
        val title: String?,
        val author: String?,
        val artist: String?,
        val description: String?,
        val genre: List<String>?,
        val status: Long?,
    ) : CustomMangaInfo
}

class MangaUpdate(val id: Long, block: MangaUpdate.() -> Unit) : PartialUpdate() {
    var favoriteAt: Instant? by field(null)
    var lastUpdate: Instant? by field(null)
    var nextUpdate: Instant? by field(null)
    var fetchInterval: Int? by field(null)
    var viewerFlags: Long? by field(null)
    var chapterFlags: Long? by field(null)
    var coverLastModified: Instant? by field(null)
    var notes: String? by field(null)

    var customInfo: CustomMangaInfo = CustomMangaInfo.KeepAll
    var scanlatorFillerPages: Map<String, ScanlatorFillerPages>? by field(null)

    init {
        block()
    }
}
