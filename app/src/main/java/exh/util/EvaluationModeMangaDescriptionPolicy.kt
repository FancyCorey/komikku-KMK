package exh.util

/**
 * Removes source identity from manga descriptions at the final display boundary used by the
 * manga detail screen. Source metadata can be embedded in a description by an upstream source,
 * so obfuscating only the separate source label is insufficient for public evidence.
 */
object EvaluationModeMangaDescriptionPolicy {
    private val sourceAnnotationPattern = Regex(
        """(?im)(\(?\s*source\s*:\s*)[^)\r\n]+(\s*\)?)""",
    )

    fun displayDescription(
        description: String?,
        evaluationModeEnabled: Boolean,
        sourceLabel: String,
        rawSourceNames: Collection<String>,
    ): String? {
        if (!evaluationModeEnabled || description.isNullOrEmpty()) return description

        var sanitized = sourceAnnotationPattern.replace(description) { match ->
            "${match.groupValues[1]}$sourceLabel${match.groupValues[2]}"
        }
        rawSourceNames
            .asSequence()
            .filter { it.isNotBlank() && !it.equals(sourceLabel, ignoreCase = true) }
            .distinct()
            .sortedByDescending(String::length)
            .forEach { rawSourceName ->
                sanitized = sanitized.replace(rawSourceName, sourceLabel, ignoreCase = true)
            }
        return sanitized
    }
}
