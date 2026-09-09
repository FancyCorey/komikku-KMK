package exh.recs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import exh.recs.evaluation.SourceEvaluationDisplayPolicy
import exh.util.EvaluationModeFormatter
import exh.util.rememberEvaluationModeEnabled
import kotlinx.serialization.json.Json
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationMetadataConfidence
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale

/**
 * Selects the newest evaluation row for each source. Older rows are retained for Source
 * Evaluation history, but showing them here would make one source appear multiple times and make
 * the diagnostics harder to interpret.
 */
internal object SourceMetadataTagDiagnosticsPolicy {
    enum class StatusFilter { ALL, CURRENT, OUTDATED, EXPIRED, METADATA_SPARSE, ERROR }

    enum class SortOrder { SOURCE, NEWEST, CONFIDENCE, ISSUES }

    data class Query(
        val text: String = "",
        val status: StatusFilter = StatusFilter.ALL,
        val sort: SortOrder = SortOrder.SOURCE,
    )

    data class Row(
        val evaluation: SourceEvaluation,
        val state: SourceEvaluationDisplayPolicy.SourceEvaluationDisplayState,
        val issueCount: Int,
    )

    fun latestPerSource(evaluations: List<SourceEvaluation>): List<SourceEvaluation> = evaluations
        .groupBy { it.sourceId ?: it.evaluationKey }
        .values
        .mapNotNull { rows -> rows.maxByOrNull { it.evaluatedAt } }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.sourceName })

    // KMK Final Evidence Closure 2026-07-30 -->
    /**
     * Extracted from [SourceMetadataTagDiagnosticsContent]'s row rendering so the Evaluation Mode
     * privacy branch is directly unit-testable, mirroring
     * [RecommendationSettingsSectionSummaries.sourcePrioritySummary]. Never returns
     * [SourceEvaluation.sourceName] when [evaluationModeEnabled] is true.
     */
    fun sourceLabelFor(evaluation: SourceEvaluation, evaluationModeEnabled: Boolean): String =
        if (evaluationModeEnabled) {
            evaluation.sourceId?.let(EvaluationModeFormatter::sourceLabel)
                ?: EvaluationModeFormatter.sourceLabel(evaluation.evaluationKey)
        } else {
            evaluation.sourceName
        }

    fun queryRows(
        evaluations: List<SourceEvaluation>,
        query: Query,
        evaluationModeEnabled: Boolean,
        now: Long,
    ): List<Row> {
        val normalizedQuery = query.text.trim().lowercase(Locale.ROOT)
        return latestPerSource(evaluations)
            .map { evaluation ->
                Row(
                    evaluation = evaluation,
                    state = SourceEvaluationDisplayPolicy.state(evaluation, now),
                    issueCount = evaluation.errorCount + evaluation.blockedCandidateCount,
                )
            }
            .filter { row ->
                val stateMatches = query.status.matches(row.state)
                val textMatches = normalizedQuery.isBlank() || searchableText(
                    row.evaluation,
                    evaluationModeEnabled,
                ).contains(normalizedQuery)
                stateMatches && textMatches
            }
            .sortedWith(query.sort.comparator(evaluationModeEnabled))
    }

    private fun searchableText(evaluation: SourceEvaluation, evaluationModeEnabled: Boolean): String = buildList {
        add(sourceLabelFor(evaluation, evaluationModeEnabled))
        add(evaluation.lang)
        add(evaluation.verdict.name)
        add(evaluation.catalogueMetadataConfidence.name)
        add(evaluation.errorCount.toString())
        add(evaluation.blockedCandidateCount.toString())
        if (!evaluationModeEnabled) {
            add(evaluation.extensionName)
            add(evaluation.repoName.orEmpty())
            add(structuredPayload(evaluation.sampledTitlesJson))
            add(structuredPayload(evaluation.sampledTagsJson))
        }
    }.joinToString(" ").lowercase(Locale.ROOT)

    private fun structuredPayload(payload: String?): String = payload
        ?.let { runCatching { Json.parseToJsonElement(it).toString() }.getOrDefault("") }
        .orEmpty()

    private fun StatusFilter.matches(state: SourceEvaluationDisplayPolicy.SourceEvaluationDisplayState): Boolean =
        when (this) {
            StatusFilter.ALL -> true
            StatusFilter.CURRENT -> state == SourceEvaluationDisplayPolicy.SourceEvaluationDisplayState.CURRENT
            StatusFilter.OUTDATED -> state == SourceEvaluationDisplayPolicy.SourceEvaluationDisplayState.OUTDATED_VERSION
            StatusFilter.EXPIRED -> state == SourceEvaluationDisplayPolicy.SourceEvaluationDisplayState.EXPIRED
            StatusFilter.METADATA_SPARSE -> state == SourceEvaluationDisplayPolicy.SourceEvaluationDisplayState.METADATA_SPARSE
            StatusFilter.ERROR -> state == SourceEvaluationDisplayPolicy.SourceEvaluationDisplayState.ERROR
        }

    private fun SortOrder.comparator(evaluationModeEnabled: Boolean): Comparator<Row> = when (this) {
        SortOrder.SOURCE -> compareBy<Row, String>(String.CASE_INSENSITIVE_ORDER) { row ->
            sourceLabelFor(row.evaluation, evaluationModeEnabled)
        }.thenBy { it.evaluation.evaluationKey }
        SortOrder.NEWEST -> compareByDescending<Row> { it.evaluation.evaluatedAt }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { row -> sourceLabelFor(row.evaluation, evaluationModeEnabled) }
        SortOrder.CONFIDENCE -> compareByDescending<Row> {
            when (it.evaluation.catalogueMetadataConfidence) {
                SourceEvaluationMetadataConfidence.HIGH -> 3
                SourceEvaluationMetadataConfidence.MODERATE -> 2
                SourceEvaluationMetadataConfidence.LOW -> 1
                SourceEvaluationMetadataConfidence.UNKNOWN -> 0
            }
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { row -> sourceLabelFor(row.evaluation, evaluationModeEnabled) }
        SortOrder.ISSUES -> compareByDescending<Row> { it.issueCount }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { row -> sourceLabelFor(row.evaluation, evaluationModeEnabled) }
    }
    // KMK <--
}

/** Searchable, privacy-safe diagnostics for metadata and tag evidence observed per source. */
@Composable
internal fun SourceMetadataTagDiagnosticsContent(
    evaluations: List<SourceEvaluation>,
) {
    val evaluationModeEnabled = rememberEvaluationModeEnabled()
    val allRows = remember(evaluations) { SourceMetadataTagDiagnosticsPolicy.latestPerSource(evaluations) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf(SourceMetadataTagDiagnosticsPolicy.StatusFilter.ALL) }
    var sortOrder by rememberSaveable { mutableStateOf(SourceMetadataTagDiagnosticsPolicy.SortOrder.SOURCE) }
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val rows = SourceMetadataTagDiagnosticsPolicy.queryRows(
        evaluations = allRows,
        query = SourceMetadataTagDiagnosticsPolicy.Query(searchText, statusFilter, sortOrder),
        evaluationModeEnabled = evaluationModeEnabled,
        now = System.currentTimeMillis(),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        if (allRows.isEmpty()) {
            Text(
                text = stringResource(KMR.strings.rec_source_metadata_tag_diagnostics_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(KMR.strings.rec_sources_to_try_search_hint)) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
            TextButton(onClick = {
                statusFilter = statusFilter.next()
            }) {
                Text(stringResource(statusFilter.resource()))
            }
            TextButton(onClick = {
                sortOrder = sortOrder.next()
            }) {
                Text(stringResource(KMR.strings.source_evaluation_sort_label) + " " + stringResource(sortOrder.resource()))
            }
            TextButton(onClick = {
                searchText = ""
                statusFilter = SourceMetadataTagDiagnosticsPolicy.StatusFilter.ALL
                sortOrder = SourceMetadataTagDiagnosticsPolicy.SortOrder.SOURCE
            }) {
                Text(stringResource(KMR.strings.source_evaluation_reset))
            }
        }

        if (rows.isEmpty()) {
            Text(
                text = stringResource(KMR.strings.source_evaluation_rec_quality_no_matches),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Text(
            text = pluralStringResource(KMR.plurals.rec_source_metadata_tag_diagnostics_summary, count = rows.size, rows.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        rows.forEach { row ->
            val evaluation = row.evaluation
            val key = evaluation.evaluationKey
            val sourceLabel = SourceMetadataTagDiagnosticsPolicy.sourceLabelFor(evaluation, evaluationModeEnabled)
            val confidenceLabel = stringResource(
                when (evaluation.catalogueMetadataConfidence) {
                    SourceEvaluationMetadataConfidence.HIGH -> KMR.strings.source_evaluation_metadata_confidence_high
                    SourceEvaluationMetadataConfidence.MODERATE -> KMR.strings.source_evaluation_metadata_confidence_moderate
                    SourceEvaluationMetadataConfidence.LOW -> KMR.strings.source_evaluation_metadata_confidence_low
                    SourceEvaluationMetadataConfidence.UNKNOWN -> KMR.strings.source_evaluation_metadata_confidence_unknown
                },
            )
            val isExpanded = expandedKey == key
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = sourceLabel, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(
                        KMR.strings.rec_source_metadata_tag_diagnostics_row_summary,
                        pluralStringResource(
                            KMR.plurals.rec_source_diagnostics_sample_fragment,
                            count = evaluation.sampleCount,
                            evaluation.sampleCount,
                        ),
                        pluralStringResource(
                            KMR.plurals.rec_source_diagnostics_metadata_fragment,
                            count = evaluation.metadataCandidateCount,
                            evaluation.metadataCandidateCount,
                        ),
                        pluralStringResource(
                            KMR.plurals.rec_source_diagnostics_positive_match_fragment,
                            count = evaluation.positiveCandidateCount,
                            evaluation.positiveCandidateCount,
                        ),
                        pluralStringResource(
                            KMR.plurals.rec_source_diagnostics_blocked_candidate_fragment,
                            count = evaluation.blockedCandidateCount,
                            evaluation.blockedCandidateCount,
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { expandedKey = if (isExpanded) null else key },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                ) {
                    Text(
                        stringResource(
                            if (isExpanded) {
                                KMR.strings.rec_source_metadata_tag_diagnostics_hide_details
                            } else {
                                KMR.strings.rec_source_metadata_tag_diagnostics_show_details
                            },
                        ),
                    )
                }
                // KMK v0.8.21-fix2: the per-row "Open source evaluation" action removed -- it was
                // unconditional (SourceMetadataTagDiagnosticsPolicy.availableActions() always
                // returned the same single action regardless of the row) and navigated to the
                // exact same generic SourceEvaluationScreen destination every time, carrying no
                // source-specific context at all. A confirmed usability defect: repeated, identical,
                // full-row action beneath every source. The single top-level Source Evaluation entry
                // in Recommendation Settings remains the one route to that screen.
                if (isExpanded) {
                    Text(
                        text = pluralStringResource(KMR.plurals.rec_source_metadata_tag_diagnostics_metadata, count = evaluation.sampleCount, evaluation.metadataCandidateCount, evaluation.sampleCount, confidenceLabel),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = pluralStringResource(KMR.plurals.rec_source_metadata_tag_diagnostics_enrichment, count = evaluation.detailEnrichmentAttemptCount, evaluation.detailEnrichmentSuccessCount, evaluation.detailEnrichmentAttemptCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(
                            KMR.strings.rec_source_metadata_tag_diagnostics_tags,
                            pluralStringResource(
                                KMR.plurals.rec_source_diagnostics_preferred_match_fragment,
                                count = evaluation.preferredTagMatchCount,
                                evaluation.preferredTagMatchCount,
                            ),
                            pluralStringResource(
                                KMR.plurals.rec_source_diagnostics_blocked_match_fragment,
                                count = evaluation.blockedTagMatchCount,
                                evaluation.blockedTagMatchCount,
                            ),
                            pluralStringResource(
                                KMR.plurals.rec_source_diagnostics_positive_candidate_fragment,
                                count = evaluation.positiveCandidateCount,
                                evaluation.positiveCandidateCount,
                            ),
                            pluralStringResource(
                                KMR.plurals.rec_source_diagnostics_negative_candidate_fragment,
                                count = evaluation.negativeCandidateCount,
                                evaluation.negativeCandidateCount,
                            ),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun SourceMetadataTagDiagnosticsPolicy.StatusFilter.next(): SourceMetadataTagDiagnosticsPolicy.StatusFilter {
    val values = SourceMetadataTagDiagnosticsPolicy.StatusFilter.entries
    return values[(ordinal + 1) % values.size]
}

private fun SourceMetadataTagDiagnosticsPolicy.SortOrder.next(): SourceMetadataTagDiagnosticsPolicy.SortOrder {
    val values = SourceMetadataTagDiagnosticsPolicy.SortOrder.entries
    return values[(ordinal + 1) % values.size]
}

private fun SourceMetadataTagDiagnosticsPolicy.StatusFilter.resource() = when (this) {
    SourceMetadataTagDiagnosticsPolicy.StatusFilter.ALL -> KMR.strings.rec_source_metadata_tag_diagnostics_filter_all
    SourceMetadataTagDiagnosticsPolicy.StatusFilter.CURRENT -> KMR.strings.rec_source_metadata_tag_diagnostics_filter_current
    SourceMetadataTagDiagnosticsPolicy.StatusFilter.OUTDATED -> KMR.strings.rec_source_metadata_tag_diagnostics_filter_outdated
    SourceMetadataTagDiagnosticsPolicy.StatusFilter.EXPIRED -> KMR.strings.rec_source_metadata_tag_diagnostics_filter_expired
    SourceMetadataTagDiagnosticsPolicy.StatusFilter.METADATA_SPARSE -> KMR.strings.rec_source_metadata_tag_diagnostics_filter_sparse
    SourceMetadataTagDiagnosticsPolicy.StatusFilter.ERROR -> KMR.strings.rec_source_metadata_tag_diagnostics_filter_error
}

private fun SourceMetadataTagDiagnosticsPolicy.SortOrder.resource() = when (this) {
    SourceMetadataTagDiagnosticsPolicy.SortOrder.SOURCE -> KMR.strings.rec_source_metadata_tag_diagnostics_sort_source
    SourceMetadataTagDiagnosticsPolicy.SortOrder.NEWEST -> KMR.strings.rec_source_metadata_tag_diagnostics_sort_newest
    SourceMetadataTagDiagnosticsPolicy.SortOrder.CONFIDENCE -> KMR.strings.rec_source_metadata_tag_diagnostics_sort_confidence
    SourceMetadataTagDiagnosticsPolicy.SortOrder.ISSUES -> KMR.strings.rec_source_metadata_tag_diagnostics_sort_issues
}
