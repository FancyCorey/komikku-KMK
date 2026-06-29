package tachiyomi.domain.taste.model

// KMK -->
data class MangaTaste(
    val mangaId: Long,
    val source: Long,
    val url: String,
    val title: String,
    val rating: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class MangaRating(val value: Int) {
    DISLIKE(-1),
    LIKE(1),
    LOVE(2),
    ;

    companion object {
        fun fromValue(value: Int): MangaRating? = entries.find { it.value == value }
    }
}
// KMK <--
