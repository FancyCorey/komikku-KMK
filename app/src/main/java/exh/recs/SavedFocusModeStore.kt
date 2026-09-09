package exh.recs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// KMK v0.8.21-fix2: AUG-14 slice 3 -->
/**
 * A named, saved For You focus criteria set. Purely a UI-preference concept -- applying one is
 * indistinguishable from manually selecting the same criteria in the focus dialog (never mutates
 * durable taste, exposure history, or Not-Interested state).
 *
 * KMK v0.8.21-fix5: R4/AUG-14 completion -- direct product correction (2026-08-25). Replaces the
 * old flat `groups: Set<String>` (an implicit "match any" selection) with the same include/
 * exclude/Match-All-or-Any criteria model [RecommendationFocusPolicy.FocusCriteria] uses.
 * [includeGroups] keeps the wire name "groups" (`@SerialName`) so a legacy v1 blob's JSON shape is
 * still exactly what [Envelope]'s pre-migration decode expects -- see [parse].
 */
@Serializable
data class SavedFocusMode(
    val id: String,
    val name: String,
    @SerialName("groups") val includeGroups: Set<String>,
    val excludeGroups: Set<String> = emptySet(),
    /** True = Match All (default), false = Match Any. Governs [includeGroups] only. */
    val matchAll: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val order: Int,
)

/**
 * Parses, serializes, and mutates the [SavedFocusMode] list stored in
 * [eu.kanade.domain.source.service.SourcePreferences.savedFocusModes]. This is a plain,
 * preference-backed store -- no database table, no domain/repository layer -- matching every
 * other UI-only preference in this "focus" feature area (`RecommendationFocusPolicy`,
 * `RecommendationFocusPresentationPolicy`).
 */
internal object SavedFocusModeStore {

    /**
     * v1: flat `groups: Set<String>` only (implicit Match Any semantics -- the old policy
     * unioned/reranked by any-group coverage, never required every group). v2 (current): adds
     * [SavedFocusMode.excludeGroups] and [SavedFocusMode.matchAll].
     */
    const val CURRENT_VERSION = 2

    @Serializable
    private data class Envelope(
        val version: Int = CURRENT_VERSION,
        val modes: List<SavedFocusMode> = emptyList(),
    )

    // v1 legacy shape, decoded only when the envelope's version is below CURRENT_VERSION so a
    // pre-migration blob is never mis-decoded against fields it doesn't have.
    @Serializable
    private data class LegacySavedFocusModeV1(
        val id: String,
        val name: String,
        val groups: Set<String> = emptySet(),
        val createdAt: Long,
        val updatedAt: Long,
        val order: Int,
    )

    @Serializable
    private data class LegacyEnvelopeV1(
        val version: Int = 1,
        val modes: List<LegacySavedFocusModeV1> = emptyList(),
    )

    /** Probes only the envelope's `version` field, tolerating any shape for `modes`. */
    @Serializable
    private data class VersionProbe(val version: Int = 1)

    // encodeDefaults = true is essential here: without it, a field equal to its default value
    // (e.g. a fresh v2 envelope's `version = CURRENT_VERSION`, or a Match-All mode's
    // `matchAll = true`) is silently omitted from the JSON entirely, which would make a
    // freshly-serialized v2 envelope indistinguishable from a version-less/legacy one on the next
    // parse() -- exactly the bug this store's own versioned-migration contract must not have.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Never throws -- a malformed stored value degrades to an empty list, never a crash. A legacy
     * v1 envelope upgrades cleanly to the current shape (see [migrateFromV1]); it is never
     * silently discarded to an empty list just because its version is old.
     */
    fun parse(raw: String): List<SavedFocusMode> {
        if (raw.isBlank()) return emptyList()
        val version = try {
            json.decodeFromString<VersionProbe>(raw).version
        } catch (e: Exception) {
            return emptyList()
        }
        return try {
            if (version >= CURRENT_VERSION) {
                json.decodeFromString<Envelope>(raw).modes.sortedBy { it.order }
            } else {
                migrateFromV1(json.decodeFromString<LegacyEnvelopeV1>(raw))
            }
        } catch (e: Exception) {
            // Neither the current nor the legacy shape decoded -- truly malformed/corrupt data.
            emptyList()
        }
    }

    /**
     * Legacy groups become Include, Match Any -- the closest equivalent to the old rerank
     * algorithm's semantics, which surfaced (never dropped) any candidate matching at least one
     * selected group, i.e. an implicit "match any" over the selection. No excludeGroups existed
     * pre-migration, so it is empty.
     */
    private fun migrateFromV1(legacy: LegacyEnvelopeV1): List<SavedFocusMode> =
        legacy.modes.map { old ->
            SavedFocusMode(
                id = old.id,
                name = old.name,
                includeGroups = old.groups,
                excludeGroups = emptySet(),
                matchAll = false,
                createdAt = old.createdAt,
                updatedAt = old.updatedAt,
                order = old.order,
            )
        }.sortedBy { it.order }

    fun serialize(modes: List<SavedFocusMode>): String =
        json.encodeToString(Envelope(version = CURRENT_VERSION, modes = modes))

    /** Rejects a blank/whitespace-only name (no-op, returns [current] unchanged) rather than silently trimming it to something the user didn't type. */
    fun create(
        current: List<SavedFocusMode>,
        name: String,
        includeGroups: Set<String>,
        excludeGroups: Set<String> = emptySet(),
        matchAll: Boolean = true,
        now: Long,
        newId: () -> String,
    ): List<SavedFocusMode> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return current
        val nextOrder = (current.maxOfOrNull { it.order } ?: -1) + 1
        return current + SavedFocusMode(
            id = newId(),
            name = trimmed,
            includeGroups = includeGroups,
            excludeGroups = excludeGroups,
            matchAll = matchAll,
            createdAt = now,
            updatedAt = now,
            order = nextOrder,
        )
    }

    fun rename(current: List<SavedFocusMode>, id: String, newName: String, now: Long): List<SavedFocusMode> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return current
        return current.map { if (it.id == id) it.copy(name = trimmed, updatedAt = now) else it }
    }

    /** Replaces a mode's include/exclude/Match-All-or-Any criteria and bumps updatedAt. */
    fun updateCriteria(
        current: List<SavedFocusMode>,
        id: String,
        includeGroups: Set<String>,
        excludeGroups: Set<String>,
        matchAll: Boolean,
        now: Long,
    ): List<SavedFocusMode> =
        current.map {
            if (it.id == id) {
                it.copy(includeGroups = includeGroups, excludeGroups = excludeGroups, matchAll = matchAll, updatedAt = now)
            } else {
                it
            }
        }

    fun delete(current: List<SavedFocusMode>, id: String): List<SavedFocusMode> =
        current.filterNot { it.id == id }

    /** Rewrites every mode's [SavedFocusMode.order] to its index in [orderedIds]. Ids not present in [orderedIds] keep their relative order, appended after. */
    fun reorder(current: List<SavedFocusMode>, orderedIds: List<String>): List<SavedFocusMode> {
        val byId = current.associateBy { it.id }
        val reordered = orderedIds.mapNotNull { byId[it] }
        val remaining = current.filter { it.id !in orderedIds }
        return (reordered + remaining).mapIndexed { index, mode -> mode.copy(order = index) }
    }

    /**
     * Resolves [mode]'s criteria against [availableGroups] (the live focus-dialog options at
     * apply time). A group the mode remembers that no longer exists is silently dropped from the
     * result -- the caller (UI layer) is responsible for noticing when this yields a
     * smaller-than-saved include/exclude set and surfacing that truthfully.
     */
    fun resolveApply(
        mode: SavedFocusMode,
        availableGroups: Set<String>,
    ): RecommendationFocusPolicy.FocusCriteria = RecommendationFocusPolicy.FocusCriteria(
        include = mode.includeGroups intersect availableGroups,
        exclude = mode.excludeGroups intersect availableGroups,
        matchAll = mode.matchAll,
    )
}
// KMK <--
