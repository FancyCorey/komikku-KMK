package tachiyomi.domain.taste.model

import java.util.Locale

// KMK -->
/**
 * Centralized tag normalization shared by scoring, filter mapping, and cache key generation.
 *
 * Steps:
 * 1. Lowercase using ROOT locale.
 * 2. Replace non-alphanumeric characters with spaces.
 * 3. Collapse multiple spaces.
 * 4. Trim.
 *
 * Example: "Sci-Fi" -> "sci fi", "Girls Love" -> "girls love"
 */
fun String.normalizeTag(): String = lowercase(Locale.ROOT)
    .replace(nonWordRegex, " ")
    .replace(multiSpaceRegex, " ")
    .trim()

private val nonWordRegex = Regex("[^\\p{L}\\p{N}]+")
private val multiSpaceRegex = Regex(" +")
// KMK <--
