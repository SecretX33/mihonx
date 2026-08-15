package tachiyomi.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DatabaseMigrationTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `fresh database contains both lineages`() = runBlocking {
        withDriver { driver ->
            Database.Schema.create(driver).await()

            driver.columns("mangas").shouldContainAll(
                "custom_title",
                "custom_author",
                "custom_artist",
                "custom_description",
                "custom_genre",
                "custom_status",
                "memo",
            )
            driver.columns("chapters").shouldContainAll("excluded", "memo")
            driver.columns("extension_store").shouldContainAll("extension_list_url")
        }
    }

    @Test
    fun `version 11 database receives both lineages`() = runBlocking {
        withDriver { driver ->
            driver.createVersion11Fixture()

            Database.Schema.migrate(driver, oldVersion = 11, newVersion = 14).await()

            driver.columns("mangas").shouldContainAll(
                "custom_title",
                "custom_author",
                "custom_artist",
                "custom_description",
                "custom_genre",
                "custom_status",
                "memo",
            )
            driver.columns("chapters").shouldContainAll("excluded", "memo")
            driver.string("SELECT index_url FROM extension_store") shouldBe "https://example.org/repo.json"
            driver.string("SELECT extension_list_url FROM extension_store") shouldBe null
            driver.tableExists("extension_repos") shouldBe false
        }
    }

    @Test
    fun `version 13 MihonX data survives bridge`() = runBlocking {
        withDriver { driver ->
            driver.createVersion13Fixture()

            Database.Schema.migrate(driver, oldVersion = 13, newVersion = 14).await()

            driver.string("SELECT custom_title FROM mangas") shouldBe "Custom title"
            driver.string("SELECT custom_author FROM mangas") shouldBe "Custom author"
            driver.long("SELECT excluded FROM chapters") shouldBe 1L
            driver.string("SELECT index_url FROM extension_store") shouldBe "https://example.org/repo.json"
            driver.string("SELECT name FROM extension_store") shouldBe "Example"
            driver.string("SELECT badge_label FROM extension_store") shouldBe "EX"
            driver.string("SELECT extension_list_url FROM extension_store") shouldBe null
            driver.columns("mangas").shouldContainAll("memo")
            driver.columns("chapters").shouldContainAll("memo")
            driver.tableExists("extension_repos") shouldBe false
        }
    }

    private suspend fun withDriver(block: suspend (SqlDriver) -> Unit) {
        val databaseFile = Files.createTempFile(tempDir, "migration-", ".db")
        JdbcSqliteDriver("jdbc:sqlite:$databaseFile").use { driver ->
            block(driver)
        }
    }

    private suspend fun SqlDriver.createVersion11Fixture() {
        executeSql("CREATE TABLE mangas(_id INTEGER NOT NULL PRIMARY KEY)")
        executeSql("CREATE TABLE chapters(_id INTEGER NOT NULL PRIMARY KEY)")
        createExtensionRepos()
    }

    private suspend fun SqlDriver.createVersion13Fixture() {
        executeSql(
            """
            CREATE TABLE mangas(
                _id INTEGER NOT NULL PRIMARY KEY,
                custom_title TEXT,
                custom_author TEXT,
                custom_artist TEXT,
                custom_description TEXT,
                custom_genre TEXT,
                custom_status INTEGER
            )
            """.trimIndent(),
        )
        executeSql(
            """
            CREATE TABLE chapters(
                _id INTEGER NOT NULL PRIMARY KEY,
                excluded INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        createExtensionRepos()
        executeSql(
            """
            INSERT INTO mangas(_id, custom_title, custom_author)
            VALUES (1, 'Custom title', 'Custom author')
            """.trimIndent(),
        )
        executeSql("INSERT INTO chapters(_id, excluded) VALUES (1, 1)")
    }

    private suspend fun SqlDriver.createExtensionRepos() {
        executeSql(
            """
            CREATE TABLE extension_repos(
                base_url TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                short_name TEXT,
                website TEXT NOT NULL,
                signing_key_fingerprint TEXT NOT NULL
            )
            """.trimIndent(),
        )
        executeSql(
            """
            INSERT INTO extension_repos(base_url, name, short_name, website, signing_key_fingerprint)
            VALUES ('https://example.org', 'Example', 'EX', 'https://example.org', 'fingerprint')
            """.trimIndent(),
        )
    }

    private suspend fun SqlDriver.executeSql(sql: String) {
        execute(identifier = null, sql = sql, parameters = 0).await()
    }

    private suspend fun SqlDriver.columns(table: String): Set<String> {
        return executeQuery(
            identifier = null,
            sql = "PRAGMA table_info($table)",
            mapper = { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.next().value) {
                    columns += cursor.getString(1)!!
                }
                QueryResult.Value(columns)
            },
            parameters = 0,
        ).await()
    }

    private suspend fun SqlDriver.tableExists(table: String): Boolean {
        return long("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '$table'") == 1L
    }

    private suspend fun SqlDriver.string(sql: String): String? {
        return executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                check(cursor.next().value)
                QueryResult.Value(cursor.getString(0))
            },
            parameters = 0,
        ).await()
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
