package exh.recs.sourceprefs

// KMK -->
/**
 * Serialization and mutation helpers for liked/disliked recommendation source key sets.
 *
 * Key format:
 *   installed source:  "i|<sourceId>"
 *   available source:  "a|<signatureHash>|<pkgName>|<sourceId>"  (sourceId optional — omit for extension-level)
 *   available ext:     "a|<signatureHash>|<pkgName>"
 *
 * Both liked and disliked sets use the same key format. A key cannot be in both sets simultaneously
 * — like removes from disliked, dislike removes from liked, reset removes from both.
 */
object RecommendationSourcePreferenceStore {

    private const val SEP = ";"
    private const val FIELD_SEP = "|"
    private const val PREFIX_INSTALLED = "i"
    private const val PREFIX_AVAILABLE = "a"

    fun parse(raw: String): Set<String> =
        if (raw.isBlank()) {
            emptySet()
        } else {
            raw.split(SEP).filter { it.isNotBlank() }.toSet()
        }

    fun serialize(keys: Set<String>): String =
        keys.filter { it.isNotBlank() }.joinToString(SEP)

    fun installedKey(sourceId: Long): String = "$PREFIX_INSTALLED$FIELD_SEP$sourceId"

    fun availableKey(signatureHash: String, pkgName: String, sourceId: Long?): String =
        if (sourceId != null) {
            "$PREFIX_AVAILABLE$FIELD_SEP$signatureHash$FIELD_SEP$pkgName$FIELD_SEP$sourceId"
        } else {
            "$PREFIX_AVAILABLE$FIELD_SEP$signatureHash$FIELD_SEP$pkgName"
        }

    /**
     * Returns source IDs of all installed-source keys in the given set.
     * Used to build the effective disabled-source set for For You.
     */
    fun installedSourceIds(keys: Set<String>): Set<Long> =
        keys.mapNotNull { key ->
            if (!key.startsWith("$PREFIX_INSTALLED$FIELD_SEP")) return@mapNotNull null
            key.removePrefix("$PREFIX_INSTALLED$FIELD_SEP").toLongOrNull()
        }.toSet()

    /** Returns (newLiked, newDisliked) after setting this key to LIKE. */
    fun like(liked: Set<String>, disliked: Set<String>, key: String): Pair<Set<String>, Set<String>> =
        (liked + key) to (disliked - key)

    /** Returns (newLiked, newDisliked) after setting this key to DISLIKE. */
    fun dislike(liked: Set<String>, disliked: Set<String>, key: String): Pair<Set<String>, Set<String>> =
        (liked - key) to (disliked + key)

    /** Returns (newLiked, newDisliked) after resetting this key to NEUTRAL. */
    fun reset(liked: Set<String>, disliked: Set<String>, key: String): Pair<Set<String>, Set<String>> =
        (liked - key) to (disliked - key)
}
// KMK <--
