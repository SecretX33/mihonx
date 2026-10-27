package eu.kanade.tachiyomi.data.cache

import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.manga.model.Manga

class CoverLinkStoreTest {

    private val manga = Manga.create().copy(id = 1, source = 10, url = "/original", title = "Original")
    private val link = CoverLink(20, "/selected", "Selected", "Cover source")

    @Test
    fun `link survives changed database IDs and titles`() {
        val preferences = preferences()
        CoverLinkStore(preferences).set(manga, link)

        CoverLinkStore(preferences).get(manga.copy(id = 99, title = "Renamed", customTitle = "Custom")) shouldBe link
        CoverLinkStore(preferences).get(manga.copy(source = 11)) shouldBe null
        CoverLinkStore(preferences).get(manga.copy(url = "/different")) shouldBe null
    }

    @Test
    fun `changing and removing a link preserves other entries`() = runTest {
        val store = CoverLinkStore(preferences())
        val other = manga.copy(url = "/other")
        store.set(manga, link)
        store.set(other, link)
        store.set(manga, link.copy(url = "/replacement"))

        store.observe(manga).first()?.url shouldBe "/replacement"
        store.set(manga, null)

        store.get(manga) shouldBe null
        store.get(other) shouldBe link
    }

    @Test
    fun `malformed preference does not prevent reading valid links`() {
        val preferences = preferences()
        val store = CoverLinkStore(preferences)
        store.set(manga, link)
        val preference = preferences.getStringSet("mihonx_cover_links")
        preference.set(preference.get() + "invalid json")

        store.get(manga) shouldBe link
    }

    @Test
    fun `self link clears an existing alternate cover`() = runTest {
        val store = CoverLinkStore(preferences())
        store.set(manga, link)
        store.set(manga, CoverLink(manga.source, manga.url, manga.title, "Original source"))

        store.get(manga) shouldBe null
        store.observe(manga).first() shouldBe null
        store.isEmpty() shouldBe true
    }

    @Test
    fun `self links from old preferences are ignored`() = runTest {
        val preferences = preferences()
        preferences.getStringSet("mihonx_cover_links").set(
            setOf(
                """
                {
                    "manga": {"sourceId": 10, "url": "/original"},
                    "cover": {"sourceId": 10, "url": "/original", "title": "Original", "sourceName": "Source"}
                }
                """.trimIndent(),
            ),
        )
        val store = CoverLinkStore(preferences)

        store.get(manga) shouldBe null
        store.observe(manga).first() shouldBe null
        store.isEmpty() shouldBe true
    }

    @Test
    fun `app settings backup carries links through protobuf serialization`() {
        val preferences = preferences()
        CoverLinkStore(preferences).set(manga, link)
        val creator = PreferenceBackupCreator(sourceManager = mockk(), preferenceStore = preferences)
        val backup = Backup(backupManga = emptyList(), backupPreferences = creator.createApp(false))

        val decoded = ProtoBuf.decodeFromByteArray<Backup>(ProtoBuf.encodeToByteArray(backup))
        val restoredPreferences = preferences()
        decoded.backupPreferences.forEach {
            restoredPreferences.getStringSet(it.key).set((it.value as StringSetPreferenceValue).value)
        }

        CoverLinkStore(restoredPreferences).get(manga.copy(id = 100)) shouldBe link
    }

    private fun preferences(): PreferenceStore {
        val values = mutableMapOf<String, MutableStateFlow<Set<String>>>()
        val preferences = mutableMapOf<String, Preference<Set<String>>>()
        return mockk {
            every { getStringSet(any(), any()) } answers {
                val key = firstArg<String>()
                val default = secondArg<Set<String>>()
                preferences.getOrPut(key) {
                    val flow = values.getOrPut(key) { MutableStateFlow(default) }
                    mockk {
                        every { get() } answers { flow.value }
                        every { set(any()) } answers { flow.value = firstArg() }
                        every { changes() } returns flow
                    }
                }
            }
            every { getAll() } answers { values.mapValues { it.value.value } }
        }
    }
}
