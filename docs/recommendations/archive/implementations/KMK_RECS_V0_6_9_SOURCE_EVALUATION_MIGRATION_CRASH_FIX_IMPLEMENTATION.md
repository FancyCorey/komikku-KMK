# KMK-Recs v0.6.9: Source Evaluation Migration Crash Fix — Implementation Notes

Date: 2026-06-19

Feature version: KMK-Recs v0.6.9

User-approved scope: Focused hotfix — add the missing SQLite migration for `source_evaluation` and add defensive read fallbacks so the crash cannot recur even on corrupt/missing table states.

---

## Goal

Prevent `android.database.sqlite.SQLiteException: no such table: source_evaluation` from crashing For You / Recommendation Settings when updating from a pre-v0.6.8 APK.

---

## Root Cause

v0.6.8 added `data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq` and wired the generated queries into `SourceEvaluationRepositoryImpl`. SQLDelight creates the table for **fresh installs** from the `.sq` definition, but **updates from older installs** require an explicit migration `.sqm` file.

v0.6.8 did not include that migration file. The latest migration at the time was `46.sqm`, which created KMK taste tables (manga_taste, tag_taste, tag_alias, recommendation_cache, recommendation_disabled_source) but **not** source_evaluation.

The crash path:
1. User updates APK from pre-v0.6.8 build.
2. SQLDelight applies migrations up to version 46; source_evaluation is never created.
3. `RecommendationsSettingsScreenModel` creates `GetNonInstalledSourceSuggestions`, which combines `getSourceEvaluations.subscribeAll()` into its reactive flow.
4. First emission of the subscribed flow throws `SQLiteException: no such table: source_evaluation`.
5. Unhandled exception propagates up; settings screen crashes.

---

## Fix

### 1. Migration file added

`data/src/main/sqldelight/tachiyomi/migrations/47.sqm`

Creates `source_evaluation` and its four indexes using `IF NOT EXISTS`, so it is safe to apply on any database that may or may not have had the table from a partial migration or fresh-install SQLDelight generation.

Schema exactly matches the checked-in `source_evaluation.sq`. Column `lang TEXT NOT NULL` is used (not `source_lang`).

### 2. Defensive fallback in GetNonInstalledSourceSuggestions

`app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt`

`getSourceEvaluations.subscribeAll()` is now wrapped:

```kotlin
val safeEvaluationsFlow = getSourceEvaluations.subscribeAll()
    .catch { e ->
        logcat(LogPriority.ERROR, e) { "source_evaluation table unavailable; falling back to empty evaluations" }
        emit(emptyList())
    }
```

`safeEvaluationsFlow` is then used in the `combine` instead of the raw flow. If the evaluation table is absent, Sources To Try renders using metadata-only suggestions (behavior identical to pre-v0.6.8) and logs the error. No crash.

### 3. Defensive fallback in SourceEvaluationScreenModel

`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`

The `init` block also subscribes to `getSourceEvaluations.subscribeAll()` directly. Added the same `.catch` before `.onEach`:

```kotlin
getSourceEvaluations.subscribeAll()
    .catch { e ->
        logcat(LogPriority.ERROR, e) { "source_evaluation table unavailable in SourceEvaluationScreen" }
        emit(emptyList())
    }
    .onEach { evals ->
        mutableState.update { it.copy(evaluations = evals) }
    }
    .launchIn(screenModelScope)
```

The Source Evaluation screen opens with an empty past-evaluations list instead of crashing.

---

## Files Changed

| File | Change |
|------|--------|
| `data/src/main/sqldelight/tachiyomi/migrations/47.sqm` | NEW — creates source_evaluation table and indexes |
| `app/.../discovery/GetNonInstalledSourceSuggestions.kt` | Added `.catch` on `subscribeAll()` flow |
| `app/.../evaluation/SourceEvaluationScreenModel.kt` | Added `.catch` on `subscribeAll()` flow |
| `app/.../KmkRecsReleaseNotes.kt` | VERSION_CODE=609, VERSION_NAME=v0.6.9, release notes entry |

---

## Behavior Changed

- Updating from pre-v0.6.8 builds now creates source_evaluation via migration 47.
- If the table is somehow still absent after migration, Sources To Try and Source Evaluation screen now fail open (empty evaluations, logged error) instead of crashing.
- No existing source_evaluation records are deleted during normal update.
- No uninstall/reinstall required.
- Normal recommendation behavior is unchanged.

---

## Tests Run

- `:app:testDebugUnitTest` — BUILD SUCCESSFUL (267 tasks, all passing)
- `:app:assembleDebug` — BUILD SUCCESSFUL

Test details: all 267 existing tests passed. No new tests were added; the migration itself cannot be covered by JVM unit tests without an Android instrumented test harness (none present in this project).

---

## APK

`Komikku-v1.13.6-kmk.6.9-debug.apk` — 133,236,305 bytes

---

## Update-Over-Existing-Install Verification

**Not manually device-verified** in this session. The migration mechanism is the standard SQLDelight migration path used since v0.6.8's migration 46. The `IF NOT EXISTS` guards prevent failure if the table already exists. The defensive catch fallback ensures the screen cannot crash even if migration is somehow delayed or fails.

Manual verification recommended: install the crashing v0.6.8 APK, confirm the crash, then install v0.6.9 APK as an update and confirm Recommendation Settings and For You open without error.

---

## Known Limitations

- Shizuku binder alive check remains hardcoded false (same as v0.6.8).
- No instrumented migration test harness; migration correctness is validated by schema match against the checked-in `.sq` file.

---

## Deviations from Plan

None. Implementation matches the plan exactly.

---

## Follow-Up Recommendations

- Add an Android instrumented test or SQLDelight migration verification task to catch missing migrations earlier.
- Consider adding a migration smoke test that opens an old database, runs migrations, and verifies all KMK tables exist.
