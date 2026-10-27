package eu.kanade.tachiyomi.data.coil

import eu.kanade.tachiyomi.data.cache.CoverLink
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.domain.source.service.SourceManager
import java.io.IOException

class LinkedCoverResolverTest {

    private val source = mockk<HttpSource>()
    private val sourceManager = mockk<SourceManager> {
        every { get(20) } returns source
    }
    private val link = CoverLink(20, "/selected", "Selected", "Cover source")
    private var time = 0L
    private val resolver = LinkedCoverResolver(sourceManager) { time }

    @Test
    fun `requests only details of the explicitly selected entry`() = runBlocking<Unit> {
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } answers {
            SMangaUpdate(
                SManga.create().apply { thumbnail_url = "https://covers.example/high-quality.jpg" },
                emptyList(),
            )
        }

        val result = resolver.resolve(link, allowNetwork = true)

        result?.sourceId shouldBe 20
        result?.url shouldBe "https://covers.example/high-quality.jpg"
        result?.isMangaFavorite shouldBe false
        coVerify(exactly = 1) {
            source.getMangaUpdate(
                match {
                    it.url == "/selected"
                },
                emptyList(),
                fetchDetails = true,
                fetchChapters = false,
            )
        }
    }

    @Test
    fun `missing extension produces no alternate cover`() = runBlocking<Unit> {
        every { sourceManager.get(20) } returns null

        resolver.resolve(link, allowNetwork = true) shouldBe null
    }

    @Test
    fun `missing or failed cover produces no alternate result and retries later`() = runBlocking<Unit> {
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } throws IOException("Unavailable")

        resolver.resolve(link, allowNetwork = true) shouldBe null
        resolver.resolve(link, allowNetwork = true) shouldBe null
        coVerify(exactly = 1) { source.getMangaUpdate(any(), any(), any(), any()) }

        time = 61_000
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } returns SMangaUpdate(
            SManga.create().apply { thumbnail_url = null },
            emptyList(),
        )
        resolver.resolve(link, allowNetwork = true) shouldBe null
        coVerify(exactly = 2) { source.getMangaUpdate(any(), any(), any(), any()) }
    }

    @Test
    fun `cover details are cached without searching again`() = runBlocking<Unit> {
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } returns SMangaUpdate(
            SManga.create().apply { thumbnail_url = "https://covers.example/cover.jpg" },
            emptyList(),
        )
        val result = resolver.resolve(link, allowNetwork = true)

        resolver.resolve(link, allowNetwork = false) shouldBe result
        resolver.resolve(link, allowNetwork = true) shouldBe result
        coVerify(exactly = 1) { source.getMangaUpdate(any(), any(), any(), any()) }
    }

    @Test
    fun `network disabled requests do not fetch entry details`() = runBlocking<Unit> {
        resolver.resolve(link, allowNetwork = false) shouldBe null

        coVerify(exactly = 0) { source.getMangaUpdate(any(), any(), any(), any()) }
    }

    @Test
    fun `expired details do not retain an unavailable alternate cover`() = runBlocking<Unit> {
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } returns SMangaUpdate(
            SManga.create().apply { thumbnail_url = "https://covers.example/cover.jpg" },
            emptyList(),
        )
        resolver.resolve(link, allowNetwork = true)?.url shouldBe "https://covers.example/cover.jpg"
        time = 3_600_001
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } throws IOException("Unavailable")

        resolver.resolve(link, allowNetwork = true) shouldBe null
    }

    @Test
    fun `concurrent requests for the same entry share one details fetch`() = runBlocking<Unit> {
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } coAnswers {
            delay(25)
            SMangaUpdate(
                SManga.create().apply { thumbnail_url = "https://covers.example/cover.jpg" },
                emptyList(),
            )
        }

        val covers = (1..20).map {
            async(Dispatchers.Default) { resolver.resolve(link, allowNetwork = true) }
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
