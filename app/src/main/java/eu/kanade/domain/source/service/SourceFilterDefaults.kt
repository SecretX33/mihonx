package eu.kanade.domain.source.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.source.model.SourceFilterSnapshot
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class SourceFilterDefaults(private val preferenceStore: PreferenceStore) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun isSet(sourceId: Long): Boolean = preference(sourceId).isSet()

    fun get(sourceId: Long): SourceFilterSnapshot? = runCatching {
        json.decodeFromString<SourceFilterSnapshot>(preference(sourceId).get())
    }.getOrNull()

    fun set(sourceId: Long, snapshot: SourceFilterSnapshot) {
        preference(sourceId).set(json.encodeToString(snapshot))
    }

    fun clear(sourceId: Long) {
        preference(sourceId).delete()
    }

    private fun preference(sourceId: Long) = preferenceStore.getString("mihonx_source_default_filters_$sourceId")
}
