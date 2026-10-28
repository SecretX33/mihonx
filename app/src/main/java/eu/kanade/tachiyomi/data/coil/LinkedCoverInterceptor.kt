package eu.kanade.tachiyomi.data.coil

import coil3.Extras
import coil3.getOrDefault
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.cache.CoverLinkStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover

@Inject
class LinkedCoverInterceptor(
    private val store: CoverLinkStore,
    private val resolver: LinkedCoverResolver,
    private val getManga: GetManga,
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        if (!chain.request.extras.getOrDefault(USE_LINKED_COVER) || store.isEmpty()) return chain.proceed()
        val manga = when (val data = chain.request.data) {
            is Manga -> data
            is MangaCover -> withContext(Dispatchers.IO) { getManga.await(data.mangaId) }
            else -> null
        } ?: return chain.proceed()
        val link = store.get(manga) ?: return chain.proceed()

        val linkedResult = try {
            resolver.resolve(link, chain.request.networkCachePolicy.readEnabled)?.let { resolved ->
                val cover = resolved.copy(lastModified = manga.coverLastModified)
                val request = chain.request.newBuilder()
                    .data(cover)
                    .memoryCacheKey("linked-cover:${link.sourceId}:${link.url}:${cover.url}:${cover.lastModified}")
                    .apply { extras[MangaCoverFetcher.USE_CUSTOM_COVER_KEY] = false }
                    .build()
                chain.withRequest(request).proceed() as? SuccessResult
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        store.recordUsage(manga, link, linkedResult != null)
        return linkedResult?.copy(request = chain.request) ?: chain.proceed()
    }

    companion object {
        val USE_LINKED_COVER = Extras.Key(true)
    }
}
