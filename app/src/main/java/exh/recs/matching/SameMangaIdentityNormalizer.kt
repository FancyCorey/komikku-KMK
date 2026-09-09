package exh.recs.matching

// KMK --> A11.1
import java.text.Normalizer
import java.util.Locale

internal object SameMangaIdentityNormalizer {

    enum class PartMarkerKind {
        SEASON,
        PART,
        VOLUME,
        BOOK,
        COUR,
    }

    data class PartMarker(
        val kind: PartMarkerKind,
        val ordinal: Int,
    )

    data class NormalizedTitle(
        val canonicalBase: String,
        val compatibilityBase: String,
        val markers: Set<PartMarker>,
    )

    fun normalizeTitles(
        displayTitle: String,
        originalTitle: String,
        aliases: List<String>,
    ): List<NormalizedTitle> {
        return buildList {
            add(displayTitle)
            add(originalTitle)
            addAll(aliases)
        }
            .map(::normalizeTitle)
            .filter { it.canonicalBase.isNotBlank() || it.compatibilityBase.isNotBlank() }
            .distinct()
    }

    fun normalizeContributor(value: String?): String? {
        return value
            ?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) }
            ?.lowercase(Locale.ROOT)
            ?.replace(NON_ALPHANUMERIC, " ")
            ?.replace(WHITESPACE, " ")
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    fun normalizeGenre(value: String): String? {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(NON_ALPHANUMERIC, " ")
            .replace(WHITESPACE, " ")
            .trim()
            .takeIf(String::isNotBlank)
    }

    private fun normalizeTitle(raw: String): NormalizedTitle {
        val canonical = Normalizer.normalize(raw, Normalizer.Form.NFC).lowercase(Locale.ROOT)
        val compatibility = Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        val markers = extractMarkers(compatibility)
        return NormalizedTitle(
            canonicalBase = collapseTitle(PART_MARKER.replace(canonical, " ")),
            compatibilityBase = collapseTitle(PART_MARKER.replace(compatibility, " ")),
            markers = markers,
        )
    }

    private fun extractMarkers(value: String): Set<PartMarker> {
        return PART_MARKER.findAll(value)
            .mapNotNull { match ->
                val kind = when (match.groupValues[1].lowercase(Locale.ROOT)) {
                    "season" -> PartMarkerKind.SEASON
                    "part", "pt" -> PartMarkerKind.PART
                    "volume", "vol" -> PartMarkerKind.VOLUME
                    "book" -> PartMarkerKind.BOOK
                    "cour" -> PartMarkerKind.COUR
                    else -> return@mapNotNull null
                }
                parseOrdinal(match.groupValues[2])?.let { PartMarker(kind, it) }
            }
            .toSet()
    }

    private fun parseOrdinal(raw: String): Int? {
        raw.toIntOrNull()?.let { return it.takeIf { value -> value > 0 } }
        val roman = raw.uppercase(Locale.ROOT)
        if (!VALID_ROMAN.matches(roman)) return null
        var total = 0
        var previous = 0
        for (character in roman.reversed()) {
            val value = ROMAN_VALUES[character] ?: return null
            if (value < previous) total -= value else total += value
            previous = value
        }
        return total.takeIf { it > 0 }
    }

    private fun collapseTitle(value: String): String {
        return value
            .replace(NON_ALPHANUMERIC, " ")
            .replace(WHITESPACE, " ")
            .trim()
    }

    private val PART_MARKER = Regex(
        pattern = "(?:^|[\\s(\\[<{:_-])(season|part|pt|volume|vol|book|cour)\\s*([0-9]+|[ivxlcdm]+)(?=$|[\\s)\\]}>:_-])",
        option = RegexOption.IGNORE_CASE,
    )
    private val VALID_ROMAN = Regex("M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})")
    private val ROMAN_VALUES = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
    private val WHITESPACE = Regex("\\s+")
}
// KMK <--
