package tachiyomi.data

import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.manga.MangaMapper

class LibraryViewTest {

    @Test
    fun `unread count excludes hidden chapters and ignored scanlators`() {
        runBlocking {
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
                Database.Schema.create(driver).await()
                driver.executeSql(
                    """
                    INSERT INTO mangas(
                        _id, source, url, title, status, favorite, initialized, viewer,
                        chapter_flags, cover_last_modified, date_added
                    ) VALUES (1, 1, '/manga', 'Manga', 0, 1, 1, 0, 0, 0, 0)
                    """.trimIndent(),
                )
                driver.executeSql(
                    """
                    INSERT INTO chapters(
                        _id, manga_id, url, name, scanlator, read, bookmark,
                        last_page_read, chapter_number, source_order, date_fetch, date_upload, excluded
                    ) VALUES
                        (1, 1, '/1', 'Chapter 1', 'Visible', 0, 0, 0, 1, 0, 0, 0, 0),
                        (2, 1, '/2', 'Chapter 2', 'Visible', 0, 0, 0, 2, 1, 0, 0, 1),
                        (3, 1, '/3', 'Chapter 3', 'Ignored', 0, 0, 0, 3, 2, 0, 0, 0),
                        (4, 1, '/4', 'Chapter 4', 'Visible', 1, 0, 0, 4, 3, 0, 0, 0)
                    """.trimIndent(),
                )
                driver.executeSql("INSERT INTO excluded_scanlators(manga_id, scanlator) VALUES (1, 'Ignored')")

                val database = Database(
                    driver = driver,
                    chaptersAdapter = Chapters.Adapter(MemoColumnAdapter),
                    historyAdapter = History.Adapter(DateColumnAdapter),
                    mangasAdapter = Mangas.Adapter(
                        genreAdapter = StringListColumnAdapter,
                        update_strategyAdapter = UpdateStrategyColumnAdapter,
                        custom_genreAdapter = StringListColumnAdapter,
                        memoAdapter = MemoColumnAdapter,
                    ),
                )

                database.libraryViewQueries
                    .library(MangaMapper::mapLibraryManga)
                    .awaitAsOne()
                    .unreadCount shouldBe 1L
            }
        }
    }

    private suspend fun SqlDriver.executeSql(sql: String) {
        execute(identifier = null, sql = sql, parameters = 0).await()
    }
}
