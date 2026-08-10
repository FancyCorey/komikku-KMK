package eu.kanade.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// KMK -->
/**
 * Stable identity for each empty-state illustration this app can render. Deliberately a closed,
 * exhaustive set (not an open sealed hierarchy with per-screen subtypes) so [KmkEmptyStateIllustration]
 * is the single place that maps a semantic state to artwork -- see that composable's own doc for why.
 */
enum class KmkEmptyStateArtwork {
    FOR_YOU,
    SOURCE_EVALUATION,
    ACTION_HISTORY,
    FIND_BEST_VERSION_UNAVAILABLE,
    READER_SCHEDULE,
}

/**
 * Renders the theme-aware illustration for [artwork]. Decorative by contract -- callers must not
 * attach a content description here; the surrounding state message (already rendered by
 * [tachiyomi.presentation.core.screens.EmptyScreen] or the caller's own layout) is the accessible
 * explanation of the state.
 *
 * Every path in every vector below is baked with an arbitrary, non-prohibited placeholder color
 * (`0xFF6750A4`, Material purple, chosen only because it's *not* white/black/gray/red/green/cyan) that
 * is never actually seen: [Icon] always recolors through [tint] via `BlendMode.SrcIn`, which replaces
 * every visible pixel's color while preserving its alpha -- confirmed against this codebase's own
 * `LogoHeader.kt` (`Icon(painterResource(R.drawable.ic_komikku), tint = MaterialTheme.colorScheme.onSurface)`)
 * and `BrowseIcons.kt`'s `Image(..., colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error))`.
 * This is why the translucent star/triangle fills below (`fillAlpha = 0.14f`/`0.12f`) still read as a
 * translucent version of [tint] at runtime rather than a fixed color: SrcIn keeps the source alpha.
 *
 * [KmkEmptyStateArtwork.FOR_YOU] and [KmkEmptyStateArtwork.SOURCE_EVALUATION] are rendered through
 * isolated `Canvas` layers. For You needs opaque surface-colored card interiors so rear outlines do
 * not show through the front card; Source Evaluation needs boolean cutouts for the same reason.
 */
@Composable
fun KmkEmptyStateIllustration(
    artwork: KmkEmptyStateArtwork,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    if (artwork == KmkEmptyStateArtwork.FOR_YOU) {
        ForYouEmptyArt(tint = tint, modifier = modifier)
        return
    }
    if (artwork == KmkEmptyStateArtwork.SOURCE_EVALUATION) {
        SourceEvaluationEmptyArt(tint = tint, modifier = modifier)
        return
    }
    Icon(
        imageVector = kmkEmptyStateImageVector(artwork),
        contentDescription = null,
        tint = tint,
        modifier = modifier,
    )
}

/** `internal` (not `private`) so [KmkEmptyStateIllustrationMappingTest] can exercise it directly. */
internal fun kmkEmptyStateImageVector(artwork: KmkEmptyStateArtwork): ImageVector = when (artwork) {
    KmkEmptyStateArtwork.FOR_YOU -> KmkForYouImageVectorFallback
    KmkEmptyStateArtwork.ACTION_HISTORY -> KmkActionHistoryIllustration
    KmkEmptyStateArtwork.FIND_BEST_VERSION_UNAVAILABLE -> KmkFindBestVersionIllustration
    KmkEmptyStateArtwork.READER_SCHEDULE -> KmkReaderScheduleIllustration
    KmkEmptyStateArtwork.SOURCE_EVALUATION ->
        error("SOURCE_EVALUATION is rendered through SourceEvaluationEmptyArt, not an ImageVector")
}

/** Placeholder baked path color -- see [KmkEmptyStateIllustration]'s doc for why this is never visible. */
private val PlaceholderPathColor = SolidColor(Color(0xFF6750A4))

/**
 * Kept for the exhaustive artwork mapping contract and non-Compose callers. The rendered For You
 * surface uses [ForYouEmptyArt] so its card interiors can be opaque and theme-aware.
 */
private val KmkForYouImageVectorFallback: ImageVector by lazy {
    Builder(
        name = "KmkEmptyForYouFallback",
        defaultWidth = 96.dp,
        defaultHeight = 96.dp,
        viewportWidth = 256f,
        viewportHeight = 256f,
    ).apply {
        path(
            fill = null,
            stroke = PlaceholderPathColor,
            strokeLineWidth = 8f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(81f, 36f)
            lineTo(171f, 36f)
            lineTo(187f, 52f)
            lineTo(187f, 194f)
            lineTo(81f, 194f)
            close()
        }
    }.build()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, cx + r, cy)
    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, cx - r, cy)
    close()
}

/**
 * For You empty state: three opaque, fanned manga cards followed by the Love, Like, and Dislike
 * actions. The surface-colored interiors intentionally occlude every rear edge under the front
 * card, preserving the layered-card structure at every theme color.
 */
@Composable
private fun ForYouEmptyArt(tint: Color, modifier: Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize().padding(bottom = 18.dp)) {
            val scale = minOf(size.width / 256f, size.height / 210f)
            val offsetX = (size.width - (256f * scale)) / 2f
            val offsetY = (size.height - (210f * scale)) / 2f
            withTransform({
                translate(offsetX, offsetY)
                scale(scale, scale)
            }) {
                drawPath(ForYouEmptyArtGeometry.leftCard, color = surface, style = Fill)
                drawPath(ForYouEmptyArtGeometry.leftCard, color = tint, style = ForYouEmptyArtGeometry.cardStroke)
                drawPath(ForYouEmptyArtGeometry.rightCard, color = surface, style = Fill)
                drawPath(ForYouEmptyArtGeometry.rightCard, color = tint, style = ForYouEmptyArtGeometry.cardStroke)
                drawPath(ForYouEmptyArtGeometry.frontCard, color = surface, style = Fill)
                drawPath(ForYouEmptyArtGeometry.frontCard, color = tint, style = ForYouEmptyArtGeometry.cardStroke)
                drawPath(ForYouEmptyArtGeometry.foldedCorner, color = tint, style = ForYouEmptyArtGeometry.cardStroke)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Favorite, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Icon(Icons.Outlined.ThumbUp, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Icon(Icons.Outlined.ThumbDown, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        }
    }
}

private object ForYouEmptyArtGeometry {
    val cardStroke = Stroke(width = 8f, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
    val leftCard = Path().apply {
        moveTo(58f, 64f)
        lineTo(101f, 48f)
        lineTo(106f, 190f)
        lineTo(72f, 201f)
        close()
    }
    val rightCard = Path().apply {
        moveTo(155f, 48f)
        lineTo(198f, 64f)
        lineTo(184f, 201f)
        lineTo(150f, 190f)
        close()
    }
    val frontCard = Path().apply {
        moveTo(81f, 36f)
        lineTo(171f, 36f)
        lineTo(187f, 52f)
        lineTo(187f, 194f)
        lineTo(81f, 194f)
        close()
    }
    val foldedCorner = Path().apply {
        moveTo(171f, 36f)
        lineTo(171f, 52f)
        lineTo(187f, 52f)
    }
}

/**
 * Action History empty state: a standalone clock face plus a separate undo arrow -- deliberately no
 * connector line, partial ring, or orbit between them (both shapes' bounding boxes are non-overlapping:
 * the clock occupies roughly x=[98,222] and the arrow x=[38,84], keeping the symbols separate.
 */
private val KmkActionHistoryIllustration: ImageVector by lazy {
    Builder(
        name = "KmkEmptyActionHistory",
        defaultWidth = 96.dp,
        defaultHeight = 96.dp,
        viewportWidth = 256f,
        viewportHeight = 256f,
    ).apply {
        // Clock ring
        path(
            fill = null,
            stroke = PlaceholderPathColor,
            strokeLineWidth = 10f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            pathFillType = PathFillType.NonZero,
        ) {
            circle(160f, 132f, 62f)
        }
        // Clock hands
        path(
            fill = null,
            stroke = PlaceholderPathColor,
            strokeLineWidth = 9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(160f, 90f)
            lineTo(160f, 132f)
            lineTo(194f, 150f)
        }
        // Clock center dot
        path(
            fill = PlaceholderPathColor,
            stroke = null,
            strokeLineWidth = 0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            pathFillType = PathFillType.NonZero,
        ) {
            circle(160f, 132f, 7f)
        }
        // Undo arrow (shaft + arrowhead), standalone -- no connector to the clock
        path(
            fill = null,
            stroke = PlaceholderPathColor,
            strokeLineWidth = 10f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(84f, 84f)
            lineTo(38f, 84f)
            lineTo(56f, 66f)
            moveTo(38f, 84f)
            lineTo(56f, 102f)
        }
    }.build()
}

/**
 * Find Best Version unavailable state: two blank manga-page silhouettes (an open-book shape, page
 * interiors intentionally empty) with a small neutral warning triangle and exclamation mark.
 */
private val KmkFindBestVersionIllustration: ImageVector by lazy {
    Builder(
        name = "KmkEmptyFindBestVersion",
        defaultWidth = 96.dp,
        defaultHeight = 96.dp,
        viewportWidth = 256f,
        viewportHeight = 256f,
    ).apply {
        // Left page
        path(
            fill = null,
            stroke = PlaceholderPathColor,
            strokeLineWidth = 8f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(40f, 56f)
            lineTo(102f, 56f)
            curveTo(120f, 56f, 128f, 64f, 128f, 82f)
            lineTo(128f, 198f)
            curveTo(120f, 189f, 110f, 185f, 97f, 185f)
            lineTo(40f, 185f)
            lineTo(40f, 56f)
            close()
        }
        // Right page
        path(
            fill = null,
            stroke = PlaceholderPathColor,
            strokeLineWidth = 8f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(216f, 56f)
            lineTo(154f, 56f)
            curveTo(136f, 56f, 128f, 64f, 128f, 82f)
            lineTo(128f, 198f)
            curveTo(136f, 189f, 146f, 185f, 159f, 185f)
            lineTo(216f, 185f)
            lineTo(216f, 56f)
            close()
        }
        // Warning triangle
        path(
            fill = PlaceholderPathColor,
            fillAlpha = 0.12f,
            stroke = PlaceholderPathColor,
            strokeLineWidth = 7f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(128f, 89f)
            lineTo(96f, 147f)
            lineTo(160f, 147f)
            lineTo(128f, 89f)
            close()
        }
        // Exclamation mark
        path(
            fill = null,
            stroke = PlaceholderPathColor,
            strokeLineWidth = 7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(128f, 111f)
            lineTo(128f, 129f)
            moveTo(128f, 139f)
            lineTo(128f, 140f)
        }
    }.build()
}

/**
 * Reader Schedule empty state: a calendar outline with a small clock offset beside it, kept visually
 * separated (the calendar spans roughly x=[34,156]; the clock is centered at x=193, entirely to
 * the right of the calendar).
 */
private val KmkReaderScheduleIllustration: ImageVector by lazy {
    Builder(
        name = "KmkEmptyReaderSchedule",
        defaultWidth = 64.dp,
        defaultHeight = 64.dp,
        viewportWidth = 256f,
        viewportHeight = 256f,
    ).apply {
        // Calendar body
        path(
            fill = null,
            stroke = PlaceholderPathColor,
            strokeLineWidth = 9f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            pathFillType = PathFillType.NonZero,
        ) {
            val x = 34f
            val y = 54f
            val w = 122f
            val h = 137f
            val rx = 13f
            moveTo(x + rx, y)
            lineTo(x + w - rx, y)
            arcTo(rx, rx, 0f, isMoreThanHalf = false, isPositiveArc = true, x + w, y + rx)
            lineTo(x + w, y + h - rx)
            arcTo(rx, rx, 0f, isMoreThanHalf = false, isPositiveArc = true, x + w - rx, y + h)
            lineTo(x + rx, y + h)
            arcTo(rx, rx, 0f, isMoreThanHalf = false, isPositiveArc = true, x, y + h - rx)
            lineTo(x, y + rx)
            arcTo(rx, rx, 0f, isMoreThanHalf = false, isPositiveArc = true, x + rx, y)
            close()
        }
        // Header divider + two hanging tabs
        path(
            fill = null,
            stroke = PlaceholderPathColor,
            strokeLineWidth = 9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(34f, 94f)
            lineTo(156f, 94f)
            moveTo(65f, 39f)
            lineTo(65f, 70f)
            moveTo(126f, 39f)
            lineTo(126f, 70f)
        }
        // Clock ring
        path(
            fill = null,
            stroke = PlaceholderPathColor,
            strokeLineWidth = 9f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            pathFillType = PathFillType.NonZero,
        ) {
            circle(193f, 170f, 34f)
        }
        // Clock hands
        path(
            fill = null,
            stroke = PlaceholderPathColor,
            strokeLineWidth = 8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(193f, 150f)
            lineTo(193f, 170f)
            lineTo(208f, 179f)
        }
    }.build()
}
// KMK <--
