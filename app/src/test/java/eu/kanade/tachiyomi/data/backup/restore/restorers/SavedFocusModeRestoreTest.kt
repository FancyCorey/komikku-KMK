package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BackupSavedFocusMode
import exh.recs.SavedFocusMode
import exh.recs.SavedFocusModeStore
import exh.util.FakePreferenceStore
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// KMK v0.8.21-fix2: AUG-14 slice 3 -->
/**
 * [TasteRestorer] has a very large constructor-injected dependency graph (11 params, most with
 * no relevance to this one feature), so rather than instantiating it directly this mirrors the
 * exact merge-by-id/newest-updatedAt-wins sequence [TasteRestorer.restoreSavedFocusModes] uses --
 * matching the established pattern for this codebase's other large-constructor classes (e.g.
 * `MangaScreenModelTasteJournalTest`).
 *
 * KMK v0.8.21-fix5: R4/AUG-14 completion -- direct product correction (2026-08-25). Updated for
 * [SavedFocusMode]'s include/exclude/Match-All-or-Any schema; mirrors
 * [TasteRestorer.restoreSavedFocusModes] exactly, including the new excludeGroups/matchAll fields.
 */
class SavedFocusModeRestoreTest {

    private lateinit var sourcePreferences: SourcePreferences

    @BeforeEach
    fun setUp() {
        sourcePreferences = SourcePreferences(FakePreferenceStore())
    }

    /** Mirrors [TasteRestorer.restoreSavedFocusModes]'s exact merge logic. */
    private fun restoreSavedFocusModes(backupModes: List<BackupSavedFocusMode>) {
        if (backupModes.isEmpty()) return
        val existing = SavedFocusModeStore.parse(sourcePreferences.savedFocusModes().get())
        val existingById = existing.associateBy { it.id }
        val merged = existing.associateBy { it.id }.toMutableMap()
        for (backup in backupModes) {
            val current = existingById[backup.id]
            if (current == null || backup.updatedAt > current.updatedAt) {
                merged[backup.id] = SavedFocusMode(
                    id = backup.id,
                    name = backup.name,
                    includeGroups = backup.groups.toSet(),
                    excludeGroups = backup.excludeGroups.toSet(),
                    matchAll = backup.matchAll,
                    createdAt = backup.createdAt,
                    updatedAt = backup.updatedAt,
                    order = backup.order,
                )
            }
        }
        sourcePreferences.savedFocusModes().set(SavedFocusModeStore.serialize(merged.values.sortedBy { it.order }))
    }

    private fun storedModes() = SavedFocusModeStore.parse(sourcePreferences.savedFocusModes().get())

    @Test
    fun `a restore with no existing local modes adds every backup mode`() = runTest {
        restoreSavedFocusModes(
            listOf(
                BackupSavedFocusMode(id = "a", name = "Cozy", groups = listOf("comedy"), createdAt = 1, updatedAt = 1, order = 0),
                BackupSavedFocusMode(id = "b", name = "Hype", groups = listOf("action"), createdAt = 2, updatedAt = 2, order = 1),
            ),
        )

        assertEquals(setOf("a", "b"), storedModes().map { it.id }.toSet())
    }

    @Test
    fun `a colliding id keeps whichever side has the newer updatedAt -- backup wins when newer`() = runTest {
        sourcePreferences.savedFocusModes().set(
            SavedFocusModeStore.serialize(
                listOf(SavedFocusMode(id = "a", name = "Old local name", includeGroups = setOf("drama"), createdAt = 1, updatedAt = 1, order = 0)),
            ),
        )

        restoreSavedFocusModes(
            listOf(BackupSavedFocusMode(id = "a", name = "Newer backup name", groups = listOf("action"), createdAt = 1, updatedAt = 5, order = 0)),
        )

        val mode = storedModes().single()
        assertEquals("Newer backup name", mode.name)
        assertEquals(setOf("action"), mode.includeGroups)
    }

    @Test
    fun `a colliding id keeps the local side when it is newer than the backup`() = runTest {
        sourcePreferences.savedFocusModes().set(
            SavedFocusModeStore.serialize(
                listOf(SavedFocusMode(id = "a", name = "Newer local name", includeGroups = setOf("drama"), createdAt = 1, updatedAt = 9, order = 0)),
            ),
        )

        restoreSavedFocusModes(
            listOf(BackupSavedFocusMode(id = "a", name = "Older backup name", groups = listOf("action"), createdAt = 1, updatedAt = 2, order = 0)),
        )

        assertEquals("Newer local name", storedModes().single().name)
    }

    @Test
    fun `duplicate names across restored and existing modes are never treated as a conflict`() = runTest {
        sourcePreferences.savedFocusModes().set(
            SavedFocusModeStore.serialize(
                listOf(SavedFocusMode(id = "a", name = "Focus", includeGroups = setOf("drama"), createdAt = 1, updatedAt = 1, order = 0)),
            ),
        )

        restoreSavedFocusModes(
            listOf(BackupSavedFocusMode(id = "b", name = "Focus", groups = listOf("action"), createdAt = 2, updatedAt = 2, order = 1)),
        )

        assertEquals(2, storedModes().size, "distinct ids with the same name must both survive")
    }

    @Test
    fun `restoring an empty backup list leaves existing local modes untouched`() = runTest {
        sourcePreferences.savedFocusModes().set(
            SavedFocusModeStore.serialize(
                listOf(SavedFocusMode(id = "a", name = "Kept", includeGroups = setOf("drama"), createdAt = 1, updatedAt = 1, order = 0)),
            ),
        )

        restoreSavedFocusModes(emptyList())

        assertEquals(listOf("Kept"), storedModes().map { it.name })
    }

    @Test
    fun `restoring excludeGroups and matchAll round-trips them onto the merged local mode`() = runTest {
        restoreSavedFocusModes(
            listOf(
                BackupSavedFocusMode(
                    id = "a",
                    name = "Cozy",
                    groups = listOf("comedy"),
                    excludeGroups = listOf("horror"),
                    matchAll = true,
                    createdAt = 1,
                    updatedAt = 1,
                    order = 0,
                ),
            ),
        )

        val mode = storedModes().single()
        assertEquals(setOf("horror"), mode.excludeGroups)
        assertTrue(mode.matchAll)
    }

    @Test
    fun `restoring an old-format backup (no excludeGroups, no matchAll) defaults to empty exclude and Match Any`() = runTest {
        // Simulates a backup written before ProtoNumber 7/8 existed -- the deserialized
        // BackupSavedFocusMode falls back to its own field defaults (empty excludeGroups,
        // matchAll = false), matching SavedFocusModeStore's own v1 migration rule.
        restoreSavedFocusModes(
            listOf(BackupSavedFocusMode(id = "a", name = "Old backup", groups = listOf("comedy"), createdAt = 1, updatedAt = 1, order = 0)),
        )

        val mode = storedModes().single()
        assertTrue(mode.excludeGroups.isEmpty())
        assertFalse(mode.matchAll)
    }
}
// KMK <--
