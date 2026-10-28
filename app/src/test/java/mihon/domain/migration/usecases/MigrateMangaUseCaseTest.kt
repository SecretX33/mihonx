package mihon.domain.migration.usecases

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.cache.CoverLink
import eu.kanade.tachiyomi.data.cache.CoverLinkStore
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.Source
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.source.interactor.UpdateMangaFromRemote
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.UpsertTrack
import java.io.File
import java.io.IOException
import kotlin.time.Instant

class MigrateMangaUseCaseTest {

    private val sourcePreferences = mockk<SourcePreferences>()
    private val trackerManager = mockk<TrackerManager>(relaxed = true)
    private val sourceManager = mockk<SourceManager>()
    private val downloadManager = mockk<DownloadManager>(relaxed = true)
    private val updateManga = mockk<UpdateManga>(relaxed = true)
    private val getChaptersByMangaId = mockk<GetChaptersByMangaId>()
    private val updateMangaFromRemote = mockk<UpdateMangaFromRemote>()
    private val updateChapter = mockk<UpdateChapter>(relaxed = true)
    private val getCategories = mockk<GetCategories>(relaxed = true)
    private val setMangaCategories = mockk<SetMangaCategories>(relaxed = true)
    private val getTracks = mockk<GetTracks>()
    private val upsertTrack = mockk<UpsertTrack>(relaxed = true)
    private val coverCache = mockk<CoverCache>(relaxed = true)
    private val getHistory = mockk<GetHistory>()
    private val upsertHistory = mockk<UpsertHistory>()
    private val coverLinkStore = mockk<CoverLinkStore>(relaxed = true)

    @TempDir
    lateinit var tempDir: File

    private val useCase = MigrateMangaUseCase(
        scope = CoroutineScope(Dispatchers.Unconfined),
        sourcePreferences = sourcePreferences,
        trackerManager = trackerManager,
        sourceManager = sourceManager,
        downloadManager = downloadManager,
        updateManga = updateManga,
        getChaptersByMangaId = getChaptersByMangaId,
        updateChapter = updateChapter,
        getCategories = getCategories,
        setMangaCategories = setMangaCategories,
        getTracks = getTracks,
        upsertTrack = upsertTrack,
        coverCache = coverCache,
        getHistory = getHistory,
        upsertHistory = upsertHistory,
        updateMangaFromRemote = updateMangaFromRemote,
        coverLinkStore = coverLinkStore,
    )

    private val current = Manga.create().copy(id = 1L, source = 10L, url = "/original", title = "Original")
    private val target = Manga.create().copy(id = 2L, source = 42L, url = "/destination", title = "Destination")

    private val historyUpdatesSlot = slot<Collection<HistoryUpdate>>()

    @BeforeEach
    fun setUp() {
        val migrationFlagsPreference = mockk<Preference<Set<MigrationFlag>>>()
        every { migrationFlagsPreference.get() } returns setOf(MigrationFlag.CHAPTER)
        every { sourcePreferences.migrationFlags } returns migrationFlagsPreference

        coEvery { sourceManager.get(target.source) } returns mockk<Source>(relaxed = true)
        coEvery { sourceManager.get(current.source) } returns null

        coEvery { updateMangaFromRemote(target, fetchChapters = true) } returns Result.success(mockk())
        coEvery { getTracks.await(current.id) } returns emptyList()
        coEvery { updateManga.awaitAll(any()) } returns true
        every { coverLinkStore.get(any()) } returns null

        coEvery { upsertHistory.awaitAll(capture(historyUpdatesSlot)) } just Runs
    }

    @Test
    fun `no history results in no history updates`() {
        runBlocking {
            coEvery { getChaptersByMangaId.await(current.id) } returns emptyList()
            coEvery { getChaptersByMangaId.await(target.id) } returns emptyList()
            coEvery { getHistory.await(current.id) } returns emptyList()

            useCase(current, target, replace = false)

            historyUpdatesSlot.captured.shouldBeEmpty()
        }
    }

    @Test
    fun `history with null readAt is excluded`() {
        runBlocking {
            val prevChapter = chapter(id = 10, mangaId = current.id, chapterNumber = 1.0)
            val targetChapter = chapter(id = 20, mangaId = target.id, chapterNumber = 1.0)

            coEvery { getChaptersByMangaId.await(current.id) } returns listOf(prevChapter)
            coEvery { getChaptersByMangaId.await(target.id) } returns listOf(targetChapter)
            coEvery { getHistory.await(current.id) } returns listOf(
                history(chapterId = prevChapter.id, readAt = null),
            )

            useCase(current, target, replace = false)

            historyUpdatesSlot.captured.shouldBeEmpty()
        }
    }

    @Test
    fun `history is migrated to matching target chapter`() {
        runBlocking {
            val readAt = Instant.fromEpochMilliseconds(1_000_000L)
            val prevChapter = chapter(id = 10, mangaId = current.id, chapterNumber = 5.0)
            val targetChapter = chapter(id = 20, mangaId = target.id, chapterNumber = 5.0)

            coEvery { getChaptersByMangaId.await(current.id) } returns listOf(prevChapter)
            coEvery { getChaptersByMangaId.await(target.id) } returns listOf(targetChapter)
            coEvery { getHistory.await(current.id) } returns listOf(
                history(chapterId = prevChapter.id, readAt = readAt, readDuration = 100L),
            )

            useCase(current, target, replace = false)

            val updates = historyUpdatesSlot.captured.toList()
            updates shouldHaveSize 1
            updates[0] shouldBe HistoryUpdate(chapterId = 20L, readAt = readAt, sessionReadDuration = 100L)
        }
    }

    @Test
    fun `no matching target chapter produces no history update`() {
        runBlocking {
            val prevChapter = chapter(id = 10, mangaId = current.id, chapterNumber = 5.0)

            coEvery { getChaptersByMangaId.await(current.id) } returns listOf(prevChapter)
            coEvery { getChaptersByMangaId.await(target.id) } returns emptyList()
            coEvery { getHistory.await(current.id) } returns listOf(
                history(chapterId = prevChapter.id, readAt = Instant.fromEpochMilliseconds(1_000_000L)),
            )

            useCase(current, target, replace = false)

            historyUpdatesSlot.captured.shouldBeEmpty()
        }
    }

    @Test
    fun `multiple target chapters at same number each get a history update`() {
        runBlocking {
            val readAt = Instant.fromEpochMilliseconds(1_000_000L)
            val prevChapter = chapter(id = 10, mangaId = current.id, chapterNumber = 5.0)
            val targetChapter1 = chapter(id = 20, mangaId = target.id, chapterNumber = 5.0)
            val targetChapter2 = chapter(id = 21, mangaId = target.id, chapterNumber = 5.0)

            coEvery { getChaptersByMangaId.await(current.id) } returns listOf(prevChapter)
            coEvery { getChaptersByMangaId.await(target.id) } returns listOf(targetChapter1, targetChapter2)
            coEvery { getHistory.await(current.id) } returns listOf(
                history(chapterId = prevChapter.id, readAt = readAt, readDuration = 50L),
            )

            useCase(current, target, replace = false)

            val updates = historyUpdatesSlot.captured.toList()
            updates shouldHaveSize 2
            updates.map { it.chapterId }.toSet() shouldBe setOf(20L, 21L)
            updates.forEach {
                it.readAt shouldBe readAt
                it.sessionReadDuration shouldBe 50L
            }
        }
    }

    @Test
    fun `duplicate histories for same chapter number keeps the most recently read`() {
        runBlocking {
            val olderReadAt = Instant.fromEpochMilliseconds(1_000_000L)
            val newerReadAt = Instant.fromEpochMilliseconds(2_000_000L)
            val prevChapter1 = chapter(id = 10, mangaId = current.id, chapterNumber = 5.0)
            val prevChapter2 = chapter(id = 11, mangaId = current.id, chapterNumber = 5.0)
            val targetChapter = chapter(id = 20, mangaId = target.id, chapterNumber = 5.0)

            coEvery { getChaptersByMangaId.await(current.id) } returns listOf(prevChapter1, prevChapter2)
            coEvery { getChaptersByMangaId.await(target.id) } returns listOf(targetChapter)
            coEvery { getHistory.await(current.id) } returns listOf(
                history(chapterId = prevChapter1.id, readAt = olderReadAt),
                history(chapterId = prevChapter2.id, readAt = newerReadAt),
            )

            useCase(current, target, replace = false)

            val updates = historyUpdatesSlot.captured.toList()
            updates shouldHaveSize 1
            updates[0].readAt shouldBe newerReadAt
        }
    }

    @Test
    fun `chapters with unrecognized number are excluded from history migration`() {
        runBlocking {
            val prevChapter = chapter(id = 10, mangaId = current.id, chapterNumber = -1.0)
            val targetChapter = chapter(id = 20, mangaId = target.id, chapterNumber = -1.0)

            coEvery { getChaptersByMangaId.await(current.id) } returns listOf(prevChapter)
            coEvery { getChaptersByMangaId.await(target.id) } returns listOf(targetChapter)
            coEvery { getHistory.await(current.id) } returns listOf(
                history(chapterId = prevChapter.id, readAt = Instant.fromEpochMilliseconds(1_000_000L)),
            )

            useCase(current, target, replace = false)

            historyUpdatesSlot.captured.shouldBeEmpty()
        }
    }

    @Test
    fun `keeping source cover creates a link to the original entry`() = runBlocking<Unit> {
        selectFlags(MigrationFlag.COVER)
        coEvery { sourceManager.getOrStub(current.source) } returns mockk { every { name } returns "Original source" }

        useCase(current, target, replace = true)

        verify {
            coverLinkStore.set(
                target,
                CoverLink(current.source, current.url, current.effectiveTitle, "Original source"),
            )
            coverLinkStore.set(current, null)
        }
    }

    @Test
    fun `keeping a linked cover carries its exact identity to the destination`() = runBlocking<Unit> {
        selectFlags(MigrationFlag.COVER)
        val link = CoverLink(99, "/cover-entry", "Cover entry", "Cover source")
        every { coverLinkStore.get(current) } returns link

        useCase(current, target, replace = true)

        verify { coverLinkStore.set(target, link) }
        verify { coverLinkStore.set(current, null) }
        coVerifyOrder {
            updateManga.awaitAll(any())
            coverLinkStore.set(target, link)
            updateManga.awaitUpdateCoverLastModified(target.id)
        }
    }

    @Test
    fun `destination cover clears its link and custom image independently of metadata`() = runBlocking<Unit> {
        selectFlags(MigrationFlag.CUSTOM_INFO)
        every { coverLinkStore.get(current) } returns CoverLink(99, "/old-cover", "Old", "Source")

        useCase(current, target, replace = true)

        verify { coverLinkStore.set(target, null) }
        verify { coverCache.deleteCustomCover(target.id) }
        verify(exactly = 0) { coverCache.setCustomCoverToCache(any(), any()) }
    }

    @Test
    fun `copying keeps the original cover link and custom cover`() = runBlocking<Unit> {
        selectFlags(MigrationFlag.COVER)
        val link = CoverLink(99, "/cover-entry", "Cover entry", "Cover source")
        every { coverLinkStore.get(current) } returns link

        useCase(current, target, replace = false)

        verify { coverLinkStore.set(target, link) }
        verify(exactly = 0) { coverLinkStore.set(current, any()) }
        verify(exactly = 0) { coverCache.deleteCustomCover(current.id) }
    }

    @Test
    fun `moving to the linked source entry uses its own cover without a redundant link`() = runBlocking<Unit> {
        selectFlags(MigrationFlag.COVER)
        every { coverLinkStore.get(current) } returns CoverLink(target.source, target.url, "Target", "Target source")

        useCase(current, target, replace = true)

        verify { coverLinkStore.set(target, null) }
    }

    @Test
    fun `custom cover is copied independently of custom text metadata`() = runBlocking<Unit> {
        selectFlags(MigrationFlag.COVER)
        val customCover = File(tempDir, "custom-cover").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        every { coverCache.getCustomCoverFile(current.id) } returns customCover
        every { coverCache.hasCustomCover(current.id) } returns true
        var copiedBytes: List<Byte>? = null
        every { coverCache.setCustomCoverToCache(target, any()) } answers {
            copiedBytes = secondArg<java.io.InputStream>().readBytes().toList()
        }

        useCase(current, target, replace = false)

        copiedBytes shouldBe listOf<Byte>(1, 2, 3)
        verify { coverLinkStore.set(target, null) }
        verify(exactly = 0) { coverCache.deleteCustomCover(current.id) }
    }

    @Test
    fun `linked cover takes priority over a hidden custom image`() = runBlocking<Unit> {
        selectFlags(MigrationFlag.COVER)
        val link = CoverLink(99, "/cover-entry", "Cover entry", "Cover source")
        every { coverLinkStore.get(current) } returns link
        every { coverCache.getCustomCoverFile(current.id) } returns
            File(tempDir, "custom").apply { writeText("custom") }

        useCase(current, target, replace = false)

        verify { coverLinkStore.set(target, link) }
        verify { coverCache.deleteCustomCover(target.id) }
        verify(exactly = 0) { coverCache.setCustomCoverToCache(any(), any()) }
    }

    @Test
    fun `failed source update leaves cover links untouched`() = runBlocking<Unit> {
        selectFlags(MigrationFlag.COVER)
        coEvery { updateMangaFromRemote(target, fetchChapters = true) } returns
            Result.failure(IOException("Unavailable"))

        useCase(current, target, replace = true)

        verify(exactly = 0) { coverLinkStore.set(any(), any()) }
        verify(exactly = 0) { coverCache.deleteCustomCover(any()) }
    }

    @Test
    fun `failed database update does not move cover links`() = runBlocking<Unit> {
        selectFlags(MigrationFlag.COVER)
        every { coverLinkStore.get(current) } returns CoverLink(99, "/cover-entry", "Cover entry", "Cover source")
        coEvery { updateManga.awaitAll(any()) } returns false

        useCase(current, target, replace = true)

        verify(exactly = 0) { coverLinkStore.set(any(), any()) }
        verify(exactly = 0) { coverCache.deleteCustomCover(any()) }
    }

    @Test
    fun `migrating an entry to itself does not change covers`() = runBlocking<Unit> {
        useCase(current, current, replace = true)

        verify(exactly = 0) { coverLinkStore.set(any(), any()) }
        coVerify(exactly = 0) { updateMangaFromRemote(current, fetchChapters = true) }
    }

    @Test
    fun `another entry on the linked source retains the original cover link`() = runBlocking<Unit> {
        selectFlags(MigrationFlag.COVER)
        val link = CoverLink(target.source, "/other-entry", "Cover", "Cover source")
        every { coverLinkStore.get(current) } returns link

        useCase(current, target, replace = true)

        verify { coverLinkStore.set(target, link) }
    }

    @Test
    fun `same entry identity with a different database ID does not migrate`() = runBlocking<Unit> {
        useCase(current, current.copy(id = 99), replace = true)

        verify(exactly = 0) { coverLinkStore.set(any(), any()) }
        coVerify(exactly = 0) { updateMangaFromRemote(any<Manga>(), fetchChapters = true) }
        coVerify(exactly = 0) { updateManga.awaitAll(any()) }
    }

    private fun selectFlags(vararg flags: MigrationFlag) {
        every { sourcePreferences.migrationFlags.get() } returns flags.toSet()
    }

    private fun chapter(id: Long, mangaId: Long, chapterNumber: Double) =
        Chapter.create().copy(id = id, mangaId = mangaId, chapterNumber = chapterNumber)

    private fun history(chapterId: Long, readAt: Instant?, readDuration: Long = 0L) =
        History(id = chapterId * 100, chapterId = chapterId, readAt = readAt, readDuration = readDuration)
}
