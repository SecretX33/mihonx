package eu.kanade.tachiyomi.util

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.cache.CoverLinkStore
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.isLocal
import java.io.InputStream
import kotlin.time.Clock

fun Manga.removeCovers(
    coverCache: CoverCache,
    coverLinkStore: CoverLinkStore,
): Manga {
    val hadCoverLink = coverLinkStore.get(this) != null
    if (hadCoverLink) coverLinkStore.set(this, null)
    val deletedCover = !isLocal() && coverCache.deleteFromCache(this, true) > 0
    return if (hadCoverLink || deletedCover) {
        copy(coverLastModified = Clock.System.now())
    } else {
        this
    }
}

suspend fun Manga.editCover(
    coverManager: LocalCoverManager,
    stream: InputStream,
    updateManga: UpdateManga,
    coverCache: CoverCache,
) {
    if (isLocal()) {
        coverManager.update(toSManga(), stream)
        updateManga.awaitUpdateCoverLastModified(id)
    } else if (favorite) {
        coverCache.setCustomCoverToCache(this, stream)
        updateManga.awaitUpdateCoverLastModified(id)
    }
}
