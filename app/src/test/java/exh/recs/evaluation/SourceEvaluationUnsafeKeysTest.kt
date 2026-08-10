package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.SourceEvaluationUnsafeKeys

// KMK -->
class SourceEvaluationUnsafeKeysTest {

    @Test
    fun `source-level key includes sourceId when present`() {
        val key = SourceEvaluationUnsafeKeys.build("sig1", "com.example.ext", 12345L)
        assertEquals("sig1|com.example.ext|12345", key)
    }

    @Test
    fun `extension-level key used when sourceId is null`() {
        val key = SourceEvaluationUnsafeKeys.build("sig1", "com.example.ext", null)
        assertEquals("sig1|com.example.ext", key)
    }

    @Test
    fun `extensionKey always returns sig+pkg without sourceId`() {
        val key = SourceEvaluationUnsafeKeys.extensionKey("sig2", "com.other.ext")
        assertEquals("sig2|com.other.ext", key)
    }

    @Test
    fun `source-level key differs from extension-level key`() {
        val sourceKey = SourceEvaluationUnsafeKeys.build("sig", "com.ext", 999L)
        val extKey = SourceEvaluationUnsafeKeys.extensionKey("sig", "com.ext")
        assertNotEquals(sourceKey, extKey)
    }

    @Test
    fun `different source ids produce different keys`() {
        val key1 = SourceEvaluationUnsafeKeys.build("sig", "pkg", 1L)
        val key2 = SourceEvaluationUnsafeKeys.build("sig", "pkg", 2L)
        assertNotEquals(key1, key2)
    }

    private fun assertNotEquals(a: String, b: String) {
        assert(a != b) { "Expected values to differ but both were: $a" }
    }
}
// KMK <--
