package eu.kanade.tachiyomi.data.coil

import coil3.disk.DiskCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.cache.CoverLink
import eu.kanade.tachiyomi.data.cache.LinkedCoverCache
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import java.io.File
import java.io.IOException
import kotlin.time.Instant

class LinkedCoverResolverTest {

    private val source = mockk<HttpSource>()
    private val sourceManager = mockk<SourceManager> {
        coEvery { get(20) } returns source
    }
    private val coverCache = mockk<CoverCache>(relaxed = true)
    private val link = CoverLink(20, "/selected", "Selected", "Cover source")
    private val manga = Manga.create().copy(
        id = 1,
        favoriteAt = Instant.fromEpochMilliseconds(1),
        coverLastModified = Instant.fromEpochMilliseconds(100),
    )
    private var time = 0L

    @TempDir
    lateinit var directory: File
    private val diskCache by lazy { DiskCache.Builder().directory(File(directory, "coil").toOkioPath()).build() }
    private val resolver by lazy { newResolver() }

    private fun newResolver() = LinkedCoverResolver(sourceManager, LinkedCoverCache { diskCache }, coverCache) { time }

    @AfterEach
    fun tearDown() {
        diskCache.shutdown()
    }

    private fun details(url: String? = "https://covers.example/cover.jpg") = SMangaUpdate(
        SManga.create().apply { thumbnail_url = url },
        emptyList(),
    )

    @Test
    fun `requests only details of the explicitly selected entry`() = runBlocking<Unit> {
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } returns details()

        val result = resolver.resolve(link, allowNetwork = true)

        result?.sourceId shouldBe 20
        result?.url shouldBe "https://covers.example/cover.jpg"
        result?.isMangaFavorite shouldBe false
        coVerify(exactly = 1) {
            source.getMangaUpdate(
                match { it.url == "/selected" },
                emptyList(),
                fetchDetails = true,
                fetchChapters = false,
            )
        }
    }

    @Test
    fun `missing extension produces no alternate cover`() = runBlocking<Unit> {
        coEvery { sourceManager.get(20) } returns null

        resolver.resolve(link, allowNetwork = true) shouldBe null
    }

    @Test
    fun `failed cover retries later without retrying on every scroll`() = runBlocking<Unit> {
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } throws IOException("Unavailable")

        resolver.resolve(link, allowNetwork = true) shouldBe null
        resolver.resolve(link, allowNetwork = true) shouldBe null
        coVerify(exactly = 1) { source.getMangaUpdate(any(), any(), any(), any()) }

        time = 61_000
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } returns details(null)
        resolver.resolve(link, allowNetwork = true) shouldBe null
        coVerify(exactly = 2) { source.getMangaUpdate(any(), any(), any(), any()) }
    }

    @Test
    fun `scrolling and elapsed time reuse details without an hourly refresh`() = runBlocking<Unit> {
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } returns details()
        val result = resolver.resolve(link, allowNetwork = true, manga)
        time = 7 * 24 * 3_600_000L

        repeat(20) { resolver.resolve(link, allowNetwork = true, manga) shouldBe result }
        resolver.resolve(link, allowNetwork = false, manga) shouldBe result
        coVerify(exactly = 1) { source.getMangaUpdate(any(), any(), any(), any()) }
    }

    @Test
    fun `restart reuses resolved URLs from disk even offline`() = runBlocking<Unit> {
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } returns details()
        val result = resolver.resolve(link, allowNetwork = true, manga)
        time = 7 * 24 * 3_600_000L

        newResolver().resolve(link, allowNetwork = false, manga) shouldBe result
        newResolver().resolve(link, allowNetwork = true, manga) shouldBe result
        coVerify(exactly = 1) { source.getMangaUpdate(any(), any(), any(), any()) }
    }

    @Test
    fun `clearing the shared Coil cache evicts resolved details`() = runBlocking<Unit> {
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } returns details()
        resolver.resolve(link, allowNetwork = true, manga)

        diskCache.clear()

        resolver.resolve(link, allowNetwork = false, manga) shouldBe null
        resolver.resolve(link, allowNetwork = true, manga)?.url shouldBe "https://covers.example/cover.jpg"
        coVerify(exactly = 2) { source.getMangaUpdate(any(), any(), any(), any()) }
    }

    @Test
    fun `normal cover refresh resolves linked details once and invalidates unchanged image URL`() = runBlocking<Unit> {
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } returns details()
        val image = File(directory, "cover.jpg")
        every { coverCache.getCoverFile("https://covers.example/cover.jpg") } returns image
        resolver.resolve(link, allowNetwork = true, manga)
        image.writeText("old cover bytes")
        val refreshed = manga.copy(coverLastModified = Instant.fromEpochMilliseconds(200))

        newResolver().resolve(link, allowNetwork = true, refreshed)?.url shouldBe "https://covers.example/cover.jpg"
        image.exists() shouldBe false
        image.writeText("refreshed cover bytes")
        newResolver().resolve(link, allowNetwork = true, refreshed)?.url shouldBe "https://covers.example/cover.jpg"
        image.readText() shouldBe "refreshed cover bytes"
        coVerify(exactly = 2) { source.getMangaUpdate(any(), any(), any(), any()) }
    }

    @Test
    fun `normal refresh discovers a changed alternative URL`() = runBlocking<Unit> {
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } returnsMany listOf(
            details("https://covers.example/old.jpg"),
            details("https://covers.example/new.jpg"),
        )
        resolver.resolve(link, allowNetwork = true, manga)?.url shouldBe "https://covers.example/old.jpg"
        val refreshed = manga.copy(coverLastModified = Instant.fromEpochMilliseconds(200))

        resolver.resolve(link, allowNetwork = true, refreshed)?.url shouldBe "https://covers.example/new.jpg"
        newResolver().resolve(link, allowNetwork = false, refreshed)?.url shouldBe "https://covers.example/new.jpg"
    }

    @Test
    fun `titles sharing a linked entry keep independent cover refresh state`() = runBlocking<Unit> {
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } returns details()
        val other = manga.copy(id = 2, coverLastModified = Instant.fromEpochMilliseconds(300))
        resolver.resolve(link, allowNetwork = true, manga)
        resolver.resolve(link, allowNetwork = true, other)

        repeat(5) {
            resolver.resolve(link, allowNetwork = true, manga)
            resolver.resolve(link, allowNetwork = true, other)
        }
        coVerify(exactly = 2) { source.getMangaUpdate(any(), any(), any(), any()) }
    }

    @Test
    fun `unavailable cover after normal refresh falls back without reusing outdated details`() = runBlocking<Unit> {
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } returns details()
        resolver.resolve(link, allowNetwork = true, manga)
        val refreshed = manga.copy(coverLastModified = Instant.fromEpochMilliseconds(200))
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } throws IOException("Unavailable")

        resolver.resolve(link, allowNetwork = true, refreshed) shouldBe null
        newResolver().resolve(link, allowNetwork = false, refreshed) shouldBe null
    }

    @Test
    fun `network disabled requests do not fetch entry details`() = runBlocking<Unit> {
        resolver.resolve(link, allowNetwork = false) shouldBe null

        coVerify(exactly = 0) { source.getMangaUpdate(any(), any(), any(), any()) }
    }

    @Test
    fun `concurrent requests share one details fetch`() = runBlocking<Unit> {
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } coAnswers {
            delay(25)
            details()
        }

        val covers = (1..20).map {
            async(Dispatchers.Default) { resolver.resolve(link, allowNetwork = true, manga) }
        }.awaitAll()

        covers.all { it?.url == "https://covers.example/cover.jpg" } shouldBe true
        coVerify(exactly = 1) { source.getMangaUpdate(any(), any(), any(), any()) }
    }

    @Test
    fun `cancellation propagates`() = runBlocking<Unit> {
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } throws CancellationException()

        assertThrows<CancellationException> { resolver.resolve(link, allowNetwork = true) }
    }
}
