# Source Preference Like/Dislike Implementation Plan

Date: 2026-06-17

Status: planning only. Do not implement until the user approves this plan and requests a Claude Code prompt.

Suggested target feature version: `KMK-Recs v0.6.2`

Versioning note: this feature continues the v0.6 non-installed/extension-source discovery track. Per project convention, future work that revisits the same system should remain under the same major/minor topic line as a patch/minor increment (`v0.6.2`, `v0.6.3`, etc.) instead of jumping to a new major topic number.

## Purpose

Add explicit source-level preference controls so the user can like or dislike a source/extension directly.

This should work for:

- installed recommendation sources,
- non-installed but suggested "Sources To Try" sources.

The purpose is to stop relying only on inference. If the user knows a source is good or bad, the app should let them say so directly.

## Key Product Distinction

This feature must keep three concepts separate:

### Like Source

Means:

```text
I personally want this source favored in recommendations/discovery.
```

Installed source effect:

- boosts For You/source-fit/source-priority suggestions,
- may help keep the source visible when competing with similar sources,
- may later help source priority recommendation.

Non-installed source effect:

- keeps the suggestion visible,
- sorts it above neutral suggestions,
- marks it as a source the user is interested in testing,
- should not auto-install.

### Dislike Source

Means:

```text
I personally do not want this source recommended.
```

Installed source effect:

- strongly deprioritizes or excludes it from recommendation features,
- can hide it from For You source rows if product decision says disliked sources should be avoided,
- should not uninstall it or disable it globally.

Non-installed source effect:

- hides it from Sources To Try,
- prevents it from being suggested again unless reset,
- is stronger and more durable than dismiss.

### Dismiss Suggestion

Means:

```text
Not now / hide this suggestion.
```

Dismiss is not the same as dislike.

Dismiss should remain a lightweight "remove from this suggestion list" action, while dislike becomes a real preference signal.

## Current Related Features

### Installed Source Controls

Recommendation Settings already has source priority and enabled/disabled controls for installed recommendation sources.

Relevant files:

```text
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
app/src/main/java/exh/recs/RecommendationSourceOrdering.kt
app/src/main/java/exh/recs/RecommendationSourceFilter.kt
```

Existing source enable/disable is not the same as like/dislike:

- disable means "do not use this source in recommendation searches",
- dislike means "I dislike this source as a recommendation candidate/source."

For v1, dislike may map to exclusion from recommendation discovery/For You, but it should be stored separately so the user's intent is preserved.

### Non-Installed Suggestions

Sources To Try currently has:

- Install,
- Dismiss.

Relevant files:

```text
app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestion.kt
app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionScorer.kt
app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionStore.kt
app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt
```

Dismissal currently uses:

```text
signatureHash|pkgName|sourceId
```

This key shape can be reused for non-installed source preferences.

## Recommended Scope For First Implementation

Implement explicit source preferences without trying to solve source-fit learning yet.

Deliver:

1. A source preference model with `LIKE`, `DISLIKE`, and neutral/reset.
2. Persistence for installed and non-installed source preferences.
3. UI controls in Recommendation Settings installed source rows.
4. UI controls in Sources To Try suggestion rows.
5. Scorer/filter integration:
   - liked non-installed suggestions get a boost and stay visible,
   - disliked non-installed suggestions are hidden,
   - disliked installed sources are excluded or strongly deprioritized from For You recommendation selection,
   - liked installed sources receive a small boost/tie-break in future ordering, if safe.
6. Tests for storage, filtering, and scoring.

Do not implement:

- automatic install,
- automatic uninstall,
- source-fit learning,
- source priority auto-reorder,
- backup/restore unless a DB model is chosen and scope is approved,
- sync,
- source-specific website logic.

## Data Model Options

### Option A: Preference String

Store compact strings in `SourcePreferences`.

Example preferences:

```kotlin
fun likedRecommendationSources() = preferenceStore.getStringSet("liked_recommendation_sources", emptySet())
fun dislikedRecommendationSources() = preferenceStore.getStringSet("disliked_recommendation_sources", emptySet())
```

Key format:

```text
installed|sourceId
available|signatureHash|pkgName|sourceId
```

Pros:

- simple,
- fast to implement,
- no SQLDelight migration,
- enough for first version.

Cons:

- less structured,
- harder to add timestamps/reasons later,
- backup/sync needs explicit preference inclusion behavior check.

### Option B: SQLDelight Table

Add a table:

```sql
source_preference(
    key TEXT NOT NULL PRIMARY KEY,
    source_id INTEGER,
    signature_hash TEXT,
    package_name TEXT,
    available_source_id INTEGER,
    display_name TEXT NOT NULL,
    rating INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
)
```

Rating:

```text
1 = LIKE
-1 = DISLIKE
0 = neutral/reset means delete row
```

Pros:

- durable and extensible,
- can store timestamps,
- easier to support backup/restore later,
- cleaner for future source-fit learning.

Cons:

- larger implementation,
- migration/proto/backup decisions,
- more test work.

### Recommendation

Use **Option A** for the first implementation unless the user explicitly wants this to become a long-term synced data model immediately.

Reason: this feature is a control surface for recommendation behavior. It can begin as preference-backed data, then migrate to SQLDelight if source-fit learning evolves into a larger system.

## Source Identity Keys

Create a shared key model, for example:

```text
app/src/main/java/exh/recs/sourceprefs/RecommendationSourcePreferenceKey.kt
```

Suggested model:

```kotlin
sealed interface RecommendationSourcePreferenceKey {
    data class Installed(val sourceId: Long) : RecommendationSourcePreferenceKey
    data class Available(
        val signatureHash: String,
        val pkgName: String,
        val sourceId: Long?,
    ) : RecommendationSourcePreferenceKey
}
```

Serialized:

```text
i|sourceId
a|signatureHash|pkgName|sourceId
a|signatureHash|pkgName
```

Use `sourceId` when available. For extension-level suggestions with no source entry, omit it.

Important:

- installed sources should use source ID because the app can act on actual `CatalogueSource`.
- non-installed sources should use signature/package/source ID because source IDs may only exist in repo metadata until install.

## Source Preference Model

Suggested enum:

```kotlin
enum class RecommendationSourcePreference {
    LIKE,
    DISLIKE,
    NEUTRAL,
}
```

Neutral should delete/reset the stored key.

Suggested store:

```text
app/src/main/java/exh/recs/sourceprefs/RecommendationSourcePreferenceStore.kt
```

Responsibilities:

- parse liked/disliked preference sets,
- set like,
- set dislike,
- reset,
- resolve preference for a key,
- guarantee a key cannot be both liked and disliked.

## UI Plan

### Installed Source Rows

In Recommendation Settings source priority list, add compact controls to each source row.

Recommended UI:

- thumbs up icon,
- thumbs down icon,
- selected state if liked/disliked,
- tap selected icon again resets to neutral.

Do not make text-heavy controls in every row.

Behavior:

- Like: set installed source preference to LIKE.
- Dislike: set installed source preference to DISLIKE.
- Reset: tapping selected Like/Dislike clears preference.

Keep existing enable/disable toggle separate.

### Sources To Try Rows

In each non-installed suggestion row:

- keep Install,
- keep Dismiss,
- add Like/Dislike icons or menu actions.

Recommended behavior:

- Like: persist LIKE for the available source key.
- Dislike: persist DISLIKE and hide the suggestion.
- Dismiss: persist dismissal only, not a dislike.
- Reset should be possible if a settings/debug list of disliked sources is later added. For v1, a disliked non-installed source may require a future "Manage hidden suggestions" area to undo.

If reset UI for non-installed disliked suggestions is not included, document this limitation clearly.

## Recommendation Behavior

### Installed Source Preference Effects

For v1, use conservative behavior:

- `DISLIKE`: exclude from For You source candidate list, same practical effect as disabling for recommendations, but stored separately.
- `LIKE`: do not automatically reorder the manual source list, but use as a soft boost/tie-break in future source suggestions and optionally visual badge.

Important product decision:

If disliked installed sources are excluded from For You, the UI should show this clearly in Recommendation Settings. Otherwise the user may wonder why a source disappeared.

Suggested row label:

```text
Disliked · excluded from For You
```

Alternative:

- Do not exclude disliked installed sources yet.
- Only use dislike for future source-fit/source-priority suggestions.

Recommendation: for v1, disliked installed sources should be excluded from For You because the user explicitly said they do not want the source.

### Non-Installed Source Preference Effects

`DISLIKE`:

- exclude from Sources To Try.

`LIKE`:

- always qualify the source for Sources To Try even if the hardening scorer would otherwise hide it,
- sort above neutral suggestions,
- reason text: "You liked this source."

This is important because user preference is stronger than metadata scoring.

## Scoring Integration

### Non-Installed Discovery Scorer

Add input:

```kotlin
sourcePreferences: Map<RecommendationSourcePreferenceKey, RecommendationSourcePreference>
```

or separate liked/disliked key sets.

Behavior:

- If key is disliked: skip.
- If key is liked: include with explicit user-like score/reason.
- If neutral: apply normal v0.6.1 hardened scoring.

Suggested new reason:

```kotlin
data object UserLikedSource : NonInstalledSuggestionReason
```

Suggested score:

```text
liked source score = 0.68
```

Still below installed-source fit high-confidence range, but above ordinary metadata suggestions.

### For You Source Selection

Relevant file:

```text
app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt
```

For source selection:

1. Load disliked installed source IDs.
2. Exclude them from `orderedEnabledSources`, or treat them like disabled source IDs.
3. Ensure status diagnostics can show disliked/excluded state if needed.

Possible new status:

```kotlin
RecommendationSourceStatus.Disliked
```

or reuse `Disabled` only if the UI makes the reason clear elsewhere.

Recommendation: add `Disliked` status if not too much churn.

## Preference APIs

In `SourcePreferences`, add:

```kotlin
fun likedRecommendationSourceKeys() = preferenceStore.getStringSet("liked_recommendation_source_keys", emptySet())
fun dislikedRecommendationSourceKeys() = preferenceStore.getStringSet("disliked_recommendation_source_keys", emptySet())
```

If `StringSet` is not preferred in this codebase, use compact serialized `String` as current dismissed suggestions do.

The store should enforce:

- liking removes from disliked,
- disliking removes from liked,
- reset removes from both.

## Tests

Add tests for source preference store:

```text
app/src/test/java/exh/recs/sourceprefs/RecommendationSourcePreferenceStoreTest.kt
```

Test:

1. parse installed key.
2. parse available key with source ID.
3. parse available extension-level key without source ID.
4. malformed keys are ignored.
5. liking removes dislike.
6. disliking removes like.
7. reset removes both.
8. serialization is stable.

Update discovery scorer tests:

```text
app/src/test/java/exh/recs/discovery/NonInstalledSourceSuggestionScorerTest.kt
```

Add:

1. disliked available source is excluded.
2. liked available source is included even without metadata similarity.
3. liked source sorts above neutral suggestions.
4. liked source reason appears.
5. dismissed and disliked behavior remain distinct.

Add/adjust For You/source selection tests if there are existing seams:

- disliked installed source is excluded from recommendation source list,
- liked installed source is not auto-disabled,
- source status/reason displays correctly if implemented.

## Documentation Updates

Create implementation report:

```text
docs/recommendations/SOURCE_PREFERENCE_LIKE_DISLIKE_IMPLEMENTATION.md
```

Update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

Docs should explain:

- source like/dislike is explicit user preference,
- dismiss is separate from dislike,
- installed source dislike excludes/deprioritizes For You,
- non-installed source dislike hides from Sources To Try,
- non-installed source like keeps/pins a suggestion,
- no source is auto-installed,
- no source is auto-uninstalled.

## Versioning

Suggested:

```text
KMK-Recs v0.6.2
```

Reason: this is a new explicit preference layer for the existing v0.6 extension/source recommendation system.

Expected debug APK:

```text
Komikku-v1.13.6-kmk.6.2-debug.apk
```

Only claim the APK exists if it is actually built and copied/renamed.

## Interaction With v0.6.1 Hardening

This plan should come **after** the v0.6.1 hardening patch.

Reason:

- v0.6.1 fixes the current problem where every extension is suggested.
- source like/dislike then adds explicit user control on top of a sane suggestion list.

If implemented before v0.6.1, the like/dislike feature may mask the broad-suggestions bug instead of fixing it.

## Manual Verification

After implementation:

1. Open Recommendation Settings.
2. Like an installed source.
3. Confirm the liked state persists after leaving/reopening settings.
4. Dislike an installed source.
5. Confirm it is clearly marked as disliked.
6. Refresh For You and confirm disliked installed source is excluded or clearly deprioritized according to implementation choice.
7. Like a Sources To Try suggestion.
8. Confirm it stays visible and ranks above neutral suggestions.
9. Dislike a Sources To Try suggestion.
10. Confirm it disappears and does not return after reopening settings.
11. Dismiss a different suggestion.
12. Confirm dismiss and dislike are stored separately.
13. Confirm Install still uses the normal extension installer.
14. Confirm no source is auto-installed or auto-uninstalled.

## Risks

### User Cannot Reset Hidden Non-Installed Dislikes

If a disliked non-installed suggestion disappears, the user may need a way to undo later.

Mitigation:

- add a future "Manage hidden source suggestions" section,
- or include reset controls only for installed sources in v1 and document non-installed reset as deferred.

### Confusion With Disable Source

Dislike and disable can feel similar.

Mitigation:

- label clearly:
  - disabled = not searched,
  - disliked = avoided because user dislikes it.

### Too Much UI In Source Rows

Source rows already contain rank/status/toggle controls.

Mitigation:

- use icon buttons,
- use tooltips/content descriptions,
- keep text compact.

### Data Migration Later

Preference-backed storage may later need DB migration.

Mitigation:

- keep key serialization stable,
- document preference keys,
- centralize parse/serialize logic.

## Summary For Claude

Implement source-level Like/Dislike preferences for installed sources and non-installed Sources To Try suggestions.

Keep Dismiss separate from Dislike.

Use a centralized key/store:

- installed source key by source ID,
- available source key by signature/package/source ID.

For non-installed suggestions:

- liked source appears and ranks high,
- disliked source is hidden,
- dismissed suggestion remains a separate lightweight hide action.

For installed sources:

- liked source is marked/favored,
- disliked source is marked and excluded/deprioritized from For You according to the chosen implementation.

Do not auto-install, auto-uninstall, crawl websites, or implement source-fit learning in this pass.
