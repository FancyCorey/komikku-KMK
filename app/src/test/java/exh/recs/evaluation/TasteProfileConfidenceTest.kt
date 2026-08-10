package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.TasteProfile
import tachiyomi.domain.taste.model.TasteProfileConfidence

// KMK -->
class TasteProfileConfidenceTest {

    private fun profileWith(
        positiveWeights: Int = 0,
        negativeWeights: Int = 0,
        positiveExplicit: Int = 0,
        negativeExplicit: Int = 0,
        blocked: Int = 0,
    ): TasteProfile {
        val learned = (1..positiveWeights).associate { "pos$it" to 1.0 } +
            (1..negativeWeights).associate { "neg$it" to -1.0 }
        val explicit = (1..positiveExplicit).associate { "epos$it" to 1 } +
            (1..negativeExplicit).associate { "eneg$it" to -1 }
        val blockedSet = (1..blocked).map { "blocked$it" }.toSet()
        return TasteProfile(
            learnedTagWeights = learned,
            explicitTagPreferences = explicit,
            sourceAffinity = emptyMap(),
            blockedGroups = blockedSet,
        )
    }

    @Test
    fun `empty profile reports insufficient evidence`() {
        val confidence = TasteProfileConfidence.from(TasteProfile.EMPTY)
        assertFalse(confidence.isSufficientForPersonalizedEvaluation)
        assertEquals(0, confidence.usablePositiveTagCount)
        assertEquals(0, confidence.usableNegativeTagCount)
    }

    @Test
    fun `one rated manga (few tags) reports insufficient evidence`() {
        val confidence = TasteProfileConfidence.from(profileWith(positiveWeights = 2))
        assertFalse(confidence.isSufficientForPersonalizedEvaluation)
    }

    @Test
    fun `sufficient positive and negative tags reports sufficient evidence`() {
        val confidence = TasteProfileConfidence.from(profileWith(positiveWeights = 4, negativeWeights = 2))
        assertTrue(confidence.isSufficientForPersonalizedEvaluation)
        assertEquals(4, confidence.usablePositiveTagCount)
        assertEquals(2, confidence.usableNegativeTagCount)
    }

    @Test
    fun `explicit positive preferences count toward positive tag total`() {
        val confidence = TasteProfileConfidence.from(
            profileWith(positiveWeights = 1, positiveExplicit = 3, negativeWeights = 1),
        )
        assertEquals(4, confidence.usablePositiveTagCount)
    }

    @Test
    fun `explicit negative preferences count toward negative tag total`() {
        val confidence = TasteProfileConfidence.from(
            profileWith(negativeWeights = 1, negativeExplicit = 2),
        )
        assertEquals(3, confidence.usableNegativeTagCount)
    }

    @Test
    fun `blocked groups count toward negative tag total`() {
        val confidence = TasteProfileConfidence.from(
            profileWith(blocked = 3),
        )
        assertEquals(3, confidence.usableNegativeTagCount)
    }

    @Test
    fun `exactly threshold counts as sufficient`() {
        // threshold: total >= 5 AND positive >= 2
        val confidence = TasteProfileConfidence.from(profileWith(positiveWeights = 2, negativeWeights = 3))
        assertTrue(confidence.isSufficientForPersonalizedEvaluation)
    }

    @Test
    fun `positive below minimum makes insufficient even with many negatives`() {
        val confidence = TasteProfileConfidence.from(profileWith(positiveWeights = 1, negativeWeights = 10))
        assertFalse(confidence.isSufficientForPersonalizedEvaluation)
    }

    @Test
    fun `EMPTY constant matches empty profile`() {
        val fromEmpty = TasteProfileConfidence.from(TasteProfile.EMPTY)
        assertFalse(TasteProfileConfidence.EMPTY.isSufficientForPersonalizedEvaluation)
        assertFalse(fromEmpty.isSufficientForPersonalizedEvaluation)
    }

    @Test
    fun `source affinity flag set when any source has positive affinity`() {
        val profile = TasteProfile(
            learnedTagWeights = emptyMap(),
            explicitTagPreferences = emptyMap(),
            sourceAffinity = mapOf(1L to 0.2),
            blockedGroups = emptySet(),
        )
        val confidence = TasteProfileConfidence.from(profile)
        assertTrue(confidence.hasSourceAffinity)
    }

    @Test
    fun `source affinity flag false when affinity is zero or negative`() {
        val profile = TasteProfile(
            learnedTagWeights = emptyMap(),
            explicitTagPreferences = emptyMap(),
            sourceAffinity = mapOf(1L to 0.0, 2L to -0.1),
            blockedGroups = emptySet(),
        )
        val confidence = TasteProfileConfidence.from(profile)
        assertFalse(confidence.hasSourceAffinity)
    }
}
// KMK <--
