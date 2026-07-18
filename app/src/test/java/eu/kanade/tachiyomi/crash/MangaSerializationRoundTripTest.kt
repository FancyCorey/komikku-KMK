package eu.kanade.tachiyomi.crash

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mihon.core.common.extensions.EMPTY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Phase 0 crash-investigation evidence for the v0.8.10-fix1 gate.
 *
 * The reported application-wide crash pattern -- Settings opens fine, but Library / For You / manga
 * detail / Browse / reader / rated collections can close the app -- points at a factor shared by every
 * manga-bearing screen but not by pure-preference Settings. The 1.14.0 reconciliation made
 * [Manga] proxy Java serialization (the mechanism Android uses to save instance state when the app is
 * backgrounded or the process is recreated) through a kotlinx JSON round trip
 * (`writeReplace()` -> `JavaToKotlinXSerializable` -> `readResolve()`). Nothing exercised that real
 * round trip -- the reconciliation verified compile + unit tests only, and no test drives
 * `ObjectOutputStream`/`ObjectInputStream` over a [Manga].
 *
 * These tests drive exactly that Android background-save path against a real [Manga] to prove whether
 * the proxy actually round-trips, rather than assuming it from the fact that it compiles.
 */
class MangaSerializationRoundTripTest {

    private fun javaRoundTrip(manga: Manga): Manga {
        val bytes = ByteArrayOutputStream().use { baos ->
            ObjectOutputStream(baos).use { it.writeObject(manga) }
            baos.toByteArray()
        }
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as Manga }
    }

    @Test
    fun `a default non-favorite manga survives the Android background-save Java serialization round trip`() {
        val original = Manga.create().copy(id = 42L, source = 7L, url = "/manga/42", ogTitle = "Test Manga")
        val restored = javaRoundTrip(original)
        assertEquals(original.id, restored.id)
        assertEquals(original.source, restored.source)
        assertEquals(original.url, restored.url)
        assertEquals(original.ogTitle, restored.ogTitle)
        assertEquals(original, restored)
    }

    @Test
    fun `a manga carrying a populated memo JsonObject survives the round trip`() {
        val original = Manga.create().copy(
            id = 99L,
            source = 3L,
            url = "/manga/99",
            ogTitle = "Memo Manga",
            memo = JsonObject(mapOf("k" to JsonPrimitive("v"), "n" to JsonPrimitive(5))),
        )
        val restored = javaRoundTrip(original)
        assertEquals(original.memo, restored.memo)
        assertEquals(original, restored)
    }

    @Test
    fun `a manga with a full set of populated optional fields survives the round trip`() {
        val original = Manga.create().copy(
            id = 1L,
            source = 2L,
            url = "/manga/1",
            ogTitle = "Full Manga",
            ogArtist = "Artist",
            ogAuthor = "Author",
            ogThumbnailUrl = "https://example.invalid/cover.jpg",
            ogDescription = "A description with unicode: ★ ☆ 日本語",
            ogGenre = listOf("Action", "Romance"),
            ogStatus = 2L,
            notes = "some notes",
            memo = JsonObject.EMPTY,
        )
        val restored = javaRoundTrip(original)
        assertEquals(original, restored)
    }
}
