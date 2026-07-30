package exh.recs.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TagTaste

// KMK v0.8.10-fix9 -->
class TagPreferenceGroupingPolicyTest {

    private fun tag(name: String, pref: TagPreference?): TagTaste = TagTaste(
        normalizedTag = name.lowercase(),
        displayName = name,
        preference = pref?.value ?: 0,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `a mixed list groups Preferred and Blocked separately, each independently`() {
        val tags = listOf(
            tag("action", TagPreference.PREFER),
            tag("gore", TagPreference.BLOCK),
            tag("slow burn", TagPreference.DISLIKE),
            tag("comedy", TagPreference.PREFER),
        )

        val grouped = TagPreferenceGroupingPolicy.group(tags)

        assertEquals(listOf("action", "comedy"), grouped.preferred.map { it.displayName })
        assertEquals(listOf("gore"), grouped.blocked.map { it.displayName })
        assertEquals(listOf("slow burn"), grouped.disliked.map { it.displayName })
        assertTrue(grouped.other.isEmpty())
    }

    @Test
    fun `an unrecognized preference value is preserved under other, not dropped`() {
        val tags = listOf(tag("mystery", null))

        val grouped = TagPreferenceGroupingPolicy.group(tags)

        assertEquals(1, grouped.other.size)
        assertEquals("mystery", grouped.other.single().displayName)
    }

    @Test
    fun `an empty input list produces all-empty groups`() {
        val grouped = TagPreferenceGroupingPolicy.group(emptyList())

        assertTrue(grouped.isEmpty)
    }

    @Test
    fun `grouping never reorders tags within a group`() {
        val tags = listOf(
            tag("z", TagPreference.PREFER),
            tag("a", TagPreference.PREFER),
            tag("m", TagPreference.PREFER),
        )

        val grouped = TagPreferenceGroupingPolicy.group(tags)

        assertEquals(listOf("z", "a", "m"), grouped.preferred.map { it.displayName })
    }

    @Test
    fun `every input tag appears in exactly one output group`() {
        val tags = listOf(
            tag("a", TagPreference.PREFER),
            tag("b", TagPreference.DISLIKE),
            tag("c", TagPreference.BLOCK),
            tag("d", null),
        )

        val grouped = TagPreferenceGroupingPolicy.group(tags)
        val total = grouped.preferred.size + grouped.disliked.size + grouped.blocked.size + grouped.other.size

        assertEquals(tags.size, total)
    }
}
// KMK <--
