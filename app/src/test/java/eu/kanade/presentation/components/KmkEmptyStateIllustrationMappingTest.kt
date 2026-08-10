package eu.kanade.presentation.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
/**
 * Direct tests for the [KmkEmptyStateArtwork] -> [androidx.compose.ui.graphics.vector.ImageVector]
 * mapping. Runs as a plain JVM unit test -- `ImageVector.Builder` produces a tree of immutable
 * `PathNode` data (pure Kotlin), not an Android-backed `android.graphics.Path`, so no Robolectric or
 * device instrumentation is needed here, matching this project's standing preference for narrow,
 * fast, JVM-only tests over a heavier test framework.
 *
 * [SourceEvaluationArtGeometry] (the one artwork this mapping deliberately does not cover -- see
 * [kmkEmptyStateImageVector]'s own `error(...)` branch) is *not* tested this way: it builds real
 * `androidx.compose.ui.graphics.Path` objects via `Path.op(...)`, which are backed by
 * `android.graphics.Path` and are not usable from a plain JVM unit test.
 */
class KmkEmptyStateIllustrationMappingTest {

    @Test
    fun `the artwork enum has exactly five stable identities`() {
        assertEquals(
            setOf(
                KmkEmptyStateArtwork.FOR_YOU,
                KmkEmptyStateArtwork.SOURCE_EVALUATION,
                KmkEmptyStateArtwork.ACTION_HISTORY,
                KmkEmptyStateArtwork.FIND_BEST_VERSION_UNAVAILABLE,
                KmkEmptyStateArtwork.READER_SCHEDULE,
            ),
            KmkEmptyStateArtwork.entries.toSet(),
        )
    }

    @Test
    fun `every non-Source-Evaluation artwork resolves to a distinct, non-empty ImageVector`() {
        val nonSourceEvaluation = KmkEmptyStateArtwork.entries - KmkEmptyStateArtwork.SOURCE_EVALUATION

        val vectors = nonSourceEvaluation.associateWith { kmkEmptyStateImageVector(it) }

        vectors.forEach { (artwork, vector) ->
            assertTrue(vector.root.size > 0, "$artwork's ImageVector must have at least one path node")
            assertEquals(256f, vector.viewportWidth, "$artwork should share the same 256x256 source coordinate space")
            assertEquals(256f, vector.viewportHeight, "$artwork should share the same 256x256 source coordinate space")
        }
        // Every resolved vector is a distinct instance/name -- no accidental sharing between artworks.
        assertEquals(vectors.size, vectors.values.map { it.name }.toSet().size)
    }

    @Test
    fun `SOURCE_EVALUATION is deliberately not mapped to an ImageVector`() {
        assertThrows(IllegalStateException::class.java) {
            kmkEmptyStateImageVector(KmkEmptyStateArtwork.SOURCE_EVALUATION)
        }
    }
}
// KMK <--
