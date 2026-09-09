package mihon.core.migration.migrations

import eu.kanade.domain.source.service.SourcePreferences
import exh.recs.RecommendationSourceFilter
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext

/**
 * The single initialization/migration owner for the recommendation-language default.
 *
 * ## Approved policy: `GLOBAL_SOURCE_LANGUAGES + LEGACY_ABSENT_RETAINS_ENGLISH`
 *
 * 1. **New installs** take one normalized snapshot of the global source languages
 *    ([SourcePreferences.enabledLanguages]).
 * 2. **Legacy users whose recommendation-language key is absent** are materialized to English, so an
 *    upgrade never silently widens the catalogue they already had.
 * 3. **An explicit stored recommendation-language choice always wins** and is never rewritten.
 * 4. **Later global-language changes never rewrite recommendation languages** -- this runs once and
 *    then permanently no-ops because the key becomes present.
 *
 * ## Why one `ALWAYS` migration rather than two
 *
 * `MigrationStrategyFactory` routes a fresh install (`old == 0`) to `InitialMigrationStrategy`, which
 * runs **only** `isAlways` migrations; upgrades additionally run versioned ones. A versioned migration
 * therefore cannot serve new installs at all. `MigrationJobFactory` also sorts by `version`, and
 * [Migration.ALWAYS] is `-1f`, so `ALWAYS` migrations run *before* every versioned migration -- a
 * two-migration split would let the fresh-install branch execute first on an upgrading device and
 * snapshot global languages for a legacy user, which the policy forbids.
 *
 * Both cases are therefore decided here, from the one fact that distinguishes them: the app-state
 * version counter still holds its **pre-upgrade** value while migrations run, because `App.kt` only
 * writes the new value in `onMigrationComplete`. `0` means fresh install; anything else means upgrade.
 *
 * ## Idempotence and failure behavior
 *
 * [Preference.isSet] is the single guard: once a value is stored -- whether by this migration, by the
 * user, or by a restore -- this migration is a no-op forever. Re-running it (duplicate migration,
 * process recreation, restore-then-migrate) cannot change a stored value. A missing dependency returns
 * `false` rather than throwing, leaving the read-time default (`en`) as the truthful fallback.
 *
 * Normalization is delegated to [RecommendationSourceFilter.normalizeLanguages], the existing owner:
 * it trims blanks, lowercases with `Locale.ROOT`, preserves a valid `all`, and falls back to `en` for
 * empty input. No second normalization rule is introduced here.
 *
 * ## Blank, malformed and unsupported stored input -- why this migration deliberately does not repair it
 *
 * Determined from the current production owners, not assumed:
 *
 * - **Normalization already belongs to the consumer boundary, and every consumer honors it.**
 *   [RecommendationSourceFilter.filterForRecommendations] calls `normalizeLanguages` *itself*, so every
 *   selection path (`RecommendationSourceSelector`, `GetNonInstalledSourceSuggestions`,
 *   `GetSourceEvaluationCandidates`, `SourceEvaluationRunner`, `SourceEvaluationScreenModel`,
 *   `SameMangaCandidateSearcher`, `RecommendationsSettingsScreenModel`) already resolves `{"EN"}`,
 *   `{"en", ""}`, `{" "}` and `{}` to exactly the same source set. A blank or malformed stored value is
 *   therefore **already repaired on every read**. Rewriting it here would change stored bytes without
 *   changing a single observable behavior.
 * - **Preservation is therefore semantic, not byte-for-byte.** This migration is byte-preserving, which
 *   is strictly stronger than required and costs nothing.
 * - **"Unsupported" cannot be determined truthfully from any stable owner.** The only existing owner that
 *   could answer it, [RecommendationSourceFilter.availableLanguages], derives its answer from the
 *   *currently installed sources*, which changes whenever an extension is installed, uninstalled or fails
 *   to load. Treating a code as unsupported on that basis would silently delete a valid user choice
 *   merely because an extension was temporarily absent at migration time. No static language registry is
 *   invented here either. Unsupported codes are consequently **retained**; they are inert, because
 *   [RecommendationSourceFilter.isAllowedLanguage] simply never matches them.
 * - **A stored empty set is not a broken value needing repair.** It resolves to `en` at read time --
 *   identical to the declared default -- so it is already semantically correct.
 *
 * The one real defect this contract surfaced was *not* in stored data but in a consumer that skipped
 * normalization: `RecommendsScreenModel` used the raw stored set as a group-preview **cache key**, so
 * equivalent selections produced different keys and fragmented the cache. That was repaired at that call
 * site, which is the nearest truthful owner. This migration was left unchanged, because it was correct.
 *
 * ## Cache invalidation
 *
 * Deliberately none. On a fresh install no recommendation cache exists yet. On the legacy path the
 * stored value (`en`) is byte-identical to the read-time default that was already in effect, so no
 * consumer's resolved language set changes and no cached recommendation becomes stale. Adding an
 * invalidation here would clear valid user data for no behavioral reason.
 */
class RecommendationLanguageInitializationMigration(
    private val preferenceStoreOverride: PreferenceStore? = null,
    private val sourcePreferencesOverride: SourcePreferences? = null,
) : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val preferenceStore = preferenceStoreOverride
            ?: migrationContext.get<PreferenceStore>()
            ?: return@withIOContext false
        val sourcePreferences = sourcePreferencesOverride
            ?: migrationContext.get<SourcePreferences>()
            ?: return@withIOContext false

        val recommendationLanguages = sourcePreferences.recommendationSourceLanguages()
        // An explicit choice -- or an already-materialized default -- is authoritative and untouched.
        if (recommendationLanguages.isSet()) return@withIOContext false

        val lastVersionCode = preferenceStore
            .getInt(Preference.appStateKey(LAST_VERSION_CODE_KEY), 0)
            .get()

        val resolved = if (lastVersionCode == FRESH_INSTALL_VERSION_CODE) {
            RecommendationSourceFilter.normalizeLanguages(sourcePreferences.enabledLanguages().get())
        } else {
            RecommendationSourceFilter.DefaultLanguages
        }

        recommendationLanguages.set(resolved)
        true
    }

    companion object {
        /**
         * Owned by `App.initializeMigrator()`. It still holds the pre-upgrade value while migrations
         * run, which is what makes the fresh-install/upgrade distinction possible here.
         */
        const val LAST_VERSION_CODE_KEY = "eh_last_version_code"

        /** `MigrationStrategyFactory` treats `old == 0` as a fresh install. */
        const val FRESH_INSTALL_VERSION_CODE = 0
    }
}
// KMK <--
