package exh.recs.settings

import cafe.adriel.voyager.core.screen.Screen

// KMK v0.8.9 -->
/**
 * Pure search-matching engine for Recommendation Settings search.
 *
 * ## Why this is a new, parallel index rather than reusing `SettingsSearchScreen`'s literal classes
 *
 * The main Settings search (`SettingsSearchScreen.kt`) works by flattening every `SearchableSettings`
 * screen's `getPreferences(): List<Preference>` — the official `Preference.PreferenceItem`/
 * `PreferenceGroup` DSL those screens are literally built from — then filtering by
 * `title.contains(searchKey, true) || subtitle?.contains(searchKey, true)`.
 *
 * Every Recommendation Settings screen (`RecommendationForYouSettingsScreen`,
 * `RecommendationSourcePrioritySettingsScreen`, `RecommendationTasteTagsSettingsScreen`,
 * `RecommendationNonInstalledDiscoverySettingsScreen`, `RecommendationDiagnosticsSettingsScreen`,
 * plus `SourceEvaluationScreen`) is a hand-built `LazyColumn` of custom composables (reorderable
 * source list, tag `FilterChip`s, suggestion cards, drag handles) — **none of it is expressed as
 * `Preference.PreferenceItem` objects**. There is nothing to flatten: `getPreferences()` would have
 * nothing real to return without rewriting every one of those screens onto the official Preference
 * DSL, which would mean re-deriving custom controls (drag-and-drop reordering, suggestion bulk-select,
 * tag chips with a leading-icon-per-preference-state) that the DSL has no equivalent for today. That
 * rewrite was judged out of proportion to a search feature and too large to verify safely without a
 * device in this session — see the v0.8.9 implementation report.
 *
 * What IS reused, faithfully, is everything about the *behavior*: case-insensitive substring
 * matching (same semantics as `SettingsSearchScreen`'s `.contains(searchKey, true)`), a bounded
 * result count, a breadcrumb-style category label, and — in `RecommendationSettingsSearchScreen` —
 * the exact same `TopAppBar` + `BasicTextField` + `Crossfade` + `LazyColumn` + `EmptyScreen` UI shape
 * `SettingsSearchScreen` uses. This engine additionally adds real ranking (exact title match ranked
 * above a partial summary match) and stable ordering for ties, which the official implementation
 * does not have (it relies on flatten order alone) — both explicitly required by the plan.
 */
object RecommendationSettingsSearchIndex {

    /** One indexed destination or control. Built with already-resolved (stringResource'd) text — see [RecommendationSettingsSearchScreen] for where entries are constructed. */
    data class Entry(
        val key: String,
        val title: String,
        val summary: String,
        val category: String,
        val synonyms: List<String> = emptyList(),
        val destination: Screen,
        /** True when this setting is currently reachable. A false entry is still shown (per plan section "truthful unavailable state"), never silently hidden. */
        val available: Boolean = true,
        // KMK v0.8.10: stable in-screen scroll target -- one of the destination screen's own
        // LazyColumn item(key = ...) identifiers, never a fragile positional index. Null means this
        // entry only opens the destination screen (category-level fallback); the destination screen
        // may also simply not support anchor scrolling yet ("when that screen supports it").
        val anchor: String? = null,
    )

    /**
     * Case-insensitive, punctuation/whitespace-normalized search over [entries]. Read-only — never
     * mutates any entry or preference. Returns entries ranked exact-title-match first, then
     * title-prefix, then title-contains, then synonym match, then summary/category match; ties keep
     * [entries]' original relative order (Kotlin's `sortedByDescending` is a stable sort).
     */
    fun search(entries: List<Entry>, query: String): List<Entry> {
        val q = normalize(query)
        if (q.isBlank()) return emptyList()
        return entries
            .mapNotNull { entry -> scoreFor(entry, q)?.let { entry to it } }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun scoreFor(entry: Entry, q: String): Int? {
        val title = normalize(entry.title)
        val summary = normalize(entry.summary)
        val category = normalize(entry.category)
        val synonyms = entry.synonyms.map(::normalize)
        return when {
            title == q -> 100
            title.startsWith(q) -> 90
            title.contains(q) -> 70
            synonyms.any { it == q } -> 65
            synonyms.any { it.contains(q) } -> 55
            summary.contains(q) -> 40
            category.contains(q) -> 30
            else -> null
        }
    }

    /** Lowercase, replace punctuation with spaces, and collapse whitespace so "Source-Priority", "source priority", and "SOURCE  PRIORITY" all match identically. */
    fun normalize(s: String): String = s
        .lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
// KMK <--
