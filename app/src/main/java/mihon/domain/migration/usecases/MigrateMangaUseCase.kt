package mihon.domain.migration.usecases

import dev.zacsweers.metro.Inject
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mihon.core.metro.AppCoroutineScope
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.UpsertTrack
import kotlin.time.Clock

@Inject
class MigrateMangaUseCase(
    @AppCoroutineScope private val scope: CoroutineScope,
    private val sourcePreferences: SourcePreferences,
    private val trackerManager: TrackerManager,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val updateManga: UpdateManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val updateChapter: UpdateChapter,
    private val getCategories: GetCategories,
    private val setMangaCategories: SetMangaCategories,
    private val getTracks: GetTracks,
    private val upsertTrack: UpsertTrack,
    private val coverCache: CoverCache,
    private val getHistory: GetHistory,
    private val upsertHistory: UpsertHistory,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
) {
    private val enhancedServices by lazy { trackerManager.trackers.filterIsInstance<EnhancedTracker>() }

    suspend operator fun invoke(current: Manga, target: Manga, replace: Boolean) {
        val targetSource = sourceManager.get(target.source) ?: return
        val currentSource = sourceManager.get(current.source)
        val flags = sourcePreferences.migrationFlags.get()

        try {
            updateMangaFromRemote(target, fetchChapters = true).getOrThrow()

            // Update chapters read, bookmark, dateFetch and history
            if (MigrationFlag.CHAPTER in flags) {
                val prevMangaChapters = getChaptersByMangaId.await(current.id)
                val mangaChapters = getChaptersByMangaId.await(target.id)

                val maxChapterRead = prevMangaChapters
                    .filter { it.read }
                    .maxOfOrNull { it.chapterNumber }

                val chapterUpdates = mangaChapters
                    .filter { it.isRecognizedNumber }
                    .map { mangaChapter ->
                        val prevChapter = prevMangaChapters
                            .find { it.isRecognizedNumber && it.chapterNumber == mangaChapter.chapterNumber }

                        ChapterUpdate(mangaChapter.id) {
                            excluded = false
                            if (prevChapter != null) {
                                dateFetch = prevChapter.dateFetch
                                bookmark = prevChapter.bookmark
                            }

                            if (maxChapterRead != null && mangaChapter.chapterNumber <= maxChapterRead) {
                                read = true
                            }
                        }
                    }
                updateChapter.awaitAll(chapterUpdates)

                val historyUpdates = buildHistoryUpdates(
                    current = current,
                    oldMangaChapters = prevMangaChapters,
                    newMangaChapters = mangaChapters,
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
                ?.let { upsertTrack.awaitAll(it) }

            // Delete downloaded
            if (MigrationFlag.REMOVE_DOWNLOAD in flags && currentSource != null) {
                // Not awaited, so a slow storage delete doesn't hold up the migration
                scope.launch { downloadManager.deleteManga(current, currentSource) }
            }

            // Update custom cover (recheck if custom cover exists)
            val isMigratingCustomCover = MigrationFlag.CUSTOM_INFO in flags && current.hasCustomCover(coverCache)
            if (isMigratingCustomCover) {
                coverCache.getCustomCoverFile(current.id).inputStream().use {
                    coverCache.setCustomCoverToCache(target, it)
                }
            }
            coverCache.deleteCustomCover(current.id)

            val now = Clock.System.now()

            val currentMangaUpdate = MangaUpdate(current.id) {
                favoriteAt = null
                notes = ""
                customInfo = CustomMangaInfo.ClearAll
                coverLastModified = now
            }
                .takeIf { replace }
            val targetMangaUpdate = MangaUpdate(target.id) {
                favoriteAt = current.favoriteAt?.takeIf { replace } ?: Clock.System.now()
                coverLastModified = if (isMigratingCustomCover) now else target.coverLastModified
                if (MigrationFlag.CUSTOM_INFO in flags) {
                    customInfo =
                        CustomMangaInfo.Set(
                            current.customTitle,
                            current.customAuthor,
                            current.customArtist,
                            current.customDescription,
                            current.customGenre,
                            current.customStatus,
                        )
                }
                chapterFlags = current.chapterFlags
                viewerFlags = current.viewerFlags
                if (MigrationFlag.NOTES in flags) notes = current.notes
            }

            updateManga.awaitAll(listOfNotNull(currentMangaUpdate, targetMangaUpdate))
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
        }
    }

    private suspend fun buildHistoryUpdates(
        current: Manga,
        oldMangaChapters: Collection<Chapter>,
        newMangaChapters: Collection<Chapter>,
    ): List<HistoryUpdate> {
        val prevHistories = getHistory.await(current.id).filter { it.readAt != null }
        val prevChaptersById = oldMangaChapters.filter { it.isRecognizedNumber }.associateBy { it.id }
        val newChaptersByNumber = newMangaChapters.filter { it.isRecognizedNumber }.groupBy { it.chapterNumber }

        val historyWithChapters = prevHistories.mapNotNull { history ->
            prevChaptersById[history.chapterId]?.let { history to it }
        }.sortedWith(
            compareByDescending<Pair<History, Chapter>> { it.first.readAt!! }
                .thenBy { it.second.chapterNumber },
        ).distinctBy { (_, chapter) -> chapter.chapterNumber }

        val historyUpdates = historyWithChapters.flatMap { (history, prevChapter) ->
            val newChapters = newChaptersByNumber[prevChapter.chapterNumber]
                ?: return@flatMap emptyList()
            val readAt = history.readAt!!

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
