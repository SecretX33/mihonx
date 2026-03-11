package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.repository.MangaRepository

class SetCustomMangaInfo(
    private val mangaRepository: MangaRepository,
) {
    suspend fun await(
        mangaId: Long,
        customTitle: String? = null,
        customAuthor: String? = null,
        customArtist: String? = null,
        customDescription: String? = null,
        customGenre: List<String>? = null,
        customStatus: Long? = null,
    ): Boolean {
        return mangaRepository.updateCustomInfo(
            mangaId = mangaId,
            customTitle = customTitle,
            customAuthor = customAuthor,
            customArtist = customArtist,
            customDescription = customDescription,
            customGenre = customGenre,
            customStatus = customStatus,
        )
    }
}
