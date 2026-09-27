package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.manga.model.ScanlatorFillerPages

@Serializable
data class BackupScanlatorFillerPages(
    @ProtoNumber(1) val scanlator: String,
    @ProtoNumber(2) val beginning: Int,
    @ProtoNumber(3) val end: Int,
) {
    fun toDomain(): ScanlatorFillerPages? {
        return if (beginning >= 0 && end >= 0 && (beginning > 0 || end > 0)) {
            ScanlatorFillerPages(beginning, end)
        } else {
            null
        }
    }
}
