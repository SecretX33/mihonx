package eu.kanade.tachiyomi.data.backup.models

import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Test

@OptIn(ExperimentalSerializationApi::class)
class BackupCompatibilityTest {

    @Test
    fun `Mihon backup decodes in MihonX`() {
        val backup = UpstreamBackup(
            backupManga = listOf(
                UpstreamBackupManga(
                    source = 42L,
                    url = "/series",
                    title = "Series",
                    chapters = listOf(
                        UpstreamBackupChapter(
                            url = "/chapter-1",
                            name = "Chapter 1",
                            read = true,
                            lastPageRead = 7L,
                            memo = "{}".encodeToByteArray(),
                        ),
                    ),
                    notes = "Notes",
                    initialized = true,
                    memo = "{}".encodeToByteArray(),
                ),
            ),
        )

        val decoded = ProtoBuf.decodeFromByteArray<Backup>(ProtoBuf.encodeToByteArray(backup))
        val manga = decoded.backupManga.single()
        val chapter = manga.chapters.single()

        manga.source shouldBe 42L
        manga.url shouldBe "/series"
        manga.title shouldBe "Series"
        manga.notes shouldBe "Notes"
        manga.initialized shouldBe true
        manga.customTitle shouldBe ""
        manga.customGenre shouldBe emptyList()
        chapter.url shouldBe "/chapter-1"
        chapter.name shouldBe "Chapter 1"
        chapter.read shouldBe true
        chapter.lastPageRead shouldBe 7L
        chapter.excluded shouldBe false
    }

    @Test
    fun `MihonX backup decodes in Mihon`() {
        val backup = Backup(
            backupManga = listOf(
                BackupManga(
                    source = 42L,
                    url = "/series",
                    title = "Series",
                    chapters = listOf(
                        BackupChapter(
                            url = "/chapter-1",
                            name = "Chapter 1",
                            read = true,
                            lastPageRead = 7L,
                            memo = "{}".encodeToByteArray(),
                            excluded = true,
                        ),
                    ),
                    notes = "Notes",
                    initialized = true,
                    customTitle = "Custom series",
                    customAuthor = "Custom author",
                    customGenre = listOf("Custom genre"),
                    memo = "{}".encodeToByteArray(),
                ),
            ),
        )

        val decoded = ProtoBuf.decodeFromByteArray<UpstreamBackup>(ProtoBuf.encodeToByteArray(backup))
        val manga = decoded.backupManga.single()
        val chapter = manga.chapters.single()

        manga.source shouldBe 42L
        manga.url shouldBe "/series"
        manga.title shouldBe "Series"
        manga.notes shouldBe "Notes"
        manga.initialized shouldBe true
        chapter.url shouldBe "/chapter-1"
        chapter.name shouldBe "Chapter 1"
        chapter.read shouldBe true
        chapter.lastPageRead shouldBe 7L
    }

    @Test
    fun `Mihon round trip preserves common fields`() {
        val backup = Backup(
            backupManga = listOf(
                BackupManga(
                    source = 42L,
                    url = "/series",
                    title = "Series",
                    chapters = listOf(
                        BackupChapter(
                            url = "/chapter-1",
                            name = "Chapter 1",
                            read = true,
                            excluded = true,
                        ),
                    ),
                    notes = "Notes",
                    customTitle = "Custom series",
                ),
            ),
        )

        val mihonBackup = ProtoBuf.decodeFromByteArray<UpstreamBackup>(ProtoBuf.encodeToByteArray(backup))
        val restored = ProtoBuf.decodeFromByteArray<Backup>(ProtoBuf.encodeToByteArray(mihonBackup))
        val manga = restored.backupManga.single()
        val chapter = manga.chapters.single()

        manga.source shouldBe 42L
        manga.url shouldBe "/series"
        manga.title shouldBe "Series"
        manga.notes shouldBe "Notes"
        manga.customTitle shouldBe ""
        chapter.url shouldBe "/chapter-1"
        chapter.name shouldBe "Chapter 1"
        chapter.read shouldBe true
        chapter.excluded shouldBe false
    }
}

@Serializable
private data class UpstreamBackup(
    @ProtoNumber(1) val backupManga: List<UpstreamBackupManga>,
)

@Serializable
private data class UpstreamBackupManga(
    @ProtoNumber(1) val source: Long,
    @ProtoNumber(2) val url: String,
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(16) val chapters: List<UpstreamBackupChapter> = emptyList(),
    @ProtoNumber(110) val notes: String = "",
    @ProtoNumber(111) val initialized: Boolean = false,
    @ProtoNumber(112) val memo: ByteArray = "{}".encodeToByteArray(),
)

@Serializable
private data class UpstreamBackupChapter(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(4) val read: Boolean = false,
    @ProtoNumber(6) val lastPageRead: Long = 0,
    @ProtoNumber(13) val memo: ByteArray = "{}".encodeToByteArray(),
)
