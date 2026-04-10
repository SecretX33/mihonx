package mihon.domain.migration.usecases

import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.Source
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import mihon.domain.migration.models.MigrationFlag
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import tachiyomi.domain.track.interactor.InsertTrack
import java.util.Date

class MigrateMangaUseCaseTest {

    private val sourcePreferences = mockk<SourcePreferences>()
    private val trackerManager = mockk<TrackerManager>(relaxed = true)
    private val sourceManager = mockk<SourceManager>()
    private val downloadManager = mockk<DownloadManager>(relaxed = true)
    private val updateManga = mockk<UpdateManga>(relaxed = true)
    private val getChaptersByMangaId = mockk<GetChaptersByMangaId>()
    private val syncChaptersWithSource = mockk<SyncChaptersWithSource>(relaxed = true)
    private val updateChapter = mockk<UpdateChapter>(relaxed = true)
    private val getCategories = mockk<GetCategories>(relaxed = true)
    private val setMangaCategories = mockk<SetMangaCategories>(relaxed = true)
    private val getTracks = mockk<GetTracks>()
    private val insertTrack = mockk<InsertTrack>(relaxed = true)
    private val coverCache = mockk<CoverCache>(relaxed = true)
    private val getHistory = mockk<GetHistory>()
    private val upsertHistory = mockk<UpsertHistory>()

    private val useCase = MigrateMangaUseCase(
        sourcePreferences = sourcePreferences,
        trackerManager = trackerManager,
        sourceManager = sourceManager,
        downloadManager = downloadManager,
        updateManga = updateManga,
        getChaptersByMangaId = getChaptersByMangaId,
        syncChaptersWithSource = syncChaptersWithSource,
        updateChapter = updateChapter,
        getCategories = getCategories,
        setMangaCategories = setMangaCategories,
        getTracks = getTracks,
        insertTrack = insertTrack,
        coverCache = coverCache,
        getHistory = getHistory,
        upsertHistory = upsertHistory,
    )

    private val current = Manga.create().copy(id = 1L, source = 10L)
    private val target = Manga.create().copy(id = 2L, source = 42L)

    private val historyUpdatesSlot = slot<Collection<HistoryUpdate>>()

    @BeforeEach
    fun setUp() {
        val migrationFlagsPreference = mockk<Preference<Set<MigrationFlag>>>()
        every { migrationFlagsPreference.get() } returns setOf(MigrationFlag.CHAPTER)
        every { sourcePreferences.migrationFlags } returns migrationFlagsPreference

        every { sourceManager.get(target.source) } returns mockk<Source>(relaxed = true)
        every { sourceManager.get(current.source) } returns null

        coEvery { getTracks.await(current.id) } returns emptyList()

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
            val readAt = Date(1_000_000L)
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
                history(chapterId = prevChapter.id, readAt = Date(1_000_000L)),
            )

            useCase(current, target, replace = false)

            historyUpdatesSlot.captured.shouldBeEmpty()
        }
    }

    @Test
    fun `multiple target chapters at same number each get a history update`() {
        runBlocking {
            val readAt = Date(1_000_000L)
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
            val olderReadAt = Date(1_000_000L)
            val newerReadAt = Date(2_000_000L)
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
                history(chapterId = prevChapter.id, readAt = Date(1_000_000L)),
            )

            useCase(current, target, replace = false)

            historyUpdatesSlot.captured.shouldBeEmpty()
        }
    }

    private fun chapter(id: Long, mangaId: Long, chapterNumber: Double) =
        Chapter.create().copy(id = id, mangaId = mangaId, chapterNumber = chapterNumber)

    private fun history(chapterId: Long, readAt: Date?, readDuration: Long = 0L) =
        History(id = chapterId * 100, chapterId = chapterId, readAt = readAt, readDuration = readDuration)
}
