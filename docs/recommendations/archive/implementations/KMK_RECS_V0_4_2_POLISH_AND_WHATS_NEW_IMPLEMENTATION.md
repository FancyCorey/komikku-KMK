# KMK-Recs v0.4.2 Polish And What's New Implementation

Date: 2026-06-14

Version: KMK-Recs v0.4.2

APK: `Komikku-v1.13.6-kmk.4.2-debug.apk`

## Pre-Implementation Verification

| Check | Finding |
|---|---|
| `hideKnownManga` read order in `load()` | âœ… Bug confirmed: fingerprint computed at line 190, `hideKnownManga` read at line 196 â€” fixed by moving read before fingerprint call |
| `loadFromCache()` known-manga filter | âŒ None found â€” added fail-open known filter matching live search path |
| `profileFingerprint()` includes `hideKnownManga` | âŒ Not present â€” added `update("hideKnown:$hideKnownManga")` to digest |
| `conservativeWorkKey` single-contributor limitation | âœ… Confirmed: author always wins over artist even when both present, NUL separator in key string |
| Existing What's New infrastructure | âœ… Found `WhatsNewDialog`, `WhatsNewScreen` (ui), `WhatsNewScreen` (presentation), `MainActivity` changelog logic |
| KMK preference pattern | âœ… Confirmed `Preference.appStateKey(...)` with `PreferenceStore.getInt(...)` pattern in MainActivity |
| AboutScreen KMR string usage | âœ… KMR.strings used for KMK-specific About entries |

## Files Changed

- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` â€” moved `hideKnownManga` read before `profileFingerprint()`, added `hideKnownManga: Boolean` param to `profileFingerprint()` and `loadFromCache()`, added fail-open known filter in `loadFromCache()`
- `app/src/main/java/exh/recs/CombinedPicksAccumulator.kt` â€” replaced `conservativeWorkKey()` (single key) with `conservativeWorkKeys()` (set of keys), updated `add()` to resolve canonical bucket via any matching key, removed NUL separator
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` â€” new KMK file with `VERSION_CODE = 402`, `VERSION_NAME`, `MARKDOWN`
- `app/src/main/java/eu/kanade/tachiyomi/ui/more/KmkRecsWhatsNewScreen.kt` â€” new KMK Voyager screen wrapping `WhatsNewScreen` presentation with local notes, marks version as seen on open
- `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/KmkRecsWhatsNewDialog.kt` â€” new KMK dialog shown at launch when KMK version is newer than last seen
- `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt` â€” added `kmkRecsLastSeenVersion` preference, `showKmkChangelog` state, `KmkRecsWhatsNewDialog` shown as `else if` after upstream changelog
- `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/AboutScreen.kt` â€” added KMK What's New entry before "What's Coming" (opens `KmkRecsWhatsNewScreen`)
- `app/src/test/java/exh/recs/CombinedPicksAccumulatorTest.kt` â€” renamed helper tests from `conservativeWorkKey` to `conservativeWorkKeys`, updated assertions for set API, added tests for author-OR-artist logic (same title+same author+different artist merges, same title+same artist+different author merges, different author+different artist does not merge)
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` â€” added `kmk_recs_whats_new`, `kmk_recs_updated`

## Behavior Changed

### Cache Correctness For Hide Known Manga

`hideKnownManga` is now read before the profile fingerprint is computed. It is included in the SHA-256 digest as `"hideKnown:$hideKnownManga"`. Changing the setting produces a different fingerprint and naturally invalidates existing cache entries.

`loadFromCache()` now re-applies the known-manga filter after the favorite/rated filter. The same fail-open pattern as `searchSource()` is used: if the known-manga DB lookup fails, all cached candidates are kept and a WARN is logged.

### Refined Top Picks Duplicate Identity

`conservativeWorkKey(manga): String?` replaced by `conservativeWorkKeys(manga): Set<String>`.

The new function generates up to two keys per candidate:
- `"title:<normalized>|author:<normalized>"` when author is non-blank
- `"title:<normalized>|artist:<normalized>"` when artist is non-blank

Returns empty set when title or both contributor fields are blank (no safe key).

In `add()`, the canonical bucket is found by checking all work keys of the incoming candidate against `workKeyToPrimaryKey`. The first match determines the canonical bucket. All work keys are registered to that canonical bucket using `putIfAbsent` (first-registration wins). This means:
- If m1 has author A and artist B, it registers two keys.
- If m2 has author A and artist C, it finds a match via the author key and merges into m1's bucket. The artist C key is then also registered to m1's bucket.
- Entries with no shared author AND no shared artist do not merge.

The NUL separator (`\0`) that was present in the old key string has been replaced with `|`.

### Local KMK-Recs What's New

On first launch after installing a build with `KmkRecsReleaseNotes.VERSION_CODE = 402`, a `KmkRecsWhatsNewDialog` appears. It is shown as `else if` after the upstream `showChangelog` block, so upstream and KMK dialogs are never shown simultaneously.

Dismissing the dialog or opening the notes screen marks `kmk_recs_last_seen_version_code = 402` via `Preference.appStateKey(...)`. Subsequent launches do not show the dialog.

About screen has a new "KMK-Recs What's new" entry (showing the version name as subtitle) that opens `KmkRecsWhatsNewScreen` directly without a network call.

## Tests Run

- `CombinedPicksAccumulatorTest` â€” 32 tests, BUILD SUCCESSFUL
- Full `:app:testDebugUnitTest` â€” BUILD SUCCESSFUL
- `:app:assembleDebug` â€” BUILD SUCCESSFUL
- APK: `Komikku-v1.13.6-kmk.4.2-debug.apk` (universal)

## Known Limitations

- `KmkRecsWhatsNewScreen` reuses the presentation `WhatsNewScreen` which always shows an "Open in browser" button. Tapping it is a no-op (empty lambda). The button text may look slightly odd for local notes but does not break anything.
- Known-manga cache filter uses the same local-DB-only approach as the live path: cross-device reads or deleted history entries are not detected.
- The `kmk_recs_last_seen_version_code` preference must be manually bumped to `VERSION_CODE` in `KmkRecsReleaseNotes` for future KMK releases.

## Deviations From Plan

- Plan mentioned `IndexedRecommendation` as a possible local type for `loadFromCache()`. Implemented as a `List<Pair<Manga, Double>>` instead â€” simpler and avoids an unnecessary type.
- Plan said "decide whether to bump cache key from `personal_v3` to `personal_v4`" â€” decided not to bump. Including `hideKnownManga` in the fingerprint naturally invalidates incompatible entries once. The `personal_v3` key is stable.
- Work-key conflict resolution (multiple keys mapping to different existing buckets) is handled by `firstNotNullOfOrNull` â€” first matching key wins, which is insertion-ordered and deterministic given the order candidates arrive.

