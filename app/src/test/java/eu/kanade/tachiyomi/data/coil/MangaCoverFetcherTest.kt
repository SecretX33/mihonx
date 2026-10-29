package eu.kanade.tachiyomi.data.coil

import coil3.Extras
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.request.CachePolicy
import coil3.request.Options
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MangaCoverFetcherTest {

    @TempDir
    lateinit var directory: File

    @Test
    fun `linked library covers persist on disk and are reused without a network request`() = runBlocking<Unit> {
        var downloads = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            downloads++
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("cover image bytes".toResponseBody())
                .build()
        }.build()
        val options = mockk<Options> {
            every { extras } returns Extras.Builder().set(MangaCoverFetcher.USE_CUSTOM_COVER_KEY, false).build()
            every { diskCachePolicy } returns CachePolicy.ENABLED
            every { networkCachePolicy } returns CachePolicy.ENABLED
        }
        val libraryFile = File(directory, "library/cover")
        fun fetcher(imageLoader: ImageLoader) = MangaCoverFetcher(
            url = "https://covers.example/linked.jpg",
            isLibraryManga = true,
            options = options,
            coverFileLazy = lazy { libraryFile },
            customCoverFileLazy = lazy { error("Linked covers must bypass custom covers") },
            diskCacheKeyLazy = lazy { "linked-cover-key" },
            getSource = { null },
            callFactoryLazy = lazy { client },
            imageLoader = imageLoader,
        )
        fun loader(cache: DiskCache) = mockk<ImageLoader> { every { diskCache } returns cache }
        fun cache() = DiskCache.Builder().directory(File(directory, "coil").toOkioPath()).build()

        val firstCache = cache()
        val first = fetcher(loader(firstCache)).fetch() as SourceFetchResult
        first.source.use { it.source().readUtf8() shouldBe "cover image bytes" }
        libraryFile.readText() shouldBe "cover image bytes"
        firstCache.shutdown()

        val reopenedCache = cache()
        val second = fetcher(loader(reopenedCache)).fetch() as SourceFetchResult
        second.dataSource shouldBe DataSource.DISK
        second.source.use { it.source().readUtf8() shouldBe "cover image bytes" }
        downloads shouldBe 1
        reopenedCache.shutdown()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
