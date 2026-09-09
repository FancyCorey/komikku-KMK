# KMK-Recs v0.7.17 â€” Bundle Ambiguity, Shizuku UX, String Audit

Date: 2026-06-28

Status: implemented and tested.

## Summary

Three tasks implemented in v0.7.17:

1. **R-010**: Bundle import missing-source ambiguity detection.
2. **R-005 + R-019**: Typed installer policy messages + bundle load error string externalization.

## Task 1 â€” R-010: Bundle Import Source Ambiguity

### Problem

When a bundle item's source was not installed, the screen model called `findAvailableExtension()` which used `firstOrNull { ext.pkgName == pkgName }`. If multiple available extensions matched the same package name (e.g., different signing keys / different repos), the first one was silently picked with no user warning.

### Solution

Replaced `findAvailableExtension()` with `resolveAvailableExtension()` in `RecommendationBundleSourceResolver` (pure object, no Android deps).

**New sealed interface `AvailableExtensionResolution`:**
- `Unambiguous(ext)` â€” exactly one match
- `Ambiguous(candidates)` â€” two or more matches
- `NotFound` â€” no pkgName in item, or no available extension matches

**Resolution algorithm (2-step):**
1. If item has `extensionSignatureHash`: filter all available extensions by `pkgName == item.extensionPkgName && signatureHash == item.extensionSignatureHash`. If any match: Unambiguous (1) or Ambiguous (>1).
2. pkgName-only fallback: filter all by `pkgName == item.extensionPkgName`. If any match: Unambiguous (1) or Ambiguous (>1). NotFound otherwise.

**New state `AmbiguousSource(candidates: List<Extension.Available>)`** added to `RecommendationImportItemState`.

**Screen changes:**
- `PreviewContent` collects `ambiguousCandidates` (unique by pkgName) from `AmbiguousSource` items.
- Each candidate is shown via `InstallExtensionCard` using the new `rec_bundle_ambiguous_source_prompt` string.
- `ImportItemStateBadge` handles `AmbiguousSource` â†’ "Ambiguous source" label.
- `InstallExtensionCard` accepts an optional `labelRes: StringResource` (defaults to `rec_bundle_missing_source_prompt`).

### Files Changed

- `app/src/main/java/exh/recs/share/RecommendationBundleSourceResolver.kt` â€” added `AvailableExtensionResolution`, `resolveAvailableExtension()`
- `app/src/main/java/exh/recs/share/RecommendationBundleImportScreenModel.kt` â€” added `AmbiguousSource` state, replaced `findAvailableExtension()` with resolver call
- `app/src/main/java/exh/recs/share/RecommendationBundleImportScreen.kt` â€” added `AmbiguousSource` badge + install cards, `labelRes` param on `InstallExtensionCard`
- `i18n-kmk/.../base/strings.xml` â€” added `rec_bundle_import_state_ambiguous_source`, `rec_bundle_ambiguous_source_prompt`
- `app/src/test/java/exh/recs/share/RecommendationBundleSourceResolverTest.kt` â€” 6 new tests

### Tests

6 new tests added to `RecommendationBundleSourceResolverTest`:

1. `resolveAvailableExtension returns NotFound when item has no pkgName`
2. `resolveAvailableExtension returns NotFound when no available extension matches pkgName`
3. `resolveAvailableExtension returns Unambiguous for exact pkgName + sigHash match`
4. `resolveAvailableExtension returns Ambiguous when multiple extensions share pkgName and sigHash`
5. `resolveAvailableExtension falls back to pkgName-only when sigHash provided but no sigHash match`
6. `resolveAvailableExtension returns Ambiguous on pkgName-only match when multiple extensions share same pkgName`

All 17 tests in `RecommendationBundleSourceResolverTest` PASSED (11 original + 6 new).

---

## Task 2 â€” R-005: Shizuku UX Typed Messages

### Problem

`SourceEvaluationInstallerPolicy.PolicyResult.message: String?` contained 8 hardcoded English strings describing each installer mode/state. These were shown directly as `Text(text = msg)` in `InstallerModeSelector` and as `message` in InfoCards in `SourceEvaluationScreen`. No localization; unclear mode differentiation.

### Solution

Added `InstallerPolicyMessage` enum to `SourceEvaluationInstallerPolicy` with 8 typed cases:

```
PRIVATE_READY, PRIVATE_UNAVAILABLE,
SHIZUKU_READY, SHIZUKU_NOT_INSTALLED, SHIZUKU_NOT_RUNNING, SHIZUKU_NEEDS_PERMISSION,
CURRENT_SHIZUKU_UNAVAILABLE, CURRENT_PROMPT_HEAVY
```

Replaced `message: String?` with `messageKey: InstallerPolicyMessage?` in `PolicyResult`. All 8 `PolicyResult(...)` constructors in `validate()` updated to use typed keys.

Added `@Composable` extension `SourceEvaluationInstallerPolicy.InstallerPolicyMessage.toLocalString()` in `SourceEvaluationScreen.kt` that maps each enum case to the appropriate KMR string.

Updated both `InstallerModeSelector` and InfoCard usages to use `policy.messageKey?.toLocalString()`.

### New KMR Strings (8)

```xml
source_evaluation_installer_private_ready
source_evaluation_installer_private_unavailable
source_evaluation_installer_shizuku_ready
source_evaluation_installer_shizuku_not_installed
source_evaluation_installer_shizuku_not_running
source_evaluation_installer_shizuku_needs_permission
source_evaluation_installer_current_shizuku_unavailable
source_evaluation_installer_current_prompt_heavy
```

R-005 clarity improvements in the new copy:
- Private: "Best for evaluation. Extensions install inside Komikku and are cleaned up silently."
- Shizuku ready: "Shizuku installs extensions as system packages. Cleanup will show Android uninstall prompts. Use Private for silent cleanup."
- Shizuku not installed / not running / needs permission: distinct messages per failure state.
- Current+Shizuku unavailable: "Shizuku is not available. Switch to Private installer for reliable evaluation."
- Current+other: "This installer shows Android confirmation dialogs for each install and uninstall. Limit to small batches or switch to Private installer."

### Files Changed

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationInstallerPolicy.kt` â€” added `InstallerPolicyMessage` enum, changed `message: String?` â†’ `messageKey: InstallerPolicyMessage?`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` â€” added `toLocalString()` composable extension, updated 3 usages
- `i18n-kmk/.../base/strings.xml` â€” 8 new installer policy strings

---

## Task 3 â€” R-019: Bundle Load Error String Audit

### Problem

`RecommendationBundleImportScreenModel.load()` set `State.LoadError("hardcoded English...")` for all 6 validation error cases. The screen displayed `s.message` directly as a Text composable. No localization.

### Solution

Added `sealed interface LoadErrorKey` with 6 variants:

```kotlin
data class WrongSchema(val found: String) : LoadErrorKey
data class UnsupportedVersion(val found: Int) : LoadErrorKey
data object TooManyItems : LoadErrorKey
data object TooManySources : LoadErrorKey
data object FileTooLarge : LoadErrorKey
data class MalformedJson(val detail: String?) : LoadErrorKey
```

Changed `State.LoadError(val message: String)` â†’ `State.LoadError(val error: LoadErrorKey)`.

Added `@Composable fun LoadErrorKey.toLocalString()` in `RecommendationBundleImportScreen.kt` that maps each variant to a KMR string, using `RecommendationBundleValidator.MAX_ITEMS` / `MAX_SOURCES` constants for the limit values.

### New KMR Strings (7)

```xml
rec_bundle_load_error_wrong_schema         (format: %1$s)
rec_bundle_load_error_unsupported_version  (format: %1$d)
rec_bundle_load_error_too_many_items       (format: %1$d)
rec_bundle_load_error_too_many_sources     (format: %1$d)
rec_bundle_load_error_file_too_large
rec_bundle_load_error_malformed_json       (format: %1$s)
rec_bundle_load_error_malformed_json_unknown
```

### Files Changed

- `app/src/main/java/exh/recs/share/RecommendationBundleImportScreenModel.kt` â€” added `LoadErrorKey`, changed `State.LoadError`
- `app/src/main/java/exh/recs/share/RecommendationBundleImportScreen.kt` â€” added `LoadErrorKey.toLocalString()`, updated Text
- `i18n-kmk/.../base/strings.xml` â€” 7 new load error strings

---

## Remaining Deferred

- `SourceEvaluationScreenModel.screenErrorMessage: String?` still hardcoded (contains `e.message` exception detail). Converting requires a `ScreenErrorKey` sealed interface. Deferred to a future pass.
- R-022 architecture cleanup â€” deferred.
- Phase 12 public packaging â€” deferred.

## Test Results

```
BUILD SUCCESSFUL
RecommendationBundleSourceResolverTest: 17/17 PASSED (11 original + 6 new)
KmkOcrExclusionTest: 3/3 PASSED (regression check)
Full :app:testDebugUnitTest suite: PASSED
```

## APK Naming

After `assembleDebug`, copy output to:
`Komikku-v1.13.6-kmk.7.17-debug.apk`

