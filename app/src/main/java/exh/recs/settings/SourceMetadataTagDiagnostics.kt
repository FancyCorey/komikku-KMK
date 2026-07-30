package exh.recs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import exh.util.EvaluationModeFormatter
import exh.util.rememberEvaluationModeEnabled
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationMetadataConfidence
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Selects the newest evaluation row for each source. Older rows are retained for Source
 * Evaluation history, but showing them here would make one source appear multiple times and make
 * the diagnostics harder to interpret.
 */
internal object SourceMetadataTagDiagnosticsPolicy {
    fun latestPerSource(evaluations: List<SourceEvaluation>): List<SourceEvaluation> = evaluations
        .groupBy { it.sourceId ?: it.evaluationKey }
        .values
        .mapNotNull { rows -> rows.maxByOrNull { it.evaluatedAt } }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.sourceName })
}

/** Read-only, count-based diagnostics for the metadata and tag evidence observed per source. */
@Composable
internal fun SourceMetadataTagDiagnosticsContent(
    evaluations: List<SourceEvaluation>,
) {
    val evaluationModeEnabled = rememberEvaluationModeEnabled()
    val rows = remember(evaluations) { SourceMetadataTagDiagnosticsPolicy.latestPerSource(evaluations) }
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        if (rows.isEmpty()) {
            Text(
                text = stringResource(KMR.strings.rec_source_metadata_tag_diagnostics_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Text(
            text = stringResource(KMR.strings.rec_source_metadata_tag_diagnostics_summary, rows.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        rows.forEach { evaluation ->
            val key = evaluation.evaluationKey
            val sourceLabel = if (evaluationModeEnabled) {
                evaluation.sourceId?.let(EvaluationModeFormatter::sourceLabel)
                    ?: EvaluationModeFormatter.sourceLabel(key)
            } else {
                evaluation.sourceName
            }
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
                        evaluation.sampleCount,
                        evaluation.metadataCandidateCount,
                        evaluation.positiveCandidateCount,
                        evaluation.blockedCandidateCount,
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
                if (isExpanded) {
                    Text(
                        text = stringResource(
                            KMR.strings.rec_source_metadata_tag_diagnostics_metadata,
                            evaluation.metadataCandidateCount,
                            evaluation.sampleCount,
                            confidenceLabel,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(
                            KMR.strings.rec_source_metadata_tag_diagnostics_enrichment,
                            evaluation.detailEnrichmentSuccessCount,
                            evaluation.detailEnrichmentAttemptCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(
                            KMR.strings.rec_source_metadata_tag_diagnostics_tags,
                            evaluation.preferredTagMatchCount,
                            evaluation.blockedTagMatchCount,
                            evaluation.positiveCandidateCount,
                            evaluation.negativeCandidateCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
