package exh.recs.settings

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

// KMK v0.8.10 -->
/**
 * Scrolls [lazyListState] to the item whose stable key equals [anchor], once, when this screen is
 * first composed with a non-null anchor (from `RecommendationSettingsSearchScreen` search-result
 * navigation). [itemKeysInOrder] must list every currently-composed `item(key = ...)` in the exact
 * order they appear in the screen's `LazyColumn` — this is a plain value (not a composable), so it
 * naturally reflects whichever items are conditionally present for the current state, and is a pure
 * function directly testable without Compose.
 *
 * A missing/unknown anchor (e.g. the target row isn't present for the current state -- filtered
 * out, feature disabled, list empty) is a silent no-op: the screen still opens normally, just
 * without a scroll target. This matches the behavior contract's "scrolls/highlights the target subsection when
 * that screen supports it" allowance rather than crashing or showing an error for a stale/irrelevant
 * anchor.
 */
@Composable
fun ScrollToAnchorEffect(lazyListState: LazyListState, itemKeysInOrder: List<String>, anchor: String?) {
    LaunchedEffect(anchor) {
        val index = resolveAnchorIndex(itemKeysInOrder, anchor) ?: return@LaunchedEffect
        lazyListState.animateScrollToItem(index)
    }
}

/**
 * Pure lookup extracted from [ScrollToAnchorEffect] so the anchor-resolution contract is directly
 * unit-testable without Compose/Robolectric (neither is available in this project). Returns null
 * for a null anchor, an anchor not present in [itemKeysInOrder] (silent no-op, e.g. a row filtered
 * out for the current state), or a negative index; otherwise the zero-based scroll target.
 */
fun resolveAnchorIndex(itemKeysInOrder: List<String>, anchor: String?): Int? {
    val index = anchor?.let { itemKeysInOrder.indexOf(it) } ?: return null
    return index.takeIf { it >= 0 }
}
// KMK <--
