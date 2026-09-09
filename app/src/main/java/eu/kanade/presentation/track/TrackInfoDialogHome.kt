package eu.kanade.presentation.track

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.model.LocalTrackingActionPolicy
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.presentation.track.components.TrackLogoIcon
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.ui.manga.track.TrackerEntry
import eu.kanade.tachiyomi.util.lang.toLocalDate
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.format.DateTimeFormatter

@Composable
fun TrackInfoDialogHome(
    entries: List<TrackerEntry>,
    dateFormat: DateTimeFormatter,
    onStatusClick: (TrackItem) -> Unit,
    onChapterClick: (TrackItem) -> Unit,
    onScoreClick: (TrackItem) -> Unit,
    onStartDateEdit: (TrackItem) -> Unit,
    onEndDateEdit: (TrackItem) -> Unit,
    onNewSearch: (TrackItem) -> Unit,
    onOpenInBrowser: (TrackItem) -> Unit,
    onRemoved: (TrackItem) -> Unit,
    onCopyLink: (TrackItem) -> Unit,
    onTogglePrivate: (TrackItem) -> Unit,
    // KMK v0.8.21-fix2: Local is a peer entry in this same list, opened by a single click that
    // always shows the local status/list workflow -- never an automatic write. See TrackerEntry's
    // doc for why Local isn't just another TrackItem.
    onLocalClick: () -> Unit,
    onLocalDetailsClick: () -> Unit = {},
    onLocalChapterClick: () -> Unit = {},
    onLocalReconcileClick: () -> Unit = {},
    onLocalOtherVersionsClick: () -> Unit = {},
    localDateFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE,
) {
    Column(
        modifier = Modifier
            .animateContentSize()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .windowInsetsPadding(WindowInsets.systemBars),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        entries.forEach { entry ->
            when (entry) {
                is TrackerEntry.Local -> LocalTrackerInfoItem(
                    work = entry.work,
                    dateFormat = localDateFormat,
                    onClick = onLocalClick,
                    onDetailsClick = onLocalDetailsClick,
                    onChapterClick = onLocalChapterClick,
                    onReconcileClick = onLocalReconcileClick,
                    onOtherVersionsClick = onLocalOtherVersionsClick,
                )
                is TrackerEntry.External -> ExternalTrackerRow(
                    item = entry.item,
                    dateFormat = dateFormat,
                    onStatusClick = onStatusClick,
                    onChapterClick = onChapterClick,
                    onScoreClick = onScoreClick,
                    onStartDateEdit = onStartDateEdit,
                    onEndDateEdit = onEndDateEdit,
                    onNewSearch = onNewSearch,
                    onOpenInBrowser = onOpenInBrowser,
                    onRemoved = onRemoved,
                    onCopyLink = onCopyLink,
                    onTogglePrivate = onTogglePrivate,
                )
            }
        }
    }
}

@Composable
private fun ExternalTrackerRow(
    item: TrackItem,
    dateFormat: DateTimeFormatter,
    onStatusClick: (TrackItem) -> Unit,
    onChapterClick: (TrackItem) -> Unit,
    onScoreClick: (TrackItem) -> Unit,
    onStartDateEdit: (TrackItem) -> Unit,
    onEndDateEdit: (TrackItem) -> Unit,
    onNewSearch: (TrackItem) -> Unit,
    onOpenInBrowser: (TrackItem) -> Unit,
    onRemoved: (TrackItem) -> Unit,
    onCopyLink: (TrackItem) -> Unit,
    onTogglePrivate: (TrackItem) -> Unit,
) {
    if (item.track != null) {
        val supportsScoring = item.tracker.getScoreList().isNotEmpty()
        val supportsReadingDates = item.tracker.supportsReadingDates
        val supportsPrivate = item.tracker.supportsPrivateTracking
        TrackInfoItem(
            title = item.track.title,
            tracker = item.tracker,
            status = item.tracker.getStatus(item.track.status),
            onStatusClick = { onStatusClick(item) },
            chapters = "${item.track.lastChapterRead.toInt()}".let {
                val totalChapters = item.track.totalChapters
                if (totalChapters > 0) {
                    // Add known total chapter count
                    "$it / $totalChapters"
                } else {
                    it
                }
            },
            onChaptersClick = { onChapterClick(item) },
            score = item.tracker.displayScore(item.track)
                .takeIf { supportsScoring && item.track.score != 0.0 },
            onScoreClick = { onScoreClick(item) }
                .takeIf { supportsScoring },
            startDate = remember(item.track.startDate) { dateFormat.format(item.track.startDate.toLocalDate()) }
                .takeIf { supportsReadingDates && item.track.startDate != 0L },
            onStartDateClick = { onStartDateEdit(item) } // TODO
                .takeIf { supportsReadingDates },
            endDate = dateFormat.format(item.track.finishDate.toLocalDate())
                .takeIf { supportsReadingDates && item.track.finishDate != 0L },
            onEndDateClick = { onEndDateEdit(item) }
                .takeIf { supportsReadingDates },
            onNewSearch = { onNewSearch(item) },
            onOpenInBrowser = { onOpenInBrowser(item) },
            onRemoved = { onRemoved(item) },
            onCopyLink = { onCopyLink(item) },
            private = item.track.private,
            onTogglePrivate = { onTogglePrivate(item) }
                .takeIf { supportsPrivate },
        )
    } else {
        TrackInfoItemEmpty(
            tracker = item.tracker,
            onNewSearch = { onNewSearch(item) },
        )
    }
}

// KMK v0.8.21-fix2 -->
/**
 * The Local tracker's row in [TrackInfoDialogHome], rendered as a peer to every external tracker
 * row below them. A single tap always opens the local status/list workflow via [onClick] --
 * whether or not a [LocalTrackedWork] already exists -- so Local behaves like tapping any other
 * unconfigured/configured tracker instead of writing a status automatically.
 */
@Composable
private fun LocalTrackerInfoItem(
    work: LocalTrackedWork?,
    dateFormat: DateTimeFormatter,
    onClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onChapterClick: () -> Unit,
    onReconcileClick: () -> Unit,
    onOtherVersionsClick: () -> Unit,
) {
    if (work == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            TextButton(
                onClick = onClick,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f),
            ) {
                Text(stringResource(KMR.strings.track_locally))
            }
        }
        return
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onClick)
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(
                        KMR.strings.local_tracking_status_label,
                        stringResource(LocalTrackingActionPolicy.statusLabel(work.status)),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(KMR.strings.local_tracking_reconcile),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TrackDetailsPanel(
            status = stringResource(LocalTrackingActionPolicy.statusLabel(work.status)),
            onStatusClick = onClick,
            chapters = work.lastChapterNumber?.let { it.toInt().toString() },
            onChaptersClick = work.lastChapterNumber?.let { { onChapterClick() } },
            score = work.score?.toString(),
            onScoreClick = onDetailsClick,
            startDate = work.startDate?.let { dateFormat.format(it.toLocalDate()) },
            onStartDateClick = onDetailsClick,
            endDate = work.finishDate?.let { dateFormat.format(it.toLocalDate()) },
            onEndDateClick = onDetailsClick,
            showScore = true,
            showDates = true,
        )
        TextButton(
            onClick = onReconcileClick,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(KMR.strings.local_tracking_reconcile))
        }
        TextButton(
            onClick = onOtherVersionsClick,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(KMR.strings.local_tracking_other_versions))
        }
    }
}

@Composable
private fun TrackDetailsPanel(
    status: String,
    onStatusClick: () -> Unit,
    chapters: String?,
    onChaptersClick: (() -> Unit)?,
    score: String?,
    onScoreClick: () -> Unit,
    startDate: String?,
    onStartDateClick: (() -> Unit)?,
    endDate: String?,
    onEndDateClick: (() -> Unit)?,
    showScore: Boolean,
    showDates: Boolean,
) {
    Box(
        modifier = Modifier
            .padding(top = 12.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(8.dp)
            .clip(RoundedCornerShape(6.dp)),
    ) {
        Column {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                TrackDetailsItem(
                    modifier = Modifier.weight(1f),
                    text = status,
                    onClick = onStatusClick,
                )
                VerticalDivider()
                TrackDetailsItem(
                    modifier = Modifier.weight(1f),
                    text = chapters,
                    placeholder = "-",
                    onClick = onChaptersClick,
                )
                if (showScore) {
                    VerticalDivider()
                    TrackDetailsItem(
                        modifier = Modifier.weight(1f),
                        text = score,
                        placeholder = stringResource(MR.strings.score),
                        onClick = onScoreClick,
                    )
                }
            }
            if (showDates) {
                HorizontalDivider()
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    TrackDetailsItem(
                        modifier = Modifier.weight(1F),
                        text = startDate,
                        placeholder = stringResource(MR.strings.track_started_reading_date),
                        onClick = onStartDateClick,
                    )
                    VerticalDivider()
                    TrackDetailsItem(
                        modifier = Modifier.weight(1F),
                        text = endDate,
                        placeholder = stringResource(MR.strings.track_finished_reading_date),
                        onClick = onEndDateClick,
                    )
                }
            }
        }
    }
}
// KMK <--

@Composable
private fun TrackInfoItem(
    title: String,
    tracker: Tracker,
    status: StringResource?,
    onStatusClick: () -> Unit,
    chapters: String,
    onChaptersClick: () -> Unit,
    score: String?,
    onScoreClick: (() -> Unit)?,
    startDate: String?,
    onStartDateClick: (() -> Unit)?,
    endDate: String?,
    onEndDateClick: (() -> Unit)?,
    onNewSearch: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onRemoved: () -> Unit,
    onCopyLink: () -> Unit,
    private: Boolean,
    onTogglePrivate: (() -> Unit)?,
) {
    val context = LocalContext.current
    val changeMatchLabel = stringResource(MR.strings.track_find_different_match)
    val copyTitleLabel = stringResource(MR.strings.action_copy_tracking_title)
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BadgedBox(
                badge = {
                    if (private) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.absoluteOffset(x = (-5).dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VisibilityOff,
                                contentDescription = stringResource(MR.strings.tracked_privately),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                },
            ) {
                TrackLogoIcon(
                    tracker = tracker,
                    onClick = onOpenInBrowser,
                    onLongClick = onCopyLink,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .combinedClickable(
                        onClick = onNewSearch,
                        onClickLabel = changeMatchLabel,
                        onLongClick = {
                            context.copyToClipboard(title, title)
                        },
                        onLongClickLabel = copyTitleLabel,
                        role = Role.Button,
                    )
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = changeMatchLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VerticalDivider()
            TrackInfoItemMenu(
                onOpenInBrowser = onOpenInBrowser,
                onRemoved = onRemoved,
                onCopyLink = onCopyLink,
                private = private,
                onTogglePrivate = onTogglePrivate,
            )
        }

        TrackDetailsPanel(
            status = status?.let { stringResource(it) } ?: "",
            onStatusClick = onStatusClick,
            chapters = chapters,
            onChaptersClick = onChaptersClick,
            score = score,
            onScoreClick = onScoreClick ?: {},
            startDate = startDate,
            onStartDateClick = onStartDateClick,
            endDate = endDate,
            onEndDateClick = onEndDateClick,
            showScore = onScoreClick != null,
            showDates = onStartDateClick != null && onEndDateClick != null,
        )
    }
}

private const val UNSET_TEXT_ALPHA = 0.5F

@Composable
private fun TrackDetailsItem(
    text: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .fillMaxHeight()
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text ?: placeholder,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (text == null) UNSET_TEXT_ALPHA else 1f),
        )
    }
}

@Composable
private fun TrackInfoItemEmpty(
    tracker: Tracker,
    onNewSearch: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackLogoIcon(tracker)
        TextButton(
            onClick = onNewSearch,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        ) {
            Text(text = stringResource(MR.strings.add_tracking))
        }
    }
}

@Composable
private fun TrackInfoItemMenu(
    onOpenInBrowser: () -> Unit,
    onRemoved: () -> Unit,
    onCopyLink: () -> Unit,
    private: Boolean,
    onTogglePrivate: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(MR.strings.label_more),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.action_open_in_browser)) },
                onClick = {
                    onOpenInBrowser()
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.action_copy_link)) },
                onClick = {
                    onCopyLink()
                    expanded = false
                },
            )
            if (onTogglePrivate != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (private) {
                                    MR.strings.action_toggle_private_off
                                } else {
                                    MR.strings.action_toggle_private_on
                                },
                            ),
                        )
                    },
                    onClick = {
                        onTogglePrivate()
                        expanded = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.action_remove)) },
                onClick = {
                    onRemoved()
                    expanded = false
                },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TrackInfoDialogHomePreviews(
    @PreviewParameter(TrackInfoDialogHomePreviewProvider::class)
    content: @Composable () -> Unit,
) {
    TachiyomiPreviewTheme {
        Surface {
            content()
        }
    }
}
