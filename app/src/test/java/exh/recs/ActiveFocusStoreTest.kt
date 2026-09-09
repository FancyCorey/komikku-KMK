package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActiveFocusStoreTest {

    @Test
    fun `criteria round trips without changing canonical values`() {
        val criteria = RecommendationFocusPolicy.FocusCriteria(
            include = setOf("comedy", "action"),
            exclude = setOf("horror"),
            matchAll = false,
        )

        assertEquals(criteria, ActiveFocusStore.parse(ActiveFocusStore.serialize(criteria)))
    }

    @Test
    fun `blank and malformed values fail closed to cleared focus`() {
        assertEquals(RecommendationFocusPolicy.FocusCriteria.EMPTY, ActiveFocusStore.parse(""))
        assertEquals(RecommendationFocusPolicy.FocusCriteria.EMPTY, ActiveFocusStore.parse("not-json"))
    }

    @Test
    fun `empty criteria is inactive and still serializes explicitly`() {
        val serialized = ActiveFocusStore.serialize(RecommendationFocusPolicy.FocusCriteria.EMPTY)

        assertFalse(ActiveFocusStore.parse(serialized).isActive)
        assertTrue(serialized.contains("includeGroups"))
        assertTrue(serialized.contains("matchAll"))
    }
}
