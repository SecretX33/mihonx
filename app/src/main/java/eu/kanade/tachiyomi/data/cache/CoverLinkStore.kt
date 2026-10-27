package eu.kanade.tachiyomi.data.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.manga.model.Manga

@Serializable
data class CoverLink(
    val sourceId: Long,
    val url: String,
    val title: String,
    val sourceName: String,
)

@Serializable
data class CoverLinkIdentity(val sourceId: Long, val url: String)

@Serializable
private data class CoverLinkBinding(val manga: CoverLinkIdentity, val cover: CoverLink)

class CoverLinkStore(preferenceStore: PreferenceStore) {

    private data class Snapshot(val encoded: Set<String>, val links: Map<CoverLinkIdentity, CoverLink>)

    private val preference = preferenceStore.getStringSet("mihonx_cover_links")
    private val json = Json { ignoreUnknownKeys = true }
    private var snapshot = Snapshot(emptySet(), emptyMap())
    private val mutableUsage = MutableStateFlow<Map<CoverLinkIdentity, Boolean>>(emptyMap())
    val usage = mutableUsage.asStateFlow()

    fun isEmpty(): Boolean = preference.get().isEmpty()

    fun get(manga: Manga): CoverLink? = bindings()[manga.coverLinkIdentity()]

    fun observe(manga: Manga): Flow<CoverLink?> = preference.changes()
        .map { bindings(it)[manga.coverLinkIdentity()] }
        .distinctUntilChanged()

    @Synchronized
    fun set(manga: Manga, cover: CoverLink?) {
        val identity = manga.coverLinkIdentity()
        val bindings = bindings().toMutableMap().apply {
            if (cover == null) remove(identity) else put(identity, cover)
        }
        preference.set(
            bindings.map { (manga, cover) -> json.encodeToString(CoverLinkBinding(manga, cover)) }
                .toSet(),
        )
        mutableUsage.update { it - identity }
    }

    @Synchronized
    fun recordUsage(manga: Manga, cover: CoverLink, used: Boolean) {
        if (get(manga) != cover) return
        mutableUsage.update { it + (manga.coverLinkIdentity() to used) }
    }

    @Synchronized
    private fun bindings(values: Set<String> = preference.get()): Map<CoverLinkIdentity, CoverLink> {
        if (snapshot.encoded != values) {
            val links = values.mapNotNull {
                runCatching { json.decodeFromString<CoverLinkBinding>(it) }.getOrNull()
            }.associate { it.manga to it.cover }
            snapshot = Snapshot(values, links)
        }
        return snapshot.links
    }
}

fun Manga.coverLinkIdentity(): CoverLinkIdentity = CoverLinkIdentity(source, url)
