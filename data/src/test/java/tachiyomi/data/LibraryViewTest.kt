package tachiyomi.data

import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.manga.MangaMapper

class LibraryViewTest {

    @Test
    fun `unread count excludes hidden chapters and ignored scanlators`(): Unit = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            Database.Schema.create(driver).await()
            driver.executeSql(
                """
                INSERT INTO manga(id, source_id, remote_url, remote_title, remote_status, user_favorite_at,
                    state_initialized, user_reader_flags, user_chapter_flags, remote_update_strategy, remote_memo,
                    user_notes, state_chapter_fetch_interval)
                VALUES (1, 1, '/manga', 'Manga', 0, 1, 1, 0, 0, 0, '{}', '', 0)
                """.trimIndent(),
            )
            driver.executeSql(
                """
                INSERT INTO chapter(id, manga_id, remote_url, remote_name, remote_scanlator, user_read,
                    user_bookmark, user_last_page_read, remote_chapter_number, remote_order, state_date_fetch,
                    remote_memo, excluded)
                VALUES
                    (1, 1, '/1', 'Chapter 1', 'Visible', 0, 0, 0, 1, 0, 100, '{}', 0),
                    (2, 1, '/2', 'Chapter 2', 'Visible', 0, 0, 0, 2, 1, 100, '{}', 1),
                    (3, 1, '/3', 'Chapter 3', 'Ignored', 0, 0, 0, 3, 2, 100, '{}', 0),
                    (4, 1, '/4', 'Chapter 4', 'Visible', 1, 0, 0, 4, 3, 100, '{}', 0)
                """.trimIndent(),
            )
            driver.executeSql("INSERT INTO excluded_scanlator(manga_id, scanlator) VALUES (1, 'Ignored')")

            testDatabase(driver).libraryViewQueries.library(MangaMapper::mapLibraryManga)
                .awaitAsOne().unreadCount shouldBe 1L
        }
    }
}
