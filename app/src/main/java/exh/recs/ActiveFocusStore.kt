package exh.recs

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persists the currently applied For You focus separately from taste and named saved modes. */
internal object ActiveFocusStore {

    @Serializable
    private data class Record(
        val includeGroups: Set<String> = emptySet(),
        val excludeGroups: Set<String> = emptySet(),
        val matchAll: Boolean = true,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun parse(raw: String): RecommendationFocusPolicy.FocusCriteria {
        if (raw.isBlank()) return RecommendationFocusPolicy.FocusCriteria.EMPTY
        return try {
            json.decodeFromString<Record>(raw).let {
                RecommendationFocusPolicy.FocusCriteria(
                    include = it.includeGroups,
                    exclude = it.excludeGroups,
                    matchAll = it.matchAll,
                )
            }
        } catch (_: Exception) {
            RecommendationFocusPolicy.FocusCriteria.EMPTY
        }
    }

    fun serialize(criteria: RecommendationFocusPolicy.FocusCriteria): String =
        json.encodeToString(
            Record(
                includeGroups = criteria.include,
                excludeGroups = criteria.exclude,
                matchAll = criteria.matchAll,
            ),
        )
}
