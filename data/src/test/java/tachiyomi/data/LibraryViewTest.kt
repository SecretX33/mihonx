package tachiyomi.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

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

                driver.long("SELECT totalCount - readCount FROM libraryView WHERE _id = 1") shouldBe 1L
            }
        }
    }

    private suspend fun SqlDriver.executeSql(sql: String) {
        execute(identifier = null, sql = sql, parameters = 0).await()
    }

    private suspend fun SqlDriver.long(sql: String): Long {
        return executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                check(cursor.next().value)
                QueryResult.Value(cursor.getLong(0)!!)
            },
            parameters = 0,
        ).await()
    }
}
