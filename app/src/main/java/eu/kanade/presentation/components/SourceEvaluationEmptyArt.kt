package eu.kanade.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale

// KMK -->
/**
 * Source Evaluation empty state: a magnifying glass crossing three overlapping, generic source-card
 * silhouettes. Android `VectorDrawable` cannot represent the required geometric cutouts, so this
 * illustration is drawn with Compose's canvas APIs instead.
 *
 * This is intentionally **not** an `ImageVector` like the other four illustrations in
 * [KmkEmptyStateIllustration]. The original SVG cuts each rear card's stroke wherever a card drawn on
 * top of it (or the magnifier circle) overlaps, so "overlapping source-card interiors must remain
 * empty; lower card lines must not show through the front cards or magnifier." A cutout shape can
 * extend beyond the card whose line it's trimming (e.g. the magnifier circle overlaps empty background
 * as well as card edges) -- so naively stacking the cutout as one more even-odd subpath in a static
 * `ImageVector` path would incorrectly paint part of that cutout's *own* interior wherever it falls
 * outside the card it's meant to trim, since even-odd fill parity is computed globally across all
 * subpaths, not intersected with the specific band being cut. A static path cannot express "subtract
 * B from A only where they actually overlap" -- that is a genuine geometric boolean operation.
 *
 * `androidx.compose.ui.graphics.Path.op(a, b, PathOperation.Difference)` performs that real boolean
 * subtraction (backed by the platform graphics engine), providing an isolated Compose drawing layer
 * with explicit geometric clearing/clipping behavior. The three
 * card "stroke band" paths (see [strokeBandPath]) and the cutout shapes are computed once via
 * [remember] (pure geometry, independent of [tint] and of the composable's eventual pixel size) and
 * drawn scaled to fit [modifier]'s box. The rear card has the middle card, the front card, and the
 * magnifier all cut out of it; the middle
 * card has the front card and the magnifier cut out; the front card only has the magnifier cut out; the
 * magnifier ring and its handle are drawn last, uncut.
 */
@Composable
fun SourceEvaluationEmptyArt(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val geometry = remember { SourceEvaluationArtGeometry.build() }
    Canvas(modifier = modifier) {
        val scaleX = size.width / SourceEvaluationArtGeometry.VIEWPORT
        val scaleY = size.height / SourceEvaluationArtGeometry.VIEWPORT
        scale(scaleX, scaleY, pivot = Offset.Zero) {
            drawPath(geometry.rearCard, color = tint)
            drawPath(geometry.middleCard, color = tint)
            drawPath(geometry.frontCard, color = tint)
            drawPath(geometry.magnifierRing, color = tint)
            drawLine(
                color = tint,
                start = geometry.handleStart,
                end = geometry.handleEnd,
                strokeWidth = SourceEvaluationArtGeometry.HANDLE_STROKE_WIDTH,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Pure geometry for [SourceEvaluationEmptyArt] -- kept in its own object (rather than inline in the
 * composable) so [SourceEvaluationArtGeometryTest] can assert the boolean-subtraction structure (every
 * card's cutout list) without needing a Compose UI test harness.
 */
internal object SourceEvaluationArtGeometry {
    const val VIEWPORT = 256f
    const val HANDLE_STROKE_WIDTH = 12f

    private val rearRect = CardRect(x = 30f, y = 83f, w = 101f, h = 65f, r = 10f)
    private val middleRect = CardRect(x = 48f, y = 64f, w = 101f, h = 65f, r = 10f)
    private val frontRect = CardRect(x = 66f, y = 45f, w = 101f, h = 65f, r = 10f)
    private const val CARD_STROKE_WIDTH = 8f
    private const val MAGNIFIER_CX = 157f
    private const val MAGNIFIER_CY = 126f
    private const val MAGNIFIER_R = 43f
    private const val MAGNIFIER_STROKE_WIDTH = 9f
    val handleStart = Offset(188f, 157f)
    val handleEnd = Offset(225f, 194f)

    data class CardRect(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float)

    data class Result(
        val rearCard: Path,
        val middleCard: Path,
        val frontCard: Path,
        val magnifierRing: Path,
        val handleStart: Offset,
        val handleEnd: Offset,
    )

    fun build(): Result {
        val middleFill = roundRectFillPath(middleRect)
        val frontFill = roundRectFillPath(frontRect)
        val magnifierFill = ovalFillPath(MAGNIFIER_CX, MAGNIFIER_CY, MAGNIFIER_R)

        val rearCard = strokeBandPath(rearRect, CARD_STROKE_WIDTH)
            .minus(middleFill)
            .minus(frontFill)
            .minus(magnifierFill)
        val middleCard = strokeBandPath(middleRect, CARD_STROKE_WIDTH)
            .minus(frontFill)
            .minus(magnifierFill)
        val frontCard = strokeBandPath(frontRect, CARD_STROKE_WIDTH)
            .minus(magnifierFill)
        val magnifierRing = circleStrokeBandPath(MAGNIFIER_CX, MAGNIFIER_CY, MAGNIFIER_R, MAGNIFIER_STROKE_WIDTH)

        return Result(rearCard, middleCard, frontCard, magnifierRing, handleStart, handleEnd)
    }

    private fun Path.minus(other: Path): Path = Path().apply { op(this@minus, other, PathOperation.Difference) }

    private fun roundRectFillPath(rect: CardRect): Path = Path().apply {
        addRoundRect(RoundRect(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, CornerRadius(rect.r, rect.r)))
    }

    private fun ovalFillPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
        addOval(Rect(cx - r, cy - r, cx + r, cy + r))
    }

    /** A filled rounded-rect "ring" the same width as an SVG stroke -- outer minus inner, offset by half the stroke. */
    private fun strokeBandPath(rect: CardRect, strokeWidth: Float): Path {
        val half = strokeWidth / 2f
        val outer = roundRectFillPath(rect.copy(x = rect.x - half, y = rect.y - half, w = rect.w + strokeWidth, h = rect.h + strokeWidth, r = rect.r + half))
        val inner = roundRectFillPath(
            rect.copy(
                x = rect.x + half,
                y = rect.y + half,
                w = (rect.w - strokeWidth).coerceAtLeast(0f),
                h = (rect.h - strokeWidth).coerceAtLeast(0f),
                r = (rect.r - half).coerceAtLeast(0f),
            ),
        )
        return outer.minus(inner)
    }

    private fun circleStrokeBandPath(cx: Float, cy: Float, r: Float, strokeWidth: Float): Path {
        val half = strokeWidth / 2f
        val outer = ovalFillPath(cx, cy, r + half)
        val inner = ovalFillPath(cx, cy, (r - half).coerceAtLeast(0f))
        return outer.minus(inner)
    }
}
// KMK <--
