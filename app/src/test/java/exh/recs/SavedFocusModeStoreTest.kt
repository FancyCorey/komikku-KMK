package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.21-fix2: AUG-14 slice 3 -->
class SavedFocusModeStoreTest {

    private var nextId = 0
    private fun newId(): String = "id-${nextId++}"

    @Test
    fun `an empty stored value parses to an empty list`() {
        assertTrue(SavedFocusModeStore.parse("").isEmpty())
    }

    @Test
    fun `a malformed stored value degrades to an empty list, never a crash`() {
        assertTrue(SavedFocusModeStore.parse("{not valid json at all").isEmpty())
        assertTrue(SavedFocusModeStore.parse("[]").isEmpty())
        assertTrue(SavedFocusModeStore.parse("not json").isEmpty())
    }

    @Test
    fun `create appends at the end of order and round-trips through serialize-parse`() {
        var modes = SavedFocusModeStore.create(emptyList(), "Cozy night", setOf("comedy", "slice-of-life"), now = 1L, newId = ::newId)
        modes = SavedFocusModeStore.create(modes, "Hype", setOf("action"), now = 2L, newId = ::newId)

        assertEquals(listOf(0, 1), modes.map { it.order })
        assertEquals(listOf("Cozy night", "Hype"), modes.map { it.name })

        val roundTripped = SavedFocusModeStore.parse(SavedFocusModeStore.serialize(modes))
        assertEquals(modes, roundTripped)
    }

    @Test
    fun `create defaults to Match All with no exclude criteria`() {
        val modes = SavedFocusModeStore.create(emptyList(), "Mode", setOf("action", "comedy"), now = 1L, newId = ::newId)

        val mode = modes.single()
        assertTrue(mode.matchAll)
        assertTrue(mode.excludeGroups.isEmpty())
    }

    @Test
    fun `create accepts explicit include, exclude, and Match Any`() {
        val modes = SavedFocusModeStore.create(
            emptyList(),
            "Mode",
            includeGroups = setOf("action"),
            excludeGroups = setOf("horror"),
            matchAll = false,
            now = 1L,
            newId = ::newId,
        )

        val mode = modes.single()
        assertEquals(setOf("action"), mode.includeGroups)
        assertEquals(setOf("horror"), mode.excludeGroups)
        assertFalse(mode.matchAll)
    }

    @Test
    fun `create rejects a blank name as a no-op rather than silently trimming it`() {
        val modes = SavedFocusModeStore.create(emptyList(), "   ", setOf("action"), now = 1L, newId = ::newId)
        assertTrue(modes.isEmpty())
    }

    @Test
    fun `duplicate names are allowed, disambiguated by id`() {
        var modes = SavedFocusModeStore.create(emptyList(), "Focus", setOf("action"), now = 1L, newId = ::newId)
        modes = SavedFocusModeStore.create(modes, "Focus", setOf("comedy"), now = 2L, newId = ::newId)

        assertEquals(2, modes.size)
        assertEquals(setOf("Focus"), modes.map { it.name }.toSet())
        assertEquals(2, modes.map { it.id }.toSet().size, "ids must still be distinct")
    }

    @Test
    fun `rename updates the name and updatedAt without touching id, criteria, or order`() {
        val created = SavedFocusModeStore.create(emptyList(), "Old name", setOf("action"), now = 1L, newId = ::newId)

        val renamed = SavedFocusModeStore.rename(created, created.single().id, "New name", now = 5L)

        val mode = renamed.single()
        assertEquals("New name", mode.name)
        assertEquals(5L, mode.updatedAt)
        assertEquals(created.single().id, mode.id)
        assertEquals(created.single().includeGroups, mode.includeGroups)
        assertEquals(created.single().order, mode.order)
    }

    @Test
    fun `rename to a blank name is a no-op`() {
        val created = SavedFocusModeStore.create(emptyList(), "Kept", setOf("action"), now = 1L, newId = ::newId)

        val renamed = SavedFocusModeStore.rename(created, created.single().id, "  ", now = 5L)

        assertEquals("Kept", renamed.single().name)
    }

    @Test
    fun `updateCriteria replaces include, exclude, and matchAll and bumps updatedAt`() {
        val created = SavedFocusModeStore.create(emptyList(), "Mode", setOf("action"), now = 1L, newId = ::newId)

        val updated = SavedFocusModeStore.updateCriteria(
            created,
            created.single().id,
            includeGroups = setOf("comedy", "drama"),
            excludeGroups = setOf("horror"),
            matchAll = false,
            now = 9L,
        )

        val mode = updated.single()
        assertEquals(setOf("comedy", "drama"), mode.includeGroups)
        assertEquals(setOf("horror"), mode.excludeGroups)
        assertFalse(mode.matchAll)
        assertEquals(9L, mode.updatedAt)
    }

    @Test
    fun `delete removes exactly the targeted mode`() {
        var modes = SavedFocusModeStore.create(emptyList(), "Keep", setOf("action"), now = 1L, newId = ::newId)
        modes = SavedFocusModeStore.create(modes, "Remove", setOf("comedy"), now = 2L, newId = ::newId)
        val toRemove = modes.first { it.name == "Remove" }.id

        val afterDelete = SavedFocusModeStore.delete(modes, toRemove)

        assertEquals(listOf("Keep"), afterDelete.map { it.name })
    }

    @Test
    fun `reorder rewrites order to match the given id sequence`() {
        var modes = SavedFocusModeStore.create(emptyList(), "A", setOf("action"), now = 1L, newId = ::newId)
        modes = SavedFocusModeStore.create(modes, "B", setOf("comedy"), now = 2L, newId = ::newId)
        modes = SavedFocusModeStore.create(modes, "C", setOf("drama"), now = 3L, newId = ::newId)
        val (a, b, c) = modes

        val reordered = SavedFocusModeStore.reorder(modes, listOf(c.id, a.id, b.id))

        assertEquals(listOf("C", "A", "B"), reordered.sortedBy { it.order }.map { it.name })
        assertEquals(listOf(0, 1, 2), reordered.sortedBy { it.order }.map { it.order })
    }

    @Test
    fun `reorder appends ids not present in the given sequence after the reordered ones`() {
        var modes = SavedFocusModeStore.create(emptyList(), "A", setOf("action"), now = 1L, newId = ::newId)
        modes = SavedFocusModeStore.create(modes, "B", setOf("comedy"), now = 2L, newId = ::newId)
        val (a, b) = modes

        val reordered = SavedFocusModeStore.reorder(modes, listOf(b.id))

        assertEquals(listOf("B", "A"), reordered.sortedBy { it.order }.map { it.name })
    }

    @Test
    fun `resolveApply intersects both include and exclude with what is currently available, keeping matchAll`() {
        val mode = SavedFocusModeStore.create(
            emptyList(),
            "Mode",
            includeGroups = setOf("action", "comedy", "gone"),
            excludeGroups = setOf("horror", "vanished"),
            matchAll = false,
            now = 1L,
            newId = ::newId,
        ).single()

        val resolved = SavedFocusModeStore.resolveApply(mode, availableGroups = setOf("action", "comedy", "drama", "horror"))

        assertEquals(setOf("action", "comedy"), resolved.include)
        assertEquals(setOf("horror"), resolved.exclude)
        assertFalse(resolved.matchAll)
    }

    @Test
    fun `resolveApply returns empty include when every stored include group has become unavailable`() {
        val mode = SavedFocusModeStore.create(emptyList(), "Stale", setOf("obsolete-tag"), now = 1L, newId = ::newId).single()

        val resolved = SavedFocusModeStore.resolveApply(mode, availableGroups = setOf("action", "comedy"))

        assertTrue(resolved.include.isEmpty())
    }

    // KMK v0.8.21-fix5: R4/AUG-14 completion -- schema migration from the pre-existing v1 flat
    // `groups: Set<String>` shape.

    @Test
    fun `a legacy v1 envelope migrates cleanly -- never degrades to empty just because the version is old`() {
        val legacyJson = """{"version":1,"modes":[{"id":"a","name":"Cozy","groups":["comedy","drama"],"createdAt":1,"updatedAt":1,"order":0}]}"""

        val migrated = SavedFocusModeStore.parse(legacyJson)

        assertEquals(1, migrated.size)
        val mode = migrated.single()
        assertEquals("a", mode.id)
        assertEquals("Cozy", mode.name)
        assertEquals(setOf("comedy", "drama"), mode.includeGroups)
    }

    @Test
    fun `legacy groups become Include, Match Any on migration -- the closest equivalent to the old rerank's any-match surfacing`() {
        val legacyJson = """{"version":1,"modes":[{"id":"a","name":"Cozy","groups":["comedy"],"createdAt":1,"updatedAt":1,"order":0}]}"""

        val mode = SavedFocusModeStore.parse(legacyJson).single()

        assertFalse(mode.matchAll, "legacy semantics were an implicit match-any, never match-all")
        assertTrue(mode.excludeGroups.isEmpty(), "v1 had no exclude concept")
    }

    @Test
    fun `a migrated legacy mode round-trips through serialize as the current version`() {
        val legacyJson = """{"version":1,"modes":[{"id":"a","name":"Cozy","groups":["comedy"],"createdAt":1,"updatedAt":1,"order":0}]}"""

        val migrated = SavedFocusModeStore.parse(legacyJson)
        val reserialized = SavedFocusModeStore.serialize(migrated)
        val reparsed = SavedFocusModeStore.parse(reserialized)

        assertEquals(migrated, reparsed)
        assertTrue(reserialized.contains("\"version\":${SavedFocusModeStore.CURRENT_VERSION}"))
    }

    @Test
    fun `current-version data with matchAll and excludeGroups round-trips exactly`() {
        val modes = SavedFocusModeStore.create(
            emptyList(),
            "Mode",
            includeGroups = setOf("action"),
            excludeGroups = setOf("horror"),
            matchAll = true,
            now = 1L,
            newId = ::newId,
        )

        val roundTripped = SavedFocusModeStore.parse(SavedFocusModeStore.serialize(modes))

        assertEquals(modes, roundTripped)
    }
}
// KMK <--
