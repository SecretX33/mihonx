package eu.kanade.tachiyomi.data.cache

import android.content.Context
import dev.zacsweers.metro.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import java.io.File

class LinkedCoverCache internal constructor(private val directory: File) {

    @Inject
    constructor(context: Context) : this(File(context.cacheDir, "linked_cover_details"))

    @Serializable
    data class Entry(val url: String, val lastModified: String?)

    fun get(identity: CoverLinkIdentity, mangaId: Long?): Entry? = runCatching {
        Json.decodeFromString<Entry>(file(identity, mangaId).readText()).takeIf { it.url.isNotBlank() }
    }.getOrNull()

    fun set(identity: CoverLinkIdentity, mangaId: Long?, entry: Entry) {
        runCatching {
            directory.mkdirs()
            file(identity, mangaId).writeText(Json.encodeToString(entry))
        }
    }

    private fun file(identity: CoverLinkIdentity, mangaId: Long?): File {
        val key = "${identity.sourceId}:${identity.url}:$mangaId".encodeUtf8().sha256().hex()
        return File(directory, key)
    }
}
