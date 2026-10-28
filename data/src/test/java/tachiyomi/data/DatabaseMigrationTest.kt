package tachiyomi.data

import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tachiyomi.data.manga.MangaMapper
import tachiyomi.domain.manga.model.ScanlatorFillerPages

class DatabaseMigrationTest {

    @ParameterizedTest
    @ValueSource(longs = [12, 13, 14, 15])
    fun `MihonX custom data survives upstream migrations`(version: Long): Unit = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            driver.loadFixture("mihonx-v15.sql")
            driver.executeSql(
                """
                INSERT INTO mangas(_id, source, url, title, status, favorite, initialized, date_added,
                    custom_title, custom_author, custom_genre, scanlator_filler_pages, memo, viewer, chapter_flags, cover_last_modified)
                VALUES (2, 1, '/manga', 'Source title', 0, 1, 1, 1234, 'Custom title', 'Custom author',
                    'Action, Adventure', '{"Group":{"beginning":2,"end":1}}', '{"source":"value"}', 0, 0, 0)
                """.trimIndent(),
            )
            driver.executeSql(
                """
                INSERT INTO chapters(_id, manga_id, url, name, chapter_number, excluded, read, bookmark,
                    last_page_read, source_order, date_fetch, date_upload)
                VALUES (1, 2, '/chapter', 'Chapter', 1, 1, 0, 1, 3, 0, 100, 100)
                """.trimIndent(),
            )

            driver.executeSql(
                """
                INSERT INTO mangas(_id, source, url, title, status, favorite, initialized, viewer,
                    chapter_flags, cover_last_modified, date_added)
                VALUES (1, 1, '/manga', 'Source title', 0, 1, 1, 0, 0, 0, 1000)
                """.trimIndent(),
            )
            driver.executeSql(
                """
                INSERT INTO chapters(_id, manga_id, url, name, chapter_number, excluded, read, bookmark,
                    last_page_read, source_order, date_fetch, date_upload)
                VALUES (2, 1, '/chapter', 'Chapter', 1, 0, 0, 0, 0, 0, 100, 100)
                """.trimIndent(),
            )
            driver.executeSql(
                """
                INSERT INTO manga_sync(manga_id, sync_id, remote_id, title, last_chapter_read,
                    total_chapters, status, score, remote_url, start_date, finish_date)
                VALUES (2, 3, 123, 'Track', 0, 0, 0, 5, '/track', 0, 0)
                """.trimIndent(),
            )

            if (version < 15) driver.executeSql("ALTER TABLE mangas DROP COLUMN scanlator_filler_pages")
            if (version < 14) {
                driver.executeSql("ALTER TABLE mangas DROP COLUMN memo")
                driver.executeSql("ALTER TABLE chapters DROP COLUMN memo")
                driver.executeSql("DROP TABLE extension_store")
                driver.executeSql(
                    "CREATE TABLE extension_repos(base_url TEXT, name TEXT, short_name TEXT, signing_key_fingerprint TEXT, website TEXT)",
                )
                driver.executeSql(
                    "INSERT INTO extension_repos VALUES ('https://example.org', 'Example', 'EX', 'key', 'https://example.org')",
                )
            }
            if (version < 13) driver.executeSql("ALTER TABLE chapters DROP COLUMN excluded")
            driver.executeSql("PRAGMA user_version = $version")
            Database.Schema.migrate(driver, version, Database.Schema.version).await()

            val manga = testDatabase(driver).mangaQueries.getMangaById(1, MangaMapper::mapManga).awaitAsOne()
            manga.customTitle shouldBe "Custom title"
            manga.customAuthor shouldBe "Custom author"
            manga.customGenre shouldBe listOf("Action", "Adventure")
            manga.scanlatorFillerPages shouldBe
                if (version == 15L) mapOf("Group" to ScanlatorFillerPages(2, 1)) else emptyMap()
            if (version >= 14) manga.memo["source"].toString() shouldBe "\"value\""
            driver.long("SELECT excluded FROM chapter") shouldBe
                if (version >= 13) 1L else 0L
            driver.long("SELECT count(*) FROM manga") shouldBe 1L
            driver.long("SELECT count(*) FROM chapter") shouldBe 1L
            driver.long("SELECT CAST(score AS INTEGER) FROM manga_track") shouldBe 10L
            driver.long("PRAGMA foreign_key_check") shouldBe null
        }
    }

    private fun SqlDriver.loadFixture(name: String) {
        val sql = requireNotNull(javaClass.getResource("/$name")).readText()
        sql.split("-- statement-break").forEach { executeSql(it.trim()) }
    }
}

internal fun testDatabase(driver: SqlDriver): Database = Database(
    driver = driver,
    historyAdapter = History.Adapter(InstantColumnAdapter),
    mangaAdapter = Manga.Adapter(
        remote_genreAdapter = StringListColumnAdapter,
        custom_genreAdapter = StringListColumnAdapter,
        scanlator_filler_pagesAdapter = MemoColumnAdapter,
        remote_update_strategyAdapter = UpdateStrategyColumnAdapter,
        remote_memoAdapter = MemoColumnAdapter,
        user_favorite_atAdapter = InstantColumnAdapter,
        state_chapter_last_updateAdapter = InstantColumnAdapter,
        state_chapter_next_updateAdapter = InstantColumnAdapter,
        state_cover_last_modifiedAdapter = InstantColumnAdapter,
    ),
    chapterAdapter = Chapter.Adapter(InstantColumnAdapter, MemoColumnAdapter, IntColumnAdapter, InstantColumnAdapter),
    manga_trackAdapter = Manga_track.Adapter(InstantColumnAdapter, InstantColumnAdapter),
)

internal fun SqlDriver.executeSql(sql: String) {
    execute(null, sql, 0).value
}

internal fun SqlDriver.long(sql: String): Long? = executeQuery(null, sql, { cursor ->
    QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
}, 0).value
