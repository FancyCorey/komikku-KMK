# KMK-Recs v0.8.10 Fix 2: Extension Linkage Failure Isolation

**Status:** Planned
**Scope:** Focused crash fix only
**Build naming:** `Komikku-v1.14.0-kmk.8.10-fix2-debug.apk`

## Evidence

The diagnostic file `komikku_crash_logs_3.txt` contains several historical crashes. The current failure is the entry at `2026-07-18 06:31:02` on app version `1.14.0-32`:

```text
java.lang.NoClassDefFoundError: Failed resolution of: Lokhttp3/zstd/Zstd;
Caused by: java.lang.ClassNotFoundException: Didn't find class "okhttp3.zstd.Zstd"
```

The missing class is looked up through `ChildFirstPathClassLoader` while the installed `eu.kanade.tachiyomi.extension.en.asurascans` extension is creating its HTTP client. For You loads this source during recommendation retrieval. The extension failure escapes the source row and terminates the process.

The older `GetCrossSourceGroupPrimary` and `UpdateMangaFromRemote` entries are historical records from earlier builds, not the current July 18 root cause.

## Confirmed technical cause

`NoClassDefFoundError` is an `Error`, not an `Exception`. `RecommendsScreenModel` currently rethrows every `Error` after a source request fails. That protects the process from fatal VM conditions, but it also rethrows recoverable extension linkage failures such as `NoClassDefFoundError` and `NoSuchMethodError`.

Therefore one incompatible or incompletely packaged extension can crash the entire For You screen instead of becoming one failed source row.

## Required implementation

1. Add a narrowly scoped classifier for extension execution failures. Treat missing-class, missing-method, incompatible-class, and linkage failures as recoverable source failures. Do not catch or downgrade `OutOfMemoryError`, `StackOverflowError`, or unrelated fatal VM errors.
2. Apply the classifier at the shared recommendation/source-request boundary used by For You and group recommendation previews. Do not add a broad process-level catch and do not hide errors from unrelated app screens.
3. Convert a recoverable linkage failure into the existing per-source error state, preserving the source name and a sanitized diagnostic category such as `Extension incompatible or missing dependency`.
4. Ensure sibling source requests continue after Asura fails; one source must not cancel the whole recommendation load.
5. Prevent immediate retry loops for the same source during the active load. Existing cancellation and generation guards must remain intact.
6. If the existing extension quarantine mechanism supports runtime failures, record this source as unavailable/quarantined through that existing path. Do not uninstall an extension automatically and do not invent a second quarantine store.
7. Verify that normal extension errors, network errors, cancellation, and fatal VM errors retain their existing semantics.

## Files to inspect first

- `app/src/main/java/exh/recs/RecommendsScreenModel.kt`
- `app/src/main/java/exh/recs/GroupPreviewLoadCoordinator.kt`
- `app/src/main/java/exh/recs/sources/RecommendationPagingSource.kt`
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt`
- Existing source quarantine/error policies and their tests

Claude must confirm the actual shared boundary before editing. Do not assume the group-preview catch is the only path used by the For You page.

## Tests and verification

- Unit-test classification of `NoClassDefFoundError`, `NoSuchMethodError`, and other linkage failures as recoverable.
- Unit-test that `OutOfMemoryError` and `StackOverflowError` remain fatal and are rethrown.
- Test that one failed source produces an error row while another source still produces results.
- Test cancellation and generation changes do not publish stale source results.
- Test that the diagnostic category does not expose a raw stack trace in normal UI text.
- Run `spotlessCheck`, the complete unit-test suite, and `assembleDebug`.
- Build the APK using the exact v0.8.10 fix2 naming convention and place the handoff APK in the workspace `private` folder.
- Record the result in a v0.8.10 fix2 implementation report and update the recommendation documentation index/current-state record.

## Non-goals

- Do not change recommendation ranking or taste scoring.
- Do not change normal global search limits.
- Do not add a new HTTP client dependency solely to mask a broken extension package.
- Do not silently swallow fatal VM errors.
- Do not rewrite unrelated UI, settings, or Komikku 1.14 reconciliation work.
