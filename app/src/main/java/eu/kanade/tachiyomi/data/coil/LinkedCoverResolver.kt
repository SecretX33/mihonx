package eu.kanade.tachiyomi.data.coil

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.cache.CoverLink
import eu.kanade.tachiyomi.data.cache.CoverLinkIdentity
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.source.service.SourceManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

@Inject
@SingleIn(AppScope::class)
class LinkedCoverResolver(
    private val sourceManager: SourceManager,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private data class CachedCover(val cover: MangaCover?, val expiresAt: Long)

    private val cache = ConcurrentHashMap<CoverLinkIdentity, CachedCover>()
    private val permits = ConcurrentHashMap<CoverLinkIdentity, Semaphore>()

    suspend fun resolve(link: CoverLink, allowNetwork: Boolean): MangaCover? {
        val source = sourceManager.get(link.sourceId) as? HttpSource ?: return null
        val identity = CoverLinkIdentity(link.sourceId, link.url)
        return permits.computeIfAbsent(identity) { Semaphore(1) }.withPermit {
            cache[identity]?.takeIf { it.expiresAt > now() }?.let { return@withPermit it.cover }
            if (!allowNetwork) return@withPermit null

            val cover = try {
                withTimeoutOrNull(10_000) {
                    withContext(Dispatchers.IO) {
                        val manga = SManga.create().apply {
                            url = link.url
                            title = link.title
                        }
                        source.getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
                            .manga.thumbnail_url
                            ?.takeIf { it.isNotBlank() }
                            ?.let {
                                MangaCover(
                                    mangaId = -1,
                                    sourceId = link.sourceId,
                                    isMangaFavorite = false,
                                    url = it,
                                    lastModified = null,
                                )
                            }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            cache[identity] = CachedCover(cover, now() + if (cover == null) 60_000 else 3_600_000)
            cover
        }
    }
}
