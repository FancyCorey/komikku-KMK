package exh.recs.bestversion

import exh.recs.RecommendationErrorClassifier
import exh.recs.RecommendationErrorKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class BestVersionErrorReasonTest {

    @Test
    fun `recommendation error reasons preserve the shared classifier result`() {
        assertEquals(
            BestVersionErrorReason.Recommendation(RecommendationErrorKind.Network),
            BestVersionErrorReason.Recommendation(RecommendationErrorClassifier.classify(UnknownHostException())),
        )
        assertEquals(
            BestVersionErrorReason.Recommendation(RecommendationErrorKind.Timeout),
            BestVersionErrorReason.Recommendation(RecommendationErrorClassifier.classify(SocketTimeoutException())),
        )
        assertEquals(
            BestVersionErrorReason.Recommendation(RecommendationErrorKind.ExtensionIncompatible),
            BestVersionErrorReason.Recommendation(RecommendationErrorClassifier.classify(NoClassDefFoundError())),
        )
        assertEquals(
            BestVersionErrorReason.Recommendation(RecommendationErrorKind.Internal),
            BestVersionErrorReason.Recommendation(RecommendationErrorClassifier.classify(IllegalStateException())),
        )
    }

    @Test
    fun `Best Version error states accept typed reasons rather than messages`() {
        val expectedParameter = BestVersionErrorReason::class.java
        listOf(
            BestVersionStep.Error::class.java,
            CandidateChapterState.ChapterError::class.java,
            CandidatePreviewState.PreviewError::class.java,
        ).forEach { type ->
            val constructor = type.declaredConstructors.single { it.parameterCount == 1 }
            assertEquals(expectedParameter, constructor.parameterTypes.single(), "$type must accept a typed reason")
            assertFalse(type.declaredFields.any { it.name == "message" }, "$type must not retain a message field")
            assertTrue(type.declaredFields.any { it.name == "reason" }, "$type must expose its typed reason")
        }
    }

    @Test
    fun `Best Version renderer has no verbatim unknown error fallback`() {
        val source = File("src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt").readText()

        assertTrue(source.contains("private fun bestVersionErrorText(reason: BestVersionErrorReason)"))
        assertTrue(source.contains("BestVersionErrorReason.OriginMissing"))
        assertTrue(source.contains("BestVersionErrorReason.SourceUnavailable"))
        assertFalse(source.contains("?: return key"))
        assertFalse(source.contains("private fun recommendationErrorText(key: String)"))
    }
}
