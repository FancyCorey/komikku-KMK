package eu.kanade.tachiyomi.data.backup

// KMK --> 1.14.0 reconciliation Phase 5: memo proto field round-trip / compatibility guards
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.core.common.extensions.JsonObjectEmptyBytes
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.data.MemoColumnAdapter

/**
 * Guards the mangas.memo/chapters.memo backup wiring added in Phase 5 of the 1.14.0
 * reconciliation (proto 112 on BackupManga, proto 13 on BackupChapter):
 *
 * - A populated memo value survives a real ProtoBuf encode/decode round trip.
 * - A backup produced before this field existed (no field 112/13 in the byte stream)
 *   still decodes cleanly, with memo defaulting to an empty JSON object — old backups
 *   must not fail to restore or throw on the new field.
 * - A backup produced by a newer/other build with an extra unknown field still decodes
 *   cleanly on this schema — forward-compatibility for whatever the next field number
 *   ends up being.
 */
class Kmk114MemoBackupRoundTripTest {

    private val parser = ProtoBuf

    // Mirrors BackupManga's proto shape *before* field 112 (memo) was added, to simulate
    // a backup file produced prior to this reconciliation.
    @Serializable
    private class PreMemoBackupManga(
        @ProtoNumber(1) var source: Long,
        @ProtoNumber(2) var url: String,
        @ProtoNumber(3) var title: String = "",
    )

    // Mirrors BackupChapter's proto shape before field 13 (memo) was added.
    @Serializable
    private class PreMemoBackupChapter(
        @ProtoNumber(1) var url: String,
        @ProtoNumber(2) var name: String,
    )

    // A hypothetical future backup with a field number beyond anything BackupManga
    // currently declares, to simulate forward-compatibility with newer backup producers.
    @Serializable
    private class FutureBackupManga(
        @ProtoNumber(1) var source: Long,
        @ProtoNumber(2) var url: String,
        @ProtoNumber(3) var title: String = "",
        @ProtoNumber(999) var fromTheFuture: String = "unknown-to-us",
    )

    @Test
    fun `BackupManga memo survives encode and decode`() {
        val memoBytes = MemoColumnAdapter.encode(
            kotlinx.serialization.json.Json.parseToJsonElement("""{"note":"hello"}""").let {
                it as kotlinx.serialization.json.JsonObject
            },
        )
        val original = BackupManga(source = 1L, url = "/manga/x", title = "X", memo = memoBytes)

        val bytes = parser.encodeToByteArray(BackupManga.serializer(), original)
        val decoded = parser.decodeFromByteArray(BackupManga.serializer(), bytes)

        assertArrayEquals(memoBytes, decoded.memo)
        assertEquals("hello", MemoColumnAdapter.decode(decoded.memo)["note"]?.toString()?.trim('"'))
    }

    @Test
    fun `BackupChapter memo survives encode and decode`() {
        val memoBytes = MemoColumnAdapter.encode(
            kotlinx.serialization.json.Json.parseToJsonElement("""{"flag":true}""").let {
                it as kotlinx.serialization.json.JsonObject
            },
        )
        val original = BackupChapter(url = "/chapter/1", name = "Ch. 1", memo = memoBytes)

        val bytes = parser.encodeToByteArray(BackupChapter.serializer(), original)
        val decoded = parser.decodeFromByteArray(BackupChapter.serializer(), bytes)

        assertArrayEquals(memoBytes, decoded.memo)
    }

    @Test
    fun `old backup without memo field decodes with empty-object default`() {
        val old = PreMemoBackupManga(source = 42L, url = "/manga/old", title = "Old Manga")
        val bytes = parser.encodeToByteArray(PreMemoBackupManga.serializer(), old)

        val decoded = assertDoesNotThrow<BackupManga> {
            parser.decodeFromByteArray(BackupManga.serializer(), bytes)
        }

        assertEquals(42L, decoded.source)
        assertEquals("/manga/old", decoded.url)
        assertEquals("Old Manga", decoded.title)
        assertArrayEquals(JsonObjectEmptyBytes, decoded.memo)
    }

    @Test
    fun `old chapter backup without memo field decodes with empty-object default`() {
        val old = PreMemoBackupChapter(url = "/chapter/old", name = "Old Chapter")
        val bytes = parser.encodeToByteArray(PreMemoBackupChapter.serializer(), old)

        val decoded = assertDoesNotThrow<BackupChapter> {
            parser.decodeFromByteArray(BackupChapter.serializer(), bytes)
        }

        assertEquals("/chapter/old", decoded.url)
        assertEquals("Old Chapter", decoded.name)
        assertArrayEquals(JsonObjectEmptyBytes, decoded.memo)
    }

    @Test
    fun `backup with a field unknown to this schema still decodes known fields`() {
        val fromTheFuture = FutureBackupManga(source = 7L, url = "/manga/future", title = "Future")
        val bytes = parser.encodeToByteArray(FutureBackupManga.serializer(), fromTheFuture)

        val decoded = assertDoesNotThrow<BackupManga> {
            parser.decodeFromByteArray(BackupManga.serializer(), bytes)
        }

        assertEquals(7L, decoded.source)
        assertEquals("/manga/future", decoded.url)
        assertEquals("Future", decoded.title)
        assertArrayEquals(JsonObjectEmptyBytes, decoded.memo)
    }

    // Mirrors BackupDecoder.decode()'s own SerializationException -> IOException translation
    // for corrupt/malformed backup payloads, without needing BackupDecoder's Android Context/Uri
    // plumbing -- proves the failure signal it depends on actually fires for garbage bytes.
    @Test
    fun `decoding garbage bytes as a Backup throws a catchable SerializationException`() {
        val garbage = byteArrayOf(-1, -2, -3, 0, 127, 5, 9, 33, -128, 64)

        org.junit.jupiter.api.Assertions.assertThrows(
            kotlinx.serialization.SerializationException::class.java,
        ) {
            parser.decodeFromByteArray(eu.kanade.tachiyomi.data.backup.models.Backup.serializer(), garbage)
        }
    }

    // NOTE: unlike outright-garbage bytes (which reliably throw SerializationException, caught by
    // BackupDecoder.decode()'s `catch (_: SerializationException)`), a *truncated but otherwise
    // well-formed* protobuf stream throws IndexOutOfBoundsException from kotlinx.serialization's
    // protobuf reader -- which is NOT a SerializationException and would NOT be caught by
    // BackupDecoder, letting a corrupted/truncated backup file crash instead of showing the
    // "invalid backup file" error message. Verified this is not a reconciliation regression:
    // BackupDecoder.kt is byte-identical between the official v1.13.6 and v1.14.0 tags, so this
    // is a pre-existing gap in code this reconciliation didn't touch, not something to silently
    // fix as a side effect here -- documented and flagged separately instead.
    @Test
    fun `decoding a truncated valid backup throws (but not the exception type BackupDecoder catches)`() {
        val original = BackupManga(source = 1L, url = "/manga/x", title = "X")
        val bytes = parser.encodeToByteArray(BackupManga.serializer(), original)
        val truncated = bytes.copyOf(bytes.size / 2)

        val thrown = org.junit.jupiter.api.Assertions.assertThrows(Throwable::class.java) {
            parser.decodeFromByteArray(BackupManga.serializer(), truncated)
        }
        org.junit.jupiter.api.Assertions.assertFalse(
            thrown is kotlinx.serialization.SerializationException,
            "If this ever becomes a SerializationException, BackupDecoder's existing catch clause " +
                "would actually handle truncated backups -- update this test to assertThrows " +
                "SerializationException instead, and note the gap is closed.",
        )
    }
}
// KMK <--
