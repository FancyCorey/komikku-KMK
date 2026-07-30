package exh.recs.settings

// KMK v0.8.14-fix1 -->
/**
 * Pure model + compact serializer for a read-only "Preview For You" snapshot shown in Recommendation
 * Settings' "For You sources" screen.
 *
 * Replaces the v0.8.13-fix1 status-only preview, which never showed actual manga -- only source order/
 * enabled/liked/disliked/last-status. This snapshot stores just enough display-safe data (row headings,
 * manga covers/titles, source/url identity) to render a visual approximation of the real For You page
 * without ever touching a source, the network, or install/update logic. It is built once, after a real
 * For You refresh finishes with at least one visible row (see
 * `BrowsePersonalRecommendationsScreenModel.load()`), and persisted to a single string preference
 * ([eu.kanade.domain.source.service.SourcePreferences.recommendationForYouPreviewSnapshot]). Settings
 * reads it back through [RecommendationsSettingsScreenModel] so this settings screen never depends on
 * the live For You screen model.
 *
 * Deliberately NOT stored: descriptions, genres, chapter/page data, scores, or any field beyond what a
 * plain cover + title row needs. Delimited-string format (same style as
 * [exh.recs.RecommendationSourceRunStatusStore]) using ASCII separator control characters
 * (``/``/``/``) that never appear in real manga titles, rather than JSON --
 * there is no JSON serializer already wired into this preference-store style elsewhere in `exh.recs`.
 */
data class RecommendationForYouPreviewSnapshot(
    val capturedAt: Long,
    val rows: List<RecommendationForYouPreviewRow>,
)

enum class RecommendationForYouPreviewRowType {
    TOP_PICKS,
    SOURCE,
}

data class RecommendationForYouPreviewRow(
    /** "top_picks" for the Top Picks row, or the source id (as a string) for a source row. */
    val rowKey: String,
    val rowType: RecommendationForYouPreviewRowType,
    val title: String,
    val subtitle: String,
    val mangas: List<RecommendationForYouPreviewManga>,
)

data class RecommendationForYouPreviewManga(
    val mangaId: Long,
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
)

object RecommendationForYouPreviewSnapshotStore {
    /** Defensive cap on rows persisted -- Top Picks + a bounded number of source rows. */
    const val MAX_ROWS = 21

    /** Defensive cap on manga cards persisted per row, independent of the configured result budget. */
    const val MAX_MANGA_PER_ROW = 30

    private const val ROW_SEP = '' // Record Separator
    private const val ROW_FIELD_SEP = '' // Unit Separator
    private const val MANGA_SEP = '' // Group Separator
    private const val MANGA_FIELD_SEP = '' // File Separator

    fun serialize(snapshot: RecommendationForYouPreviewSnapshot): String {
        val rowsPart = snapshot.rows.take(MAX_ROWS).joinToString(ROW_SEP.toString()) { row ->
            val mangasPart = row.mangas.take(MAX_MANGA_PER_ROW).joinToString(MANGA_SEP.toString()) { m ->
                listOf(
                    m.mangaId.toString(),
                    m.sourceId.toString(),
                    escape(m.url),
                    escape(m.title),
                    escape(m.thumbnailUrl.orEmpty()),
                ).joinToString(MANGA_FIELD_SEP.toString())
            }
            listOf(escape(row.rowKey), row.rowType.name, escape(row.title), escape(row.subtitle), mangasPart)
                .joinToString(ROW_FIELD_SEP.toString())
        }
        return "${snapshot.capturedAt}$ROW_SEP$rowsPart"
    }

    fun parse(value: String): RecommendationForYouPreviewSnapshot? {
        if (value.isBlank()) return null
        return runCatching {
            val firstSep = value.indexOf(ROW_SEP)
            if (firstSep == -1) return null
            val capturedAt = value.substring(0, firstSep).toLong()
            val rest = value.substring(firstSep + 1)
            if (rest.isBlank()) return RecommendationForYouPreviewSnapshot(capturedAt, emptyList())

            val rows = rest.split(ROW_SEP).mapNotNull { rowStr ->
                if (rowStr.isBlank()) return@mapNotNull null
                val fields = rowStr.split(ROW_FIELD_SEP)
                if (fields.size < 5) return@mapNotNull null
                val rowKey = escape(fields[0], reverse = true)
                val rowType = runCatching { RecommendationForYouPreviewRowType.valueOf(fields[1]) }.getOrNull()
                    ?: return@mapNotNull null
                val title = escape(fields[2], reverse = true)
                val subtitle = escape(fields[3], reverse = true)
                val mangasPart = fields[4]
                val mangas = if (mangasPart.isBlank()) {
                    emptyList()
                } else {
                    mangasPart.split(MANGA_SEP).mapNotNull { mangaStr ->
                        val mFields = mangaStr.split(MANGA_FIELD_SEP)
                        if (mFields.size < 5) return@mapNotNull null
                        runCatching {
                            RecommendationForYouPreviewManga(
                                mangaId = mFields[0].toLong(),
                                sourceId = mFields[1].toLong(),
                                url = escape(mFields[2], reverse = true),
                                title = escape(mFields[3], reverse = true),
                                thumbnailUrl = escape(mFields[4], reverse = true).ifBlank { null },
                            )
                        }.getOrNull()
                    }.take(MAX_MANGA_PER_ROW)
                }
                RecommendationForYouPreviewRow(rowKey, rowType, title, subtitle, mangas)
            }.take(MAX_ROWS)

            RecommendationForYouPreviewSnapshot(capturedAt, rows)
        }.getOrNull()
    }

    // Control characters used as separators can't legally appear in manga titles/urls in practice, but
    // strip them defensively on the way in/out anyway so malformed input can never desync parsing.
    private fun escape(s: String, reverse: Boolean = false): String {
        if (reverse) return s
        return s.filterNot { it == ROW_SEP || it == ROW_FIELD_SEP || it == MANGA_SEP || it == MANGA_FIELD_SEP }
    }
}
// KMK <--
