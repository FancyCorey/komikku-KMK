package eu.kanade.tachiyomi.ui.reader.bridge

import androidx.lifecycle.SavedStateHandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlternateSourceReaderStateCodecTest {

    @Test
    fun `valid session round trips through flat primitives`() {
        val session = AlternateSourceReaderSession.create(
            bridgeKey = readerBridgeKey(),
            primaryResumeRoute = readerRoute(AlternateSourceReaderRouteRole.PRIMARY),
            alternateRoute = readerRoute(AlternateSourceReaderRouteRole.ALTERNATE),
            activeTargetId = READER_TARGET_ID,
            bridgeUpdatedAt = 2_000L,
            mappingUpdatedAt = 2_100L,
            sessionId = READER_SESSION_ID,
        )
        val encoded = AlternateSourceReaderStateCodec.encode(session)

        assertEquals(
            AlternateSourceReaderStateCodec.DecodeResult.Valid(session),
            AlternateSourceReaderStateCodec.decode(encoded),
        )
        assertTrue(
            AlternateSourceReaderStateCodec.Encoded::class.java.declaredFields
                .filterNot { it.isSynthetic }
                .all { field ->
                    field.type.isPrimitive || field.type == String::class.java
                },
        )
    }

    @Test
    fun `fresh SavedStateHandle restores the production session store from fixture primitives`() {
        val session = readerSession()
        val encoded = linkedMapOf<String, Any?>()
        AlternateSourceReaderStateCodec.write(session, encoded::set)
        val recreatedHandle = SavedStateHandle(encoded)
        val store = AlternateSourceReaderSessionStore(
            get = { key -> recreatedHandle.get<Any?>(key) },
            set = { key, value -> recreatedHandle[key] = value },
            remove = { key -> recreatedHandle.remove<Any?>(key) },
        )

        assertEquals(AlternateSourceReaderStateCodec.DecodeResult.Valid(session), store.read())
        assertTrue(store.hasStoredState())

        store.clear()

        assertFalse(store.hasStoredState())
        assertEquals(AlternateSourceReaderStateCodec.DecodeResult.Invalid, store.read())
    }

    @Test
    fun `individual primitive writer reader and clear own the complete key set`() {
        val values = mutableMapOf<String, Any?>()
        val session = readerSession()

        AlternateSourceReaderStateCodec.write(session, values::set)

        assertEquals(AlternateSourceReaderStateCodec.ALL_KEYS.toSet(), values.keys)
        assertTrue(values.values.all { it == null || it is String || it is Int || it is Long || it is Boolean })
        assertEquals(
            AlternateSourceReaderStateCodec.DecodeResult.Valid(session),
            AlternateSourceReaderStateCodec.read(values::get),
        )

        values[AlternateSourceReaderStateCodec.KEY_CURRENT_PAGE_INDEX] = "not-an-int"
        assertEquals(
            AlternateSourceReaderStateCodec.DecodeResult.Invalid,
            AlternateSourceReaderStateCodec.read(values::get),
        )

        AlternateSourceReaderStateCodec.write(session, values::set)
        values[AlternateSourceReaderStateCodec.KEY_ACTIVE_TARGET_ID] = 42L
        assertEquals(
            AlternateSourceReaderStateCodec.DecodeResult.Invalid,
            AlternateSourceReaderStateCodec.read(values::get),
        )

        AlternateSourceReaderStateCodec.clear(values::remove)
        assertTrue(values.isEmpty())
    }

    @Test
    fun `decode fails closed for malformed identity version route and bounds`() {
        val encoded = AlternateSourceReaderStateCodec.encode(readerSession())
        val malformed = listOf(
            encoded.copy(schemaVersion = 99),
            encoded.copy(sessionId = "not-a-session"),
            encoded.copy(alternateSource = encoded.primarySource, alternateUrl = encoded.primaryUrl),
            encoded.copy(primaryMangaId = -1L),
            encoded.copy(currentChapterId = 0L),
            encoded.copy(currentPageIndex = AlternateSourceReaderSession.MAX_PAGE_INDEX + 1),
            encoded.copy(currentChapterUrl = "x".repeat(4_097)),
            encoded.copy(currentRole = "UNKNOWN"),
            encoded.copy(lastTransitionReason = "UNKNOWN"),
            encoded.copy(lastSafeRouteFingerprint = "0".repeat(64)),
            encoded.copy(transitionCount = AlternateSourceReaderSession.MAX_TRANSITIONS + 1),
            encoded.copy(failedResolutionCount = AlternateSourceReaderSession.MAX_FAILED_RESOLUTIONS + 1),
        )

        assertTrue(malformed.all { AlternateSourceReaderStateCodec.decode(it) == AlternateSourceReaderStateCodec.DecodeResult.Invalid })
        assertEquals(AlternateSourceReaderStateCodec.DecodeResult.Invalid, AlternateSourceReaderStateCodec.decode(null))
    }

    @Test
    fun `persisted session model has no descriptive or secret carrying fields`() {
        val names = (
            AlternateSourceReaderSession::class.java.declaredFields +
                AlternateSourceReaderRoute::class.java.declaredFields +
                AlternateSourceReaderStateCodec.Encoded::class.java.declaredFields
            )
            .filterNot { it.isSynthetic }
            .map { it.name.lowercase() }

        listOf("title", "chaptername", "chapternumber", "sourcename", "cookie", "credential", "header", "exception")
            .forEach { forbidden -> assertFalse(names.any { forbidden in it }) }
    }
}
