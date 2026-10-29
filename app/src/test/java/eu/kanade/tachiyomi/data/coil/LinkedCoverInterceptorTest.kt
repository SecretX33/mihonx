package eu.kanade.tachiyomi.data.coil

import coil3.Extras
import coil3.getOrDefault
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import eu.kanade.tachiyomi.data.cache.CoverLink
import eu.kanade.tachiyomi.data.cache.CoverLinkStore
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import java.io.IOException
import kotlin.time.Instant

class LinkedCoverInterceptorTest {

    private val manga = Manga.create().copy(id = 1, source = 10, url = "/original")
    private val link = CoverLink(20, "/selected", "Selected", "Cover source")
    private val cover = MangaCover(-1, 20, false, "https://covers.example/cover.jpg", null)
    private val store = mockk<CoverLinkStore>(relaxed = true) {
        every { isEmpty() } returns false
        every { get(manga) } returns link
    }
    private val resolver = mockk<LinkedCoverResolver> {
        coEvery { resolve(link, true, any()) } returns cover
    }
    private val getManga = mockk<GetManga>()
    private val extras = Extras.Builder()
    private val linkedRequest = mockk<ImageRequest>()
    private val requestBuilder = mockk<ImageRequest.Builder> {
        every { data(any()) } returns this
        every { memoryCacheKey(any<String>()) } returns this
        every { this@mockk.extras } returns this@LinkedCoverInterceptorTest.extras
        every { build() } returns linkedRequest
    }
    private val request = mockk<ImageRequest> {
        every { data } returns manga
        every { extras } returns Extras.Builder().build()
        every { networkCachePolicy } returns CachePolicy.ENABLED
        every { newBuilder(any()) } returns requestBuilder
    }
    private val fallback = SuccessResult(mockk(), request)
    private val linkedSuccess = SuccessResult(mockk(), linkedRequest)
    private val linkedChain = mockk<Interceptor.Chain> {
        coEvery { proceed() } returns linkedSuccess
    }
    private val chain = mockk<Interceptor.Chain> {
        every { this@mockk.request } returns this@LinkedCoverInterceptorTest.request
        every { withRequest(linkedRequest) } returns linkedChain
        coEvery { proceed() } returns fallback
    }
    private val interceptor = LinkedCoverInterceptor(store, resolver, getManga)

    @Test
    fun `successful alternate cover uses selected source and bypasses custom cover`() = runTest {
        val result = interceptor.intercept(chain) as SuccessResult

        result.image shouldBe linkedSuccess.image
        result.request shouldBe request
        verify { requestBuilder.data(cover) }
        extras.build().getOrDefault(MangaCoverFetcher.USE_CUSTOM_COVER_KEY) shouldBe false
        verify { store.recordUsage(manga, link, true) }
        coVerify(exactly = 0) { chain.proceed() }
    }

    @Test
    fun `missing link preserves existing cover loading`() = runTest {
        every { store.get(manga) } returns null

        interceptor.intercept(chain) shouldBe fallback

        coVerify(exactly = 0) { resolver.resolve(any(), any(), any()) }
        coVerify(exactly = 1) { chain.proceed() }
    }

    @Test
    fun `library alternate covers use the persistent library cover cache`() = runTest {
        val libraryManga = manga.copy(favoriteAt = Instant.fromEpochMilliseconds(1))
        every { request.data } returns libraryManga
        every { store.get(libraryManga) } returns link

        interceptor.intercept(chain)

        verify { requestBuilder.data(cover.copy(isMangaFavorite = true)) }
        extras.build().getOrDefault(MangaCoverFetcher.USE_CUSTOM_COVER_KEY) shouldBe false
    }

    @Test
    fun `linked details use the original entry cover refresh state`() = runTest {
        interceptor.intercept(chain)

        coVerify(exactly = 1) { resolver.resolve(link, true, manga) }
    }

    @Test
    fun `offline image failure does not refresh details`() = runTest {
        every { request.networkCachePolicy } returns CachePolicy.DISABLED
        coEvery { resolver.resolve(link, false, manga) } returns cover
        coEvery { linkedChain.proceed() } returns ErrorResult(null, linkedRequest, IOException("Not cached"))

        interceptor.intercept(chain) shouldBe fallback

        coVerify(exactly = 1) { resolver.resolve(link, false, manga) }
    }

    @Test
    fun `unavailable alternate preserves existing cover loading and keeps link`() = runTest {
        coEvery { resolver.resolve(link, true, manga) } returns null

        interceptor.intercept(chain) shouldBe fallback

        verify { store.recordUsage(manga, link, false) }
        verify(exactly = 0) { store.set(any(), any()) }
    }

    @Test
    fun `invalid image falls back after decoding fails`() = runTest {
        coEvery { linkedChain.proceed() } throws IOException("Invalid image")

        interceptor.intercept(chain) shouldBe fallback

        verify { store.recordUsage(manga, link, false) }
        coVerify(exactly = 1) { chain.proceed() }
    }

    @Test
    fun `alternate error result falls back to existing cover`() = runTest {
        coEvery { linkedChain.proceed() } returns ErrorResult(null, linkedRequest, IOException("Not found"))

        interceptor.intercept(chain) shouldBe fallback
    }

    @Test
    fun `MangaCover models resolve the original entry by its database ID`() = runTest {
        every { request.data } returns MangaCover(1, 10, true, "original.jpg", null)
        coEvery { getManga.await(1) } returns manga

        (interceptor.intercept(chain) as SuccessResult).image shouldBe linkedSuccess.image

        coVerify { getManga.await(1) }
    }

    @Test
    fun `source previews bypass linked covers`() = runTest {
        every { request.extras } returns Extras.Builder()
            .set(LinkedCoverInterceptor.USE_LINKED_COVER, false)
            .build()

        interceptor.intercept(chain) shouldBe fallback

        coVerify(exactly = 0) { resolver.resolve(any(), any(), any()) }
    }

    @Test
    fun `cancellation does not trigger fallback`() = runTest {
        coEvery { linkedChain.proceed() } throws CancellationException()

        assertThrows<CancellationException> { interceptor.intercept(chain) }

        coVerify(exactly = 0) { chain.proceed() }
    }
}
