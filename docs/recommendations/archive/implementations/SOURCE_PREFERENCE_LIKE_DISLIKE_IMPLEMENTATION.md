# Source Like/Dislike Preferences Implementation

Date: 2026-06-17

Status: implemented as KMK-Recs v0.6.2.

## Goal

Add explicit source-level Like and Dislike controls for installed recommendation sources and non-installed Sources To Try suggestions.

## What Was Implemented

### New Package: `exh/recs/sourceprefs/`

**`RecommendationSourcePreference.kt`**

Enum: `LIKE`, `DISLIKE`, `NEUTRAL`.

**`RecommendationSourcePreferenceStore.kt`**

Pure stateless store with no Android dependencies.

- `installedKey(sourceId: Long): String` â€” builds `i|sourceId`
- `availableKey(signatureHash, pkgName, sourceId?): String` â€” builds `a|signatureHash|pkgName[|sourceId]`
- `parse(raw: String): Set<String>` â€” splits semicolon-separated keys
- `serialize(keys: Set<String>): String` â€” joins with semicolons
- `like(liked, disliked, key)` â€” adds to liked, removes from disliked
- `dislike(liked, disliked, key)` â€” adds to disliked, removes from liked
- `reset(liked, disliked, key)` â€” removes from both
- `installedSourceIds(keys)` â€” extracts `Long` source IDs from installed-prefix keys

### NonInstalledSuggestionReason

Added `data object UserLikedSource` to the sealed interface in `NonInstalledSourceSuggestion.kt`.

### Scorer Changes (`NonInstalledSourceSuggestionScorer.kt`)

New constant: `SCORE_USER_LIKED = 0.68`.

`scoreAndFilter` now accepts `likedKeys: Set<String> = emptySet()` and `dislikedKeys: Set<String> = emptySet()`.

In the scoring loop:
- Disliked candidate â†’ skip (same position as dismissed check, before calling `score()`).
- Liked candidate â†’ `score()` returns immediately with `UserLikedSource` + `NeedsTesting` reasons, score 0.68 (`MEDIUM` confidence). Evidence gate bypassed.
- Neutral â†’ existing v0.6.1 hardened behavior: requires `SimilarToInstalledSource` evidence.

### Interactor Changes (`GetNonInstalledSourceSuggestions.kt`)

Added `likedPref` and `dislikedPref` as two additional flows. Now uses a nested 3-arg combine for extension flows, then a 5-arg outer combine that includes dismissed, language, liked, and disliked preferences. All six reactive sources trigger recomputation.

### For You Integration (`BrowsePersonalRecommendationsScreenModel.kt`)

In `load()`, after loading `disabledSourceIds`:
1. Load `dislikedRecommendationSourceKeys()`.
2. Parse and extract installed source IDs from keys prefixed with `i|`.
3. Build `effectiveDisabledIds = disabledSourceIds + dislikedInstalledIds`.
4. Pass `effectiveDisabledIds` to `RecommendationSourceOrdering.apply()` and to `profileFingerprint()`.

Disliked installed sources are excluded from For You the same as disabled sources. The preference fingerprint change invalidates the For You cache when a dislike is added or removed.

### Preference Keys (`SourcePreferences.kt`)

```kotlin
fun likedRecommendationSourceKeys() = preferenceStore.getString("liked_recommendation_source_keys", "")
fun dislikedRecommendationSourceKeys() = preferenceStore.getString("disliked_recommendation_source_keys", "")
```

Semicolon-separated, consistent with the existing dismissed-sources preference.

### Settings Screen Model (`RecommendationsSettingsScreenModel.kt`)

New state fields:
- `likedSourceKeys: ImmutableSet<String>`
- `dislikedSourceKeys: ImmutableSet<String>`

Initialized from preferences on startup; subscribed to changes via a 2-arg combine flow.

New actions:
- `setInstalledSourcePreference(sourceId, preference)` â€” builds `i|sourceId` key and calls `applySourcePreference`.
- `setAvailableSourcePreference(suggestion, preference)` â€” builds `a|...` key and calls `applySourcePreference`.
- `applySourcePreference(key, preference)` â€” reads current liked/disliked sets, applies mutation, writes back both prefs.

### Settings Screen UI (`RecommendationsSettingsScreen.kt`)

**`SourcePriorityItem`** (installed source rows):
- Added `isLiked`, `isDisliked`, `onLike`, `onDislike` parameters.
- Status text shows `rec_source_status_disliked` ("Disliked Â· excluded from For You") in error color when disliked, before the disabled check.
- Added thumbs-up and thumbs-down `IconButton` elements between status column and the enable/disable Switch.
- Filled icon variant when active; outlined when not. Primary color for liked; error color for disliked.

**`SourceSuggestionItem`** (Sources To Try rows):
- Added `isLiked`, `isDisliked`, `onLike`, `onDislike` parameters.
- Added `UserLikedSource â†’ rec_suggestion_reason_user_liked` to reason text mapping.
- Added thumbs-up and thumbs-down `IconButton` elements in the button row alongside Install and Dismiss.

Icons used: `Icons.Outlined.ThumbUp`, `Icons.Filled.ThumbUp`, `Icons.Outlined.ThumbDown`, `Icons.Filled.ThumbDown` (all from `material-icons-extended`).

### Strings Added

4 new strings in `i18n-kmk/strings.xml`:

- `rec_suggestion_reason_user_liked` â€” "You liked this source"
- `rec_source_preference_like` â€” "Like"
- `rec_source_preference_dislike` â€” "Dislike"
- `rec_source_status_disliked` â€” "Disliked Â· excluded from For You"

## What Was Intentionally Not Implemented

- Auto-install or auto-uninstall.
- Source priority auto-reorder based on like.
- Source-fit learning.
- SQLDelight table for source preferences (preference-backed storage is sufficient for v1).
- Backup/restore of source preferences.
- "Manage hidden source suggestions" UI for resetting disliked non-installed sources. A user who dislikes a non-installed suggestion has no UI path to undo it in v0.6.2. This is a known limitation; a future "Manage hidden sources" screen or Settings reset option would address it.

## Key Design Decisions

### Dismiss vs Dislike

Dismiss (`dismissedNonInstalledRecommendationSources`) and dislike (`dislikedRecommendationSourceKeys`) are stored separately. Dismissal is checked before the liked/disliked check in the scorer, so a dismissed-and-liked source remains hidden (dismissed takes precedence). This preserves the "not now" vs "avoid long-term" distinction.

### For You: Dislike as Exclusion

Disliked installed sources are excluded from For You (added to the effective disabled set). This is conservative but clear: the user explicitly said they do not want this source. The source row shows the dislike status so users understand why it is not searched. The source is not uninstalled or hidden from the Extensions browser.

### Liked Source Score

`SCORE_USER_LIKED = 0.68` places user-liked sources above any metadata match (max 0.60 for exact name match) but below the 0.70+ range reserved for installed-source fit learning. Still capped at 0.69.

## Files Changed

- `app/src/main/java/exh/recs/sourceprefs/RecommendationSourcePreference.kt` (new)
- `app/src/main/java/exh/recs/sourceprefs/RecommendationSourcePreferenceStore.kt` (new)
- `app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestion.kt` â€” UserLikedSource reason
- `app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionScorer.kt` â€” liked/disliked support
- `app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt` â€” liked/disliked pref flows
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` â€” effectiveDisabledIds
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` â€” state and actions
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` â€” thumbs UI
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` â€” two new prefs
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` â€” VERSION_CODE=602
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` â€” 4 new strings
- `app/src/test/java/exh/recs/sourceprefs/RecommendationSourcePreferenceStoreTest.kt` (new) â€” 13 tests
- `app/src/test/java/exh/recs/discovery/NonInstalledSourceSuggestionScorerTest.kt` â€” 5 new tests (23 total)

## Commands Run

```text
./gradlew :app:compileDebugKotlin â†’ BUILD SUCCESSFUL
./gradlew :app:testDebugUnitTest --tests "*NonInstalledSource*" --tests "*RecommendationSourcePreference*" â†’ BUILD SUCCESSFUL, all tests PASSED
./gradlew :app:testDebugUnitTest â†’ BUILD SUCCESSFUL
./gradlew :app:assembleDebug â†’ BUILD SUCCESSFUL
```

## APK

`Komikku-v1.13.6-kmk.6.2-debug.apk`

## Known Limitations

- Disliked non-installed sources have no reset path from the current UI. A future "Manage hidden sources" section would address this.
- Like for installed sources does not affect the current For You source order. It is stored for future use (source-fit learning, source priority suggestions).
- No backup/restore for liked/disliked preferences.

