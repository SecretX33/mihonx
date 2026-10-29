package eu.kanade.tachiyomi.data.cache

import android.content.Context
import coil3.disk.DiskCache
import coil3.imageLoader
import dev.zacsweers.metro.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class LinkedCoverCache internal constructor(private val diskCache: () -> DiskCache?) {

    @Inject
    constructor(context: Context) : this({ context.imageLoader.diskCache })

    @Serializable
    data class Entry(val url: String?, val lastModified: String?, val retryAt: Long = 0)

    fun get(key: String): Entry? = runCatching {
        val cache = diskCache() ?: return null
        cache.openSnapshot(key)?.use { snapshot ->
            cache.fileSystem.read(snapshot.data) { Json.decodeFromString<Entry>(readUtf8()) }
                .takeIf { it.url == null || it.url.isNotBlank() }
        }
    }.getOrNull()

    fun set(key: String, entry: Entry) {
        runCatching {
            val cache = diskCache() ?: return
            val editor = cache.openEditor(key) ?: return
            try {
                cache.fileSystem.write(editor.data) { writeUtf8(Json.encodeToString(entry)) }
                editor.commit()
            } catch (e: Exception) {
                editor.abort()
                throw e
            }
        }
    }
}
