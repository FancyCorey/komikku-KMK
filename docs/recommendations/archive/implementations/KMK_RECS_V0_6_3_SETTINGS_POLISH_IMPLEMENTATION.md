# KMK-Recs v0.6.3 Settings Polish Implementation

Date: 2026-06-18

Status: implemented as KMK-Recs v0.6.3.

## Goal

Polish the Recommendation Settings screen with three improvements:
- A1: Bulk install for visible Sources To Try suggestions.
- A2: Clarify Like/Dislike content descriptions and add a scope note.
- A3: Verify source priority ordering persistence; add gap tests.

## What Was Implemented

### A1: Bulk Install Visible Sources To Try

**`RecommendationsSettingsScreenModel.kt`**

New state fields:
- `installingSuggestionKeys: ImmutableSet<String>` â€” dismissal keys of suggestions currently being installed.
- `isBulkInstallingSuggestions: Boolean` â€” true while bulk install is running.

`installSuggestion()` updated to track `installingSuggestionKeys`: sets the key before installing, removes it in a `finally` block.

New `installSuggestions(suggestions: List<NonInstalledSourceSuggestion>)`:
- Deduplicates by `signatureHash|pkgName` (avoids installing the same extension twice for multi-source extensions).
- Sets `isBulkInstallingSuggestions = true` before starting.
- Installs each suggestion sequentially, with per-extension `try/catch` to continue after failures.
- Tracks each key in `installingSuggestionKeys` while its install is in progress.
- Clears `isBulkInstallingSuggestions` when done.

**`RecommendationsSettingsScreen.kt`**

`SourceSuggestionItem` extended with `isInstalling: Boolean = false` parameter. The Install button is disabled when `isInstalling` is true.

In the call site, `isInstalling = suggestion.dismissalKey in state.installingSuggestionKeys`.

New `item(key = "suggestions_bulk_install")` after the expand toggle (inside the `else` block where suggestions are shown). Shows:
- "Install visible suggestions (N)" button â€” enabled when suggestions are present and no bulk install is running.
- "Installing suggestionsâ€¦" label while bulk install is running.

### A2: Source Preference Semantics Cleanup

**Strings added:**
- `rec_suggestion_install_visible` â€” "Install visible suggestions (%1$d)"
- `rec_suggestion_installing_visible` â€” "Installing suggestionsâ€¦"
- `rec_source_preference_scope_note` â€” "Installed source dislikes affect For You only. Sources To Try dislikes hide future suggestions."
- `rec_source_preference_like_for_you` â€” "Like for For You"
- `rec_source_preference_dislike_for_you` â€” "Dislike for For You"
- `rec_source_preference_like_source` â€” "Like source suggestion"
- `rec_source_preference_dislike_source` â€” "Dislike source suggestion"

**`SourcePriorityItem`**: thumbs-up and thumbs-down icons now use `rec_source_preference_like_for_you` and `rec_source_preference_dislike_for_you` as content descriptions (was: generic "Like" / "Dislike").

**`SourceSuggestionItem`**: thumbs-up and thumbs-down icons now use `rec_source_preference_like_source` and `rec_source_preference_dislike_source` as content descriptions.

**Scope note**: `item(key = "suggestions_scope_note")` added after the bulk install button â€” shows `rec_source_preference_scope_note` in `bodySmall` / `onSurfaceVariant` style. No data model changes.

### A3: Source Ordering Persistence Verification

Read `RecommendationSourceOrdering.kt` and `RecommendationSourceOrderingTest.kt`. All core behaviors are covered:
- Empty / blank parse, valid parse, duplicate removal.
- Serialize round-trip.
- `apply` with empty order, stored order, disabled exclusion, uninstalled-id skip, appended new sources, re-enabled source.
- `boostedSourceIds` normal and under-count cases.
- `applyAll` with disabled sources.
- `mergeVisibleOrder` appends hidden ids, no hidden ids, empty stored, deduplication, hidden relative order.

One gap found: malformed id input not explicitly tested.

**New test added in `RecommendationSourceOrderingTest.kt`:**

```
parse drops malformed ids and preserves first occurrence order
â†’ parse("1,notanid,,3,NaN") == [1L, 3L]
```

No changes to the ordering logic itself â€” implementation was correct.

### Pre-existing Test Fix

`GetTasteProfileTest.kt` and `StubMangaRepository.kt` were failing to compile because `TasteRepository` gained three new methods (`getMangaTaste(source, url)`, `getMangaTasteAsFlow(source, url)`, `deleteMangaTaste(source, url)`) and `MangaRepository` gained `getKnownRecommendationMangaIds()`. These methods were not yet implemented in the test stubs.

Fixed by:
- Adding `getKnownRecommendationMangaIds()` stub to `StubMangaRepository.kt` (returns `emptySet()`).
- Adding three new TasteRepository method stubs to the inline `fakeTasteRepo` in `GetTasteProfileTest.kt`.

## Files Changed

- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` â€” new state fields + `installSuggestions()` + `installSuggestion()` tracking
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` â€” bulk install button, scope note, `isInstalling` param, updated content descriptions
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` â€” 7 new strings
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` â€” VERSION_CODE=603
- `app/src/test/java/exh/recs/RecommendationSourceOrderingTest.kt` â€” 1 new test (malformed id parse)
- `app/src/test/java/exh/taste/StubMangaRepository.kt` â€” stub for `getKnownRecommendationMangaIds`
- `app/src/test/java/exh/taste/GetTasteProfileTest.kt` â€” stubs for 3 new TasteRepository methods

## Commands Run

```text
./gradlew :app:compileDebugKotlin --offline â†’ BUILD SUCCESSFUL
./gradlew :app:testDebugUnitTest --offline â†’ BUILD SUCCESSFUL, all tests PASSED
./gradlew :app:assembleDebug --offline â†’ BUILD SUCCESSFUL
```

## APK

`Komikku-v1.13.6-kmk.6.3-debug.apk`

