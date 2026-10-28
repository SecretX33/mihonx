package mihon.feature.migration.dialog

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.cache.CoverLink
import eu.kanade.tachiyomi.data.cache.CoverLinkStore
import eu.kanade.tachiyomi.data.download.DownloadManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import mihon.domain.migration.models.MigrationFlag
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.manga.model.Manga

class MigrateDialogViewModelTest {

    private val flags = mockk<Preference<Set<MigrationFlag>>> {
        every { get() } returns setOf(MigrationFlag.CHAPTER, MigrationFlag.CATEGORY, MigrationFlag.COVER)
    }
    private val coverCache = mockk<CoverCache> { every { hasCustomCover(any()) } returns false }
    private val coverLinks = mockk<CoverLinkStore> { every { this@mockk.get(any<Manga>()) } returns null }
    private val model = MigrateDialogViewModel(
        sourcePreference = mockk<SourcePreferences> { every { migrationFlags } returns flags },
        coverCache = coverCache,
        downloadManager = mockk<DownloadManager> { every { getDownloadCount(any()) } returns 0 },
        migrateManga = mockk(),
        coverLinkStore = coverLinks,
    )
    private val current = Manga.create().copy(id = 1, source = 10, url = "/original")
    private val target = Manga.create().copy(id = 2, source = 20, url = "/destination")

    @Test
    fun `linked cover defaults to Current regardless of saved choice`() {
        every { flags.get() } returns setOf(MigrationFlag.CHAPTER)
        every { coverLinks.get(current) } returns CoverLink(30, "/cover", "Cover", "Source")

        model.init(current, target)

        (MigrationFlag.COVER in model.state.value.selectedFlags) shouldBe true
        (MigrationFlag.CHAPTER in model.state.value.selectedFlags) shouldBe true
    }

    @Test
    fun `unlinked cover defaults to Destination regardless of saved choice`() {
        model.init(current, target)

        (MigrationFlag.COVER in model.state.value.selectedFlags) shouldBe false
        (MigrationFlag.CHAPTER in model.state.value.selectedFlags) shouldBe true
    }

    @Test
    fun `custom cover without a link defaults to Destination`() {
        every { coverCache.hasCustomCover(current.id) } returns true

        model.init(current, target)

        (MigrationFlag.COVER in model.state.value.selectedFlags) shouldBe false
    }

    @Test
    fun `linked destination stays migratable without a redundant cover choice`() {
        every { coverLinks.get(current) } returns CoverLink(target.source, target.url, "Cover", "Source")

        model.init(current, target)
        model.toggleSelection(MigrationFlag.COVER)

        model.state.value.coverMatchesDestination shouldBe true
        model.state.value.canMigrate shouldBe true
        (MigrationFlag.COVER in model.state.value.applicableFlags) shouldBe false
        (MigrationFlag.COVER in model.state.value.selectedFlags) shouldBe false
    }

    @Test
    fun `another entry from the linked source still offers the cover choice`() {
        every { coverLinks.get(current) } returns CoverLink(target.source, "/other-entry", "Cover", "Source")

        model.init(current, target)

        model.state.value.coverMatchesDestination shouldBe false
        (MigrationFlag.COVER in model.state.value.applicableFlags) shouldBe true
        (MigrationFlag.COVER in model.state.value.selectedFlags) shouldBe true
    }

    @Test
    fun `same source and URL cannot be migrated even with a different database ID`() {
        model.init(current, current.copy(id = 99))

        model.state.value.canMigrate shouldBe false
    }
}
