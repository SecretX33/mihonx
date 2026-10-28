package tachiyomi.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterRemoteUpdate
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaRemoteUpdate
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.ScanlatorFillerPages

class MihonxMetadataTest {

    @Test
    fun `source refresh preserves custom info and filler rules`(): Unit = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            Database.Schema.create(driver).await()
            val repository = MangaRepositoryImpl(testDatabase(driver))
            val manga = repository.insertNetworkManga(listOf(Manga.create().copy(source = 1, url = "/manga"))).single()
            val rules = mapOf("Group" to ScanlatorFillerPages(2, 1))
            repository.update(
                MangaUpdate(manga.id) {
                    customInfo = CustomMangaInfo.Set("Custom", null, null, null, listOf("Action"), null)
                    scanlatorFillerPages = rules
                },
            ) shouldBe true

            repository.updateRemote(
                MangaRemoteUpdate(
                    id = manga.id,
                    title = "Updated title",
                    author = null,
                    artist = null,
                    description = null,
                    genre = null,
                    status = 0,
                    thumbnailUrl = null,
                    updateStrategy = manga.updateStrategy,
                    memo = JsonObject(mapOf("source" to JsonPrimitive("new"))),
                    initialized = true,
                    coverLastModified = null,
                ),
            ) shouldBe true

            val updated = repository.getMangaById(manga.id)
            updated.title shouldBe "Updated title"
            updated.effectiveTitle shouldBe "Custom"
            updated.customGenre shouldBe listOf("Action")
            updated.scanlatorFillerPages shouldBe rules
            updated.memo["source"] shouldBe JsonPrimitive("new")

            repository.insertNetworkManga(listOf(updated.copy(title = "Browse title", memo = JsonObject(emptyMap()))))
                .single().effectiveTitle shouldBe "Custom"
            repository.update(MangaUpdate(manga.id) { customInfo = CustomMangaInfo.ClearAll })
            repository.getMangaById(manga.id).let {
                it.customTitle shouldBe null
                it.scanlatorFillerPages shouldBe rules
            }
        }
    }

    @Test
    fun `hiding chapters survives a source refresh and supports unhiding`(): Unit = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            Database.Schema.create(driver).await()
            val database = testDatabase(driver)
            val manga = MangaRepositoryImpl(database).insertNetworkManga(
                listOf(Manga.create().copy(source = 1, url = "/manga")),
            ).single()
            val repository = ChapterRepositoryImpl(database)
            val chapter = repository.updateFromRemote(
                emptyList(),
                listOf(Chapter.create().copy(mangaId = manga.id, url = "/chapter")),
                emptyList(),
            ).single()
            repository.update(ChapterUpdate(chapter.id) { excluded = true })
            repository.updateFromRemote(
                emptyList(),
                emptyList(),
                listOf(
                    ChapterRemoteUpdate(
                        id = chapter.id,
                        name = "Updated chapter",
                        scanlator = null,
                        chapterNumber = 1.0,
                        sourceOrder = 0,
                        dateUpload = null,
                        memo = JsonObject(mapOf("source" to JsonPrimitive("new"))),
                    ),
                ),
            )
            repository.getChapterById(chapter.id)!!.let {
                it.excluded shouldBe true
                it.memo["source"] shouldBe JsonPrimitive("new")
            }
            repository.update(ChapterUpdate(chapter.id) { excluded = false })
            repository.getChapterById(chapter.id)!!.excluded shouldBe false
        }
    }
}
