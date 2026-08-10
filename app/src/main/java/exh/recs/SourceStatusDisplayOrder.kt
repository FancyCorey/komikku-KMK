package exh.recs

// KMK -->
data class SourceDisplayOrderInput(
    val sourceId: Long,
    val priorityIndex: Int,
    val hasMatches: Boolean,
    val isDisliked: Boolean,
)

object SourceStatusDisplayOrder {

    enum class Group(val sortKey: Int) {
        HAS_MATCHES(0),
        NO_MATCHES(1),
        DISLIKED(2),
    }

    fun group(input: SourceDisplayOrderInput): Group = when {
        input.isDisliked -> Group.DISLIKED
        input.hasMatches -> Group.HAS_MATCHES
        else -> Group.NO_MATCHES
    }

    fun sort(inputs: List<SourceDisplayOrderInput>): List<SourceDisplayOrderInput> =
        inputs.sortedWith(
            compareBy(
                { group(it).sortKey },
                { it.priorityIndex },
                { it.sourceId },
            ),
        )
}
// KMK <--
