package eu.kanade.tachiyomi.data.coil

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.cache.CoverLink
import eu.kanade.tachiyomi.data.cache.CoverLinkIdentity
import eu.kanade.tachiyomi.data.cache.LinkedCoverCache
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.source.service.SourceManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

@Inject
@SingleIn(AppScope::class)
class LinkedCoverResolver(
    private val sourceManager: SourceManager,
    private val diskCache: LinkedCoverCache,
    private val coverCache: CoverCache,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private data class Key(val identity: CoverLinkIdentity, val mangaId: Long?)
    private data class CachedCover(val cover: MangaCover?, val lastModified: String?, val retryAt: Long = 0)

    private val cache = ConcurrentHashMap<Key, CachedCover>()
    private val permits = ConcurrentHashMap<Key, Semaphore>()

    suspend fun resolve(link: CoverLink, allowNetwork: Boolean, manga: Manga? = null): MangaCover? {
        val source = sourceManager.get(link.sourceId) as? HttpSource ?: return null
        val key = Key(link.identity, manga?.id)
        val lastModified = manga?.coverLastModified?.toString()
        return permits.computeIfAbsent(key) { Semaphore(1) }.withPermit {
            val cached = cache[key] ?: withContext(Dispatchers.IO) {
                diskCache.get(key.identity, key.mangaId)?.let {
                    CachedCover(cover(link, it.url), it.lastModified)
                }
            }?.also { cache[key] = it }
            if (cached != null && cached.lastModified == lastModified &&
                (cached.cover != null || cached.retryAt > now())
            ) {
                return@withPermit cached.cover
            }
            if (!allowNetwork) return@withPermit null

            val cover = try {
                withTimeoutOrNull(10_000) {
                    withContext(Dispatchers.IO) {
                        val selected = SManga.create().apply {
                            url = link.url
                            title = link.title
                        }
                        source.getMangaUpdate(selected, emptyList(), fetchDetails = true, fetchChapters = false)
                            .manga.thumbnail_url
                            ?.takeIf { it.isNotBlank() }
                            ?.let { url ->
                                if (manga != null) {
                                    coverCache.getCoverFile(cached?.cover?.url)?.delete()
                                    coverCache.getCoverFile(url)?.delete()
                                }
                                diskCache.set(key.identity, key.mangaId, LinkedCoverCache.Entry(url, lastModified))
                                cover(link, url)
                            }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            cache[key] = CachedCover(cover, lastModified, retryAt = now() + 60_000)
            cover
        }
    }

    private fun cover(link: CoverLink, url: String) = MangaCover(
        mangaId = -1,
        sourceId = link.sourceId,
        isMangaFavorite = false,
        url = url,
        lastModified = null,
    )
}
