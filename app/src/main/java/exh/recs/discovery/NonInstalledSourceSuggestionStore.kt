package exh.recs.discovery

// KMK -->
/** Persistence helpers for dismissed non-installed suggestion keys. */
object NonInstalledSourceSuggestionStore {
    fun parse(raw: String): Set<String> {
        if (raw.isBlank()) return emptySet()
        return raw.split(";").filter { it.isNotBlank() }.toSet()
    }

    fun serialize(keys: Set<String>): String =
        keys.filter { it.isNotBlank() }.joinToString(";")

    fun dismiss(current: Set<String>, key: String): Set<String> = current + key
}
// KMK <--
