package mihon.domain.migration.usecases

import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.CancellationException
import mihon.domain.migration.models.MigrationFlag
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.toChapterUpdate
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import java.time.Instant
import java.util.Date

class MigrateMangaUseCase(
    private val sourcePreferences: SourcePreferences,
    private val trackerManager: TrackerManager,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val updateManga: UpdateManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val syncChaptersWithSource: SyncChaptersWithSource,
    private val updateChapter: UpdateChapter,
    private val getCategories: GetCategories,
    private val setMangaCategories: SetMangaCategories,
    private val getTracks: GetTracks,
    private val insertTrack: InsertTrack,
    private val coverCache: CoverCache,
    private val getHistory: GetHistory,
    private val upsertHistory: UpsertHistory,
) {
    private val enhancedServices by lazy { trackerManager.trackers.filterIsInstance<EnhancedTracker>() }

    suspend operator fun invoke(current: Manga, target: Manga, replace: Boolean) {
        val targetSource = sourceManager.get(target.source) ?: return
        val currentSource = sourceManager.get(current.source)
        val flags = sourcePreferences.migrationFlags().get()

        try {
            val chapters = targetSource.getChapterList(target.toSManga())

            try {
                syncChaptersWithSource.await(chapters, target, targetSource)
            } catch (_: Exception) {
                // Worst case, chapters won't be synced
            }

            // Update chapters read, bookmark, dateFetch and history
            if (MigrationFlag.CHAPTER in flags) {
                val prevMangaChapters = getChaptersByMangaId.await(current.id)
                val mangaChapters = getChaptersByMangaId.await(target.id)

                val maxChapterRead = prevMangaChapters
                    .filter { it.read }
                    .maxOfOrNull { it.chapterNumber }

                val updatedMangaChapters = mangaChapters.map { mangaChapter ->
                    var updatedChapter = mangaChapter
                    if (updatedChapter.isRecognizedNumber) {
                        val prevChapter = prevMangaChapters
                            .find { it.isRecognizedNumber && it.chapterNumber == updatedChapter.chapterNumber }

                        if (prevChapter != null) {
                            updatedChapter = updatedChapter.copy(
                                dateFetch = prevChapter.dateFetch,
                                bookmark = prevChapter.bookmark,
                            )
                        }

                        if (maxChapterRead != null && updatedChapter.chapterNumber <= maxChapterRead) {
                            updatedChapter = updatedChapter.copy(read = true)
                        }
                    }

                    updatedChapter = updatedChapter.copy(excluded = false)
                    updatedChapter
                }

                val chapterUpdates = updatedMangaChapters.map { it.toChapterUpdate() }
                updateChapter.awaitAll(chapterUpdates)

                val historyUpdates = buildHistoryUpdates(
                    current = current,
                    prevMangaChapters = prevMangaChapters,
                    mangaChapters = mangaChapters,
                )
                upsertHistory.awaitAll(historyUpdates)
            }

            // Update categories
            if (MigrationFlag.CATEGORY in flags) {
                val categoryIds = getCategories.await(current.id).map { it.id }
                setMangaCategories.await(target.id, categoryIds)
            }

            // Update track
            getTracks.await(current.id).mapNotNull { track ->
                val updatedTrack = track.copy(mangaId = target.id)

                val service = enhancedServices
                    .firstOrNull { it.isTrackFrom(updatedTrack, current, currentSource) }

                if (service != null) {
                    service.migrateTrack(updatedTrack, target, targetSource)
                } else {
                    updatedTrack
                }
            }
                .takeIf { it.isNotEmpty() }
                ?.let { insertTrack.awaitAll(it) }

            // Delete downloaded
            if (MigrationFlag.REMOVE_DOWNLOAD in flags && currentSource != null) {
                downloadManager.deleteManga(current, currentSource)
            }

            // Migrate custom series info (cover, notes, text metadata)
            val isMigratingCustomCover = MigrationFlag.CUSTOM_INFO in flags && current.hasCustomCover()
            if (isMigratingCustomCover) {
                coverCache.setCustomCoverToCache(target, coverCache.getCustomCoverFile(current.id).inputStream())
            }
            coverCache.deleteCustomCover(current.id)

            val now = Instant.now().toEpochMilli()

            val currentMangaUpdate = MangaUpdate(
                id = current.id,
                favorite = false,
                dateAdded = 0,
                notes = "",
                customInfo = CustomMangaInfo.ClearAll,
                coverLastModified = now,
            ).takeIf { replace }

            val targetMangaUpdate = MangaUpdate(
                id = target.id,
                favorite = true,
                chapterFlags = current.chapterFlags,
                viewerFlags = current.viewerFlags,
                dateAdded = if (replace) current.dateAdded else now,
                coverLastModified = if (isMigratingCustomCover) now else target.coverLastModified,
                notes = if (MigrationFlag.NOTES in flags) current.notes else null,
                customInfo = if (MigrationFlag.CUSTOM_INFO in flags) {
                    CustomMangaInfo.Set(
                        title = current.customTitle,
                        author = current.customAuthor,
                        artist = current.customArtist,
                        description = current.customDescription,
                        genre = current.customGenre,
                        status = current.customStatus,
                    )
                } else {
                    CustomMangaInfo.KeepAll
                },
            )

            updateManga.awaitAll(listOfNotNull(currentMangaUpdate, targetMangaUpdate))
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
        }
    }

    private suspend fun buildHistoryUpdates(
        current: Manga,
        prevMangaChapters: Collection<Chapter>,
        mangaChapters: Collection<Chapter>,
    ): List<HistoryUpdate> {
        val prevHistories = getHistory.await(current.id).filter { it.readAt != null }
        val prevChaptersById = prevMangaChapters.filter { it.isRecognizedNumber }.associateBy { it.id }

        val historyWithChapters = prevHistories.mapNotNull { history ->
            prevChaptersById[history.chapterId]?.let { history to it }
        }.sortedWith(
            compareByDescending<Pair<History, Chapter>> { it.first.readAt ?: Date.from(Instant.EPOCH) }
                .thenBy { it.second.chapterNumber },
        )
            .distinctBy { (_, chapter) -> chapter.chapterNumber }

        val historyUpdates = historyWithChapters.flatMap { (history, prevChapter) ->
            val readAt = history.readAt!!
            val newChapters = mangaChapters.filter {
                it.isRecognizedNumber && it.chapterNumber == prevChapter.chapterNumber
            }.ifEmpty {
                return@flatMap emptyList()
            }

            newChapters.map {
                HistoryUpdate(
                    chapterId = it.id,
                    readAt = readAt,
                    sessionReadDuration = history.readDuration,
                )
            }
        }
        return historyUpdates
    }
}
