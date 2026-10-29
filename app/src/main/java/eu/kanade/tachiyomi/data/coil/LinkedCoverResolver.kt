package eu.kanade.tachiyomi.data.coil

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.cache.CoverLink
import eu.kanade.tachiyomi.data.cache.LinkedCoverCache
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Clock

@Inject
@SingleIn(AppScope::class)
class LinkedCoverResolver(
    private val sourceManager: SourceManager,
    private val diskCache: LinkedCoverCache,
    private val coverCache: CoverCache,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private val concurrentRequests = DeDupeConcurrentRequests<MangaCover?>()

    suspend fun resolve(link: CoverLink, allowNetwork: Boolean, manga: Manga? = null): MangaCover? {
        val source = sourceManager.get(link.sourceId) as? HttpSource ?: return null
        val key = "linked-cover-details:${link.sourceId}:${manga?.id}:${link.url}"
        val lastModified = manga?.coverLastModified?.toString()
        return withContext(Dispatchers.IO) {
            concurrentRequests.apply(key) {
                val cached = diskCache.get(key)
                if (cached != null && cached.lastModified == lastModified &&
                    (cached.url != null || cached.retryAt > now())
                ) {
                    return@apply cached.url?.let { cover(link, it) }
                }
                if (!allowNetwork) return@apply null

                val url = try {
                    withTimeoutOrNull(10_000) {
                        val selected = SManga.create().apply {
                            url = link.url
                            title = link.title
                        }
                        source.getMangaUpdate(selected, emptyList(), fetchDetails = true, fetchChapters = false)
                            .manga.thumbnail_url
                            ?.takeIf { it.isNotBlank() }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (url != null && manga != null) {
                    coverCache.getCoverFile(cached?.url)?.delete()
                    coverCache.getCoverFile(url)?.delete()
                }
                diskCache.set(key, LinkedCoverCache.Entry(url, lastModified, retryAt = now() + 60_000))
                url?.let { cover(link, it) }
            }
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
