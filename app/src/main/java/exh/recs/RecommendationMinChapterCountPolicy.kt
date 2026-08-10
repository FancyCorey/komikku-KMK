package exh.recs

// KMK_CLAUDE_LATEST_CATALOGUE_AND_EXPOSURE_PLAN_2026-08-08 -->
/**
 * Single pure contract for the existing For You "Minimum chapter count" filter: which values are
 * supported, what the default is, how a malformed persisted value resolves, and which label a value
 * renders as.
 *
 * ## Why this exists
 *
 * Before this policy the same truth lived in three unconnected places -- the preference default
 * (`SourcePreferences.recommendationMinChapterCount()`), the options list (hardcoded inside
 * `RecommendationTasteTagsSettingsScreen`'s composable), and the "Off" label rule (a lambda in that
 * same composable) -- and `RecommendationsSettingsScreenModel.setMinChapterCount()` persisted any
 * `Int` at all. A corrupt or out-of-contract persisted value (negative, an unsupported number such
 * as `7`, or an oversized value) therefore flowed straight into
 * [RecommendationCandidateVisibilityPolicy.evaluate]'s `minChapterCount` parameter -- where *any*
 * value greater than zero silently activates filtering at an unsupported threshold -- and into
 * `BrowsePersonalRecommendationsScreenModel`'s cache fingerprint, while the settings row would have
 * rendered it as an unlabelled raw number.
 *
 * This mirrors the established in-repo pattern of [ForYouResultBudgetPolicy] and
 * `GroupPreviewBudgetPolicy` rather than inventing a new one.
 *
 * ## Behavior contract this policy must not change
 *
 * The filter's *semantics* are unchanged and are still owned by
 * [RecommendationCandidateVisibilityPolicy]: a locally-known chapter count below the threshold is
 * hidden, a count at or above it stays visible, an unknown count is not filtered, and a failed
 * chapter-count lookup fails open (the caller passes an empty map). This policy only decides which
 * threshold value is legitimate in the first place.
 */
object RecommendationMinChapterCountPolicy {

    /** `0` disables the filter entirely and is rendered as "Off". */
    const val OFF = 0

    /** The default when nothing has been chosen or when a persisted value is malformed. */
    const val DEFAULT = OFF

    /**
     * Every value the picker offers, in display order. Any other persisted value (negative,
     * unsupported, oversized, or written by a future/older build) resolves to [DEFAULT].
     */
    val SUPPORTED_VALUES = listOf(0, 5, 10, 20, 50)

    /**
     * Resolves a raw/stored preference value to a supported threshold. Used at every read boundary
     * *and* by the setter, so a value that reaches storage is already legitimate and a value that
     * somehow predates this policy is still corrected on the way out.
     */
    fun resolve(configuredValue: Int): Int = if (configuredValue in SUPPORTED_VALUES) configuredValue else DEFAULT

    /** True when [configuredValue] would actually filter anything once resolved. */
    fun isFilterActive(configuredValue: Int): Boolean = resolve(configuredValue) > OFF
}
// KMK <--
