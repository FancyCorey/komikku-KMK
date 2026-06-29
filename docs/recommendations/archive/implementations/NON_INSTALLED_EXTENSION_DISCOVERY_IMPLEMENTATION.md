# Non-Installed Extension Discovery Implementation

Date: 2026-06-16

Status: implemented as KMK-Recs v0.6.0. Superseded by v0.6.1 for scorer behavior. See `NON_INSTALLED_EXTENSION_DISCOVERY_HARDENING_IMPLEMENTATION.md` for the correction.

## What Was Implemented

A conservative metadata-based "Sources To Try" section in Recommendation Settings that suggests non-installed extensions worth trying for Browse > For You recommendations.

### New Package: `exh/recs/discovery/`

**`NonInstalledSourceSuggestion.kt`**

- `SuggestionConfidence` enum: `LOW`, `MEDIUM`. HIGH is intentionally absent — reserved for post-install installed-source fit learning.
- `NonInstalledSuggestionReason` sealed interface: `LanguageMatch`, `SimilarToInstalledSource`, `SameRepoAsInstalledSources`, `NeedsTesting`.
- `NonInstalledSourceSuggestion` data class holding original `Extension.Available`, selected `Extension.Available.Source?`, score, confidence, reasons, and stable `dismissalKey`.
- `InstalledExtensionHints` internal data class: slim representation of installed extension data (no Android `Drawable`), used so the scorer has zero Android dependencies.

**`NonInstalledSourceSuggestionScorer.kt`**

Pure object, no Android dependencies, fully unit-testable.

Filtering (exclude if):
- Extension `signatureHash|pkgName` matches any installed extension.
- Extension `signatureHash|pkgName` matches any untrusted extension.
- `isNsfw && !nsfwEnabled`.
- Source lang not in normalized `recLanguages`.
- `dismissalKey` in dismissed set.

For extensions with multiple sources, each eligible source gets its own suggestion.

Scoring signals:
- `+0.20` — language match (always, since filtered otherwise).
- `+0.10` — same repo name as any installed extension.
- `+0.10` — source name similarity to any installed source (substring containment on normalized names, min length 6).
- `+0.05` — source has a non-empty base URL.
- `+0.05` — source name or base URL contains a content keyword (`scans`, `manhwa`, `manga`, `webtoon`, `comics`, `scan`).
- Score capped at `0.69`.

Confidence:
- `MEDIUM` if score >= 0.35 (multiple signals agree).
- `LOW` otherwise.

**`NonInstalledSourceSuggestionStore.kt`**

Persistence helpers: `parse(raw)`, `serialize(keys)`, `dismiss(current, key)`.

**`GetNonInstalledSourceSuggestions.kt`**

Interactor combining `availableExtensionsFlow`, `installedExtensionsFlow`, `untrustedExtensionsFlow`, and `dismissedNonInstalledRecommendationSources().changes()`. Converts `Extension.Installed` to `InstalledExtensionHints` before calling the scorer. Returns `Flow<List<NonInstalledSourceSuggestion>>`.

### Screen Model Changes

`RecommendationsSettingsScreenModel`:
- Added `suggestions` and `expandSuggestions` to `State`.
- Subscribed to `GetNonInstalledSourceSuggestions.subscribe()` in `init`.
- Added `installSuggestion()`, `dismissSuggestion()`, `toggleExpandSuggestions()` actions.
- `installSuggestion()` calls `extensionManager.installExtension(suggestion.extension)` — with the original extension, not a synthetic `GetExtensionsByType` copy.

### Settings Screen Changes

`RecommendationsSettingsScreen`:
- Added "Sources To Try" section at the bottom of the LazyColumn.
- Shows top 5 suggestions by default; "Show N more" expands to all.
- Each suggestion row (`SourceSuggestionItem`): name, lang · repo · confidence label, reason text, Install button, Dismiss button.
- Install triggers existing extension install flow. Dismissed suggestions are persisted immediately.

### Preference

`SourcePreferences.dismissedNonInstalledRecommendationSources()` — semicolon-separated `"signatureHash|pkgName|sourceId"` strings.

### Strings Added

10 new strings in `i18n-kmk/strings.xml`: `rec_sources_to_try_header`, `rec_sources_to_try_empty`, `rec_suggestion_install`, `rec_suggestion_dismiss`, `rec_suggestion_confidence_low`, `rec_suggestion_confidence_medium`, `rec_suggestion_reason_language_match`, `rec_suggestion_reason_same_repo`, `rec_suggestion_reason_similar_source`, `rec_suggestion_reason_needs_testing`.

## What Was Intentionally Not Implemented

- Automatic installation.
- Website crawling or source-specific website scrapers.
- Source quality learning from real For You runs (installed-source fit) — deferred, the plan recommends implementing it before or alongside discovery, but it is a larger feature.
- Cross-source link groups, favorite mode, backup changes — unrelated.
- Changes to normal global search or source migration.
- A separate browseable extension discovery screen — Settings integration is sufficient for v1.
- "Search in Extensions" action — users can navigate to the Extensions screen from the standard Browse tab.
- Post-install pending-evaluation state — deferred, suggestions disappear naturally after install via installed flow.

## Original Extension Identity Issue

`GetExtensionsByType` creates synthetic per-source copies of `Extension.Available` with modified `pkgName = "${ext.pkgName}-${source.id}"` for UI grouping. Passing one of these copies to `ExtensionManager.installExtension()` would use the wrong package identity.

Resolution: `GetNonInstalledSourceSuggestions` works directly from `ExtensionManager.availableExtensionsFlow` (raw original extensions). Each `NonInstalledSourceSuggestion` stores the original `Extension.Available` as received from that flow, plus the specific `Extension.Available.Source` separately. `installSuggestion()` passes `suggestion.extension` directly — the original, unmodified extension.

The `dismissalKey` includes `sourceId` when a source-level suggestion is dismissed so that dismissal targets the specific source, not the whole multi-source extension.

## How Install Works

`extensionManager.installExtension(suggestion.extension)` starts the existing APK download and install flow via `ExtensionInstaller`. The system shows download/install progress (notification or status bar). After install completes, `installedExtensionsFlow` emits the new extension, and `GetNonInstalledSourceSuggestions` re-emits with the newly installed extension excluded from suggestions. The suggestion row disappears automatically.

## How Dismiss Works

`dismissSuggestion()` reads the current dismissed set from `SourcePreferences`, adds the `dismissalKey`, and writes back. `GetNonInstalledSourceSuggestions` subscribes to `dismissedNonInstalledRecommendationSources().changes()`, so the suggestion disappears immediately from the next emitted list.

## Tests Added

`NonInstalledSourceSuggestionScorerTest` (12 tests):

1. Installed extension is excluded.
2. Untrusted extension is excluded.
3. Language mismatch is excluded.
4. NSFW source excluded when NSFW display disabled.
5. Dismissed suggestion excluded.
6. Language match creates low-confidence suggestion.
7. Same repo increases score and adds reason.
8. Suggestions sorted by score descending then name ascending.
9. No suggestion exceeds 0.69 score (HIGH confidence excluded).
10. Original extension identity preserved (pkgName not mutated).
11. `normalizeSourceName` lowercases and removes non-alphanumeric.
12. `sourceKeywordTokens` splits name and baseUrl into tokens.

## Commands Run

```text
./gradlew :app:testDebugUnitTest --tests "*NonInstalledSource*" → BUILD SUCCESSFUL, 12 tests PASSED
./gradlew :app:assembleDebug → BUILD SUCCESSFUL
```

## APK

`Komikku-v1.13.6-kmk.6.0-debug.apk`

## Known Risks and Deferred Work

### Installed-source fit learning not yet implemented

Suggestions rely only on weak metadata signals. They are labeled "Potential fit" / "Install to test" deliberately because the app has no post-install evidence yet. Once installed-source fit learning exists, the scorer can be extended to use those stats.

### Language reactivity

`GetNonInstalledSourceSuggestions` subscribes to dismissed pref changes, installed/untrusted/available extension changes. It does NOT directly subscribe to `recommendationSourceLanguages` changes. A language change will not trigger a suggestion refresh until the user also dismisses something or extensions reload. Acceptable for v0.6.0; can add language pref changes subscription later.

### No suggestion caching

Suggestions are recomputed from flows on each emission. This is fine for a small list from repo metadata.

### Very large available extension lists

Scorer iterates all available extensions per emission. For a large repo, this is still fast (milliseconds) since all data is already in memory.
