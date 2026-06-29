package exh.recs

// KMK -->
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceStatusDisplayOrderTest {

    private fun input(
        sourceId: Long,
        priorityIndex: Int,
        hasMatches: Boolean,
        isDisliked: Boolean,
    ) = SourceDisplayOrderInput(sourceId, priorityIndex, hasMatches, isDisliked)

    @Test
    fun `matches sort before no-match`() {
        val inputs = listOf(
            input(1L, 0, hasMatches = false, isDisliked = false),
            input(2L, 1, hasMatches = true, isDisliked = false),
        )
        val sorted = SourceStatusDisplayOrder.sort(inputs)
        assertEquals(2L, sorted[0].sourceId, "match source should be first")
        assertEquals(1L, sorted[1].sourceId, "no-match source should be second")
    }

    @Test
    fun `no-match sorts before disliked`() {
        val inputs = listOf(
            input(1L, 0, hasMatches = false, isDisliked = true),
            input(2L, 1, hasMatches = false, isDisliked = false),
        )
        val sorted = SourceStatusDisplayOrder.sort(inputs)
        assertEquals(2L, sorted[0].sourceId, "no-match should be before disliked")
        assertEquals(1L, sorted[1].sourceId, "disliked should be last")
    }

    @Test
    fun `priority preserved inside matches group`() {
        val inputs = listOf(
            input(1L, 2, hasMatches = true, isDisliked = false),
            input(2L, 0, hasMatches = true, isDisliked = false),
            input(3L, 1, hasMatches = true, isDisliked = false),
        )
        val sorted = SourceStatusDisplayOrder.sort(inputs)
        assertEquals(
            listOf(2L, 3L, 1L),
            sorted.map { it.sourceId },
            "priority within matches group must be preserved",
        )
    }

    @Test
    fun `priority preserved inside no-match group`() {
        val inputs = listOf(
            input(1L, 1, hasMatches = false, isDisliked = false),
            input(2L, 0, hasMatches = false, isDisliked = false),
        )
        val sorted = SourceStatusDisplayOrder.sort(inputs)
        assertEquals(2L, sorted[0].sourceId)
        assertEquals(1L, sorted[1].sourceId)
    }

    @Test
    fun `priority preserved inside disliked group`() {
        val inputs = listOf(
            input(1L, 2, hasMatches = false, isDisliked = true),
            input(2L, 0, hasMatches = false, isDisliked = true),
            input(3L, 1, hasMatches = false, isDisliked = true),
        )
        val sorted = SourceStatusDisplayOrder.sort(inputs)
        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.sourceId })
    }

    @Test
    fun `empty list does not crash`() {
        val sorted = SourceStatusDisplayOrder.sort(emptyList())
        assertTrue(sorted.isEmpty())
    }

    @Test
    fun `full ordering HasMatches then NoMatches then Disliked`() {
        val inputs = listOf(
            input(1L, 0, hasMatches = false, isDisliked = true), // disliked
            input(2L, 1, hasMatches = false, isDisliked = false), // no-match
            input(3L, 2, hasMatches = true, isDisliked = false), // match
        )
        val sorted = SourceStatusDisplayOrder.sort(inputs)
        assertEquals(3L, sorted[0].sourceId, "has-matches first")
        assertEquals(2L, sorted[1].sourceId, "no-match second")
        assertEquals(1L, sorted[2].sourceId, "disliked last")
    }

    @Test
    fun `disliked is always last even if high priority`() {
        val inputs = listOf(
            input(1L, 0, hasMatches = false, isDisliked = true), // high priority but disliked
            input(2L, 9, hasMatches = false, isDisliked = false), // low priority, no-match
        )
        val sorted = SourceStatusDisplayOrder.sort(inputs)
        assertEquals(2L, sorted[0].sourceId, "no-match low-priority before disliked high-priority")
        assertEquals(1L, sorted[1].sourceId)
    }
}
// KMK <--
