package tachiyomi.domain.taste.model

// KMK -->
data class TagTaste(
    val normalizedTag: String,
    val displayName: String,
    val preference: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class TagPreference(val value: Int) {
    BLOCK(-2),
    DISLIKE(-1),
    PREFER(1),
    ;

    companion object {
        fun fromValue(value: Int): TagPreference? = entries.find { it.value == value }
    }
}
// KMK <--
