package eu.kanade.domain.source.service

import eu.kanade.domain.source.model.SourceFilterSnapshot
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference
import tachiyomi.core.common.preference.PreferenceStore

class SourceFilterDefaultsTest {

    private val snapshot = SourceFilterSnapshot.capture(FilterList(object : Filter.CheckBox("Completed", true) {}))

    @Test
    fun `sources initially have no saved filters`() {
        val store = SourceFilterDefaults(preferences())

        store.get(1) shouldBe null
        store.isSet(1) shouldBe false
    }

    @Test
    fun `defaults survive a new store instance and remain independent per source`() {
        val preferences = preferences()
        val store = SourceFilterDefaults(preferences)
        store.set(1, snapshot)
        store.set(2, snapshot.copy(filters = emptyList()))

        val reopened = SourceFilterDefaults(preferences)
        reopened.get(1) shouldBe snapshot
        reopened.get(2) shouldBe snapshot.copy(filters = emptyList())
        reopened.get(3) shouldBe null
        reopened.isSet(1) shouldBe true
    }

    @Test
    fun `updating and clearing defaults preserves other sources`() {
        val store = SourceFilterDefaults(preferences())
        store.set(1, snapshot)
        store.set(2, snapshot)
        val replacement = snapshot.copy(filters = emptyList())
        store.set(1, replacement)
        store.get(1) shouldBe replacement

        store.clear(1)

        store.get(1) shouldBe null
        store.isSet(1) shouldBe false
        store.get(2) shouldBe snapshot
    }

    @Test
    fun `malformed defaults remain clearable without affecting valid sources`() {
        val preferences = preferences()
        preferences.getString("mihonx_source_default_filters_1").set("invalid json")
        val store = SourceFilterDefaults(preferences)
        store.set(2, snapshot)

        store.get(1) shouldBe null
        store.isSet(1) shouldBe true
        store.get(2) shouldBe snapshot

        store.clear(1)

        store.isSet(1) shouldBe false
    }

    @Test
    fun `app settings backup carries defaults through protobuf serialization`() {
        val preferences = preferences()
        SourceFilterDefaults(preferences).set(1, snapshot)
        val creator = PreferenceBackupCreator(sourceManager = mockk(), preferenceStore = preferences)
        val backup = Backup(backupManga = emptyList(), backupPreferences = creator.createApp(false))
        val decoded = ProtoBuf.decodeFromByteArray<Backup>(ProtoBuf.encodeToByteArray(backup))
        val restoredPreferences = preferences()

        decoded.backupPreferences.forEach {
            restoredPreferences.getString(it.key).set((it.value as StringPreferenceValue).value)
        }

        SourceFilterDefaults(restoredPreferences).get(1) shouldBe snapshot
    }

    private fun preferences(): PreferenceStore {
        val values = mutableMapOf<String, InMemoryPreference<String>>()
        return mockk {
            every { getString(any(), any()) } answers {
                val key = firstArg<String>()
                values.getOrPut(key) { InMemoryPreference(key, null, secondArg()) }
            }
            every { getAll() } answers { values.filterValues { it.isSet() }.mapValues { it.value.get() } }
        }
    }
}
