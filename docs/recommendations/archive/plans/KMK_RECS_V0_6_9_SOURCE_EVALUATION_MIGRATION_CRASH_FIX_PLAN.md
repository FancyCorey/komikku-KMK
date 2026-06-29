# KMK-Recs v0.6.9 Source Evaluation Migration Crash Fix Plan

Status: planning. Do not implement until the user explicitly approves or provides this plan to Claude for implementation.

Date: 2026-06-19

## Problem

Opening For You / recommendation settings can crash with:

```text
android.database.sqlite.SQLiteException: no such table: source_evaluation
```

The crash happens because the settings screen subscribes to non-installed source suggestions, and that flow now also subscribes to source evaluation records. If the on-device database was created by an older APK and never migrated to include `source_evaluation`, the first query against that table crashes the screen model coroutine.

## Confirmed Code Path

The screen uses:

`app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt`

That creates:

```kotlin
private val getNonInstalledSourceSuggestions: GetNonInstalledSourceSuggestions = GetNonInstalledSourceSuggestions()
```

`GetNonInstalledSourceSuggestions` subscribes to:

`app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt`

```kotlin
val dislikedAndEvaluationsFlow = combine(
    dislikedPref.changes(),
    getSourceEvaluations.subscribeAll(),
) { dislikedRaw, evaluationList -> ... }
```

`GetSourceEvaluations.subscribeAll()` reaches:

`data/src/main/java/tachiyomi/data/taste/SourceEvaluationRepositoryImpl.kt`

```kotlin
override fun getAllAsFlow(): Flow<List<SourceEvaluation>> {
    return handler.subscribeToList {
        source_evaluationQueries.getAllAsFlow(sourceEvaluationMapper)
    }
}
```

If `source_evaluation` does not exist in the installed database, this flow throws, which takes down the settings screen.

## Confirmed Schema State

The table definition exists:

`data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq`

But the newest migration currently inspected is:

`data/src/main/sqldelight/tachiyomi/migrations/46.sqm`

`46.sqm` creates earlier KMK recommendation tables:

- `manga_taste`
- `tag_taste`
- `tag_alias`
- `recommendation_cache`
- `recommendation_disabled_source`

It does **not** create:

- `source_evaluation`

Therefore:

- fresh installs may be fine because SQLDelight creates all current `.sq` tables;
- updates from older APKs can fail because the migration path does not create `source_evaluation`.

## Important Column Detail

The current source file uses column:

```sql
lang TEXT NOT NULL
```

not:

```sql
source_lang
```

The crash report mentioned `source_lang`, but the checked repository uses `lang`. Claude must verify the actual current generated code/build output and match the migration exactly to the checked-in `source_evaluation.sq`.

Do not create a migration with `source_lang` unless the source schema has actually changed to that name.

## Root Cause

Primary root cause:

The v0.6.8 implementation added `source_evaluation.sq` and repository/query usage, but did not add the matching SQLDelight migration for existing databases.

Secondary robustness issue:

The settings/suggestions flow assumes evaluation storage is always queryable. A missing evaluation table should not crash For You settings. Since source evaluation data is rebuildable cache, the safe fallback is an empty evaluation list plus logging.

## Fix Strategy

This should be a focused hotfix.

Recommended version:

```text
KMK-Recs v0.6.9
```

Reason:

- v0.6.x is the extension/source-management track.
- v0.6.8 added source evaluation.
- v0.6.9 should fix its migration/update crash.

Expected APK naming pattern if this is the next APK:

```text
Komikku-v1.13.6-kmk.6.9-debug.apk
```

Confirm actual version metadata before building.

## Implementation Steps

### Step 1: Add SQLDelight Migration

Add a new migration file after the current latest migration.

If the latest migration remains `46.sqm`, add:

```text
data/src/main/sqldelight/tachiyomi/migrations/47.sqm
```

The migration must create the table and indexes using `IF NOT EXISTS`.

Use the exact schema from:

`data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq`

Expected migration content:

```sql
-- KMK --> v0.6.9 source evaluation table for existing installs

CREATE TABLE IF NOT EXISTS source_evaluation (
    evaluation_key TEXT NOT NULL PRIMARY KEY,
    source_id INTEGER,
    extension_pkg_name TEXT NOT NULL,
    signature_hash TEXT NOT NULL,
    extension_name TEXT NOT NULL,
    source_name TEXT NOT NULL,
    lang TEXT NOT NULL,
    base_url TEXT,
    repo_name TEXT,
    source_count INTEGER NOT NULL,
    is_nsfw INTEGER NOT NULL,
    evaluation_version INTEGER NOT NULL,
    evaluated_at INTEGER NOT NULL,
    expires_at INTEGER,
    sample_count INTEGER NOT NULL,
    popular_count INTEGER NOT NULL,
    latest_count INTEGER NOT NULL,
    search_count INTEGER NOT NULL,
    search_success_count INTEGER NOT NULL,
    liked_title_match_count INTEGER NOT NULL,
    preferred_tag_match_count INTEGER NOT NULL,
    blocked_tag_match_count INTEGER NOT NULL,
    explicit_signal_count INTEGER NOT NULL,
    ecchi_signal_count INTEGER NOT NULL,
    error_count INTEGER NOT NULL,
    quality_score REAL NOT NULL,
    recommendation_fit_score REAL NOT NULL,
    search_reliability_score REAL NOT NULL,
    explicit_score REAL NOT NULL,
    ecchi_score REAL NOT NULL,
    verdict TEXT NOT NULL,
    sampled_titles_json TEXT,
    sampled_tags_json TEXT,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS source_evaluation_source_id_index ON source_evaluation(source_id);
CREATE INDEX IF NOT EXISTS source_evaluation_pkg_index ON source_evaluation(extension_pkg_name);
CREATE INDEX IF NOT EXISTS source_evaluation_verdict_index ON source_evaluation(verdict);
CREATE INDEX IF NOT EXISTS source_evaluation_evaluated_at_index ON source_evaluation(evaluated_at);

-- KMK <--
```

Do not add default rows. The table should start empty on upgrade.

### Step 2: Verify SQLDelight Migration Numbering

Before finalizing, Claude must verify SQLDelight’s expected version/migration behavior in this project.

Checklist:

- Confirm `47.sqm` is the correct next migration number.
- Run the project’s SQLDelight verification task if available.
- If SQLDelight expects a different migration number due schema version generation, adjust accordingly.

Useful commands to investigate:

```text
./gradlew :data:tasks --all
./gradlew :data:verifySqlDelightMigration
./gradlew :data:generateSqlDelightInterface
```

Use the actual task names present in this project.

### Step 3: Add Defensive Fallback

The migration is the real fix, but the app should fail open because evaluation data is rebuildable.

Preferred place:

`domain/src/main/java/tachiyomi/domain/taste/interactor/GetSourceEvaluations.kt`

or:

`app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt`

Recommended approach:

- Keep repository methods straightforward if possible.
- Catch failures where evaluation data is optional.
- Emit an empty evaluation map/list if the evaluation table query fails.
- Log the error.

For `GetNonInstalledSourceSuggestions`, the safest local fix is:

```kotlin
val safeEvaluationsFlow = getSourceEvaluations.subscribeAll()
    .catch { e ->
        logcat(LogPriority.ERROR, e) { "Failed to load source evaluations; falling back to unevaluated suggestions" }
        emit(emptyList())
    }
```

Then combine `safeEvaluationsFlow` instead of `getSourceEvaluations.subscribeAll()` directly.

Why this location is good:

- Source evaluation is optional for Sources To Try.
- If it fails, metadata-only suggestions can still work.
- The settings screen should render.
- The source evaluation screen itself can still report an error separately if needed.

If catch is added lower in `SourceEvaluationRepositoryImpl`, ensure it does not hide write failures or corrupt-table problems during evaluation. Read fallback should be narrow.

### Step 4: Consider Source Evaluation Screen Safety

Inspect:

`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`

If it also directly collects `GetSourceEvaluations.subscribeAll()`, add similar safe handling:

- show empty past evaluations;
- show a non-crashing error state/snackbar/message if useful;
- do not crash the screen.

The user-facing fallback can be:

```text
No source evaluations yet
```

or:

```text
Source evaluations unavailable until database migration completes
```

But if the migration is fixed, most users should never see this.

### Step 5: Add Tests / Verification

Required verification:

1. Fresh database creation still succeeds.
2. Migration from version 46 to 47 creates `source_evaluation`.
3. Opening Recommendation Settings no longer crashes on updated installs.
4. Sources To Try still works when evaluation table is empty.
5. Source Evaluation screen opens with zero past evaluations.
6. Existing KMK recommendation tests still pass.

If SQLDelight migration testing is available, add or run a migration test for 46 -> 47.

If no migration test harness is available, at minimum:

- run SQLDelight generation/verification;
- run `:app:testDebugUnitTest`;
- run `:app:assembleDebug`;
- manually update over the crashing APK and open For You settings.

### Step 6: Documentation Updates

Create:

```text
docs/recommendations/KMK_RECS_V0_6_9_SOURCE_EVALUATION_MIGRATION_CRASH_FIX_IMPLEMENTATION.md
```

Update:

- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`
- `RECOMMENDATION_VERSIONING.md`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

Documentation must include:

- exact root cause;
- migration file added;
- table schema created;
- fallback behavior added;
- tests run;
- APK produced;
- whether update-over-existing-install was manually verified.

## Acceptance Criteria

This hotfix is complete only when:

- Updating from the previous APK creates `source_evaluation` automatically.
- For You settings no longer crash when the evaluation table is empty.
- Sources To Try still renders with metadata-only suggestions if evaluation data is unavailable.
- Source Evaluation screen opens without prior evaluations.
- No existing source-evaluation records are deleted during normal update.
- SQLDelight schema/migration generation succeeds.
- `:app:testDebugUnitTest` and `:app:assembleDebug` succeed, or failures are documented with exact reasons.
- A debug APK is produced with the correct next KMK version.

## Recommendation

Proceed with this as a small urgent hotfix before adding more source-evaluation features.

The user should not need to uninstall/reinstall. A normal APK update should migrate the existing database safely.

