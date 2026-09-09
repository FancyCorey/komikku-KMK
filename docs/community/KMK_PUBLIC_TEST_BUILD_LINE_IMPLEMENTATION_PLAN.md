# KMK Public Test Build Line Implementation Plan

Date: 2026-06-29
Status: pending user approval
Purpose: add a separate public/test APK line that installs beside official Komikku and beside the user's personal KMK build

## Context

The current personal build intentionally keeps:

```kotlin
applicationId = "app.komikku"
```

That is correct for the user's personal update-style APK because it installs over the existing Komikku/KMK install and preserves that app's Android data.

For a Reddit/public test APK, this is not safe. A public test APK using `app.komikku` can overwrite or conflict with official Komikku. The public test build needs a separate package ID so Android treats it as a different app.

This plan creates a separate build line while preserving the personal build exactly as-is.

## High-Level Decision

Maintain two build lines:

| Build line | Purpose | Package behavior |
|---|---|---|
| Personal KMK build | User's daily-driver/update APK | Keeps `app.komikku`; updates/replaces the user's current install |
| Public test KMK build | Reddit/community test APK | Uses a separate package ID; installs beside official Komikku |

Recommended public test package ID:

```text
app.komikku.kmk
```

Recommended public test display name:

```text
Komikku KMK
```

Recommended public test APK naming:

```text
Komikku-KMK-PublicTest-v1.13.6-kmk.7.34-debug.apk
```

or, if the build is release-signed later:

```text
Komikku-KMK-PublicTest-v1.13.6-kmk.7.34-release.apk
```

## Non-Goals

Do not change the personal build `applicationId`.

Do not migrate existing personal app data automatically.

Do not change database schema for this task.

Do not change backup/proto fields.

Do not change recommendation behavior.

Do not change Source Evaluation behavior.

Do not create a public GitHub release.

Do not publish anything externally.

Do not make OCR part of or separate from this build line unless the current build already includes OCR. This plan is only about package/build separation.

## Required Preliminary Inspection

Claude must inspect the current build setup before editing:

- `app/build.gradle.kts`
- `buildSrc/src/main/kotlin/**`
- root `build.gradle.kts`
- `settings.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `i18n/src/commonMain/moko-resources/base/strings.xml`
- `app/src/debug/res`
- `app/src/beta/res`
- any existing source-set or build-type resource override pattern

Known current observations:

- `app/build.gradle.kts` sets `applicationId = "app.komikku"` in `defaultConfig`.
- Existing build types include:
  - `debug` with `.dev`
  - `release`
  - `releaseTest` with `.rt`
  - `foss` with `.foss`
  - `preview` with `.beta`
  - `benchmark` with `.benchmark`
- `app/src/main/AndroidManifest.xml` uses `android:label="@string/app_name"`.
- `app_name` comes from `i18n/src/commonMain/moko-resources/base/strings.xml` and is currently `Komikku`.
- Manifest providers use `${applicationId}.provider` and `${applicationId}.shizuku`, which should automatically separate provider authorities for a new package ID.
- No obvious `productFlavors` declaration was found in `app/build.gradle.kts`; Claude must verify whether the custom build plugin adds a `standard` flavor or if `androidComponents.withFlavor("default" to "standard")` is build-logic generated.

## Implementation Option A: New Build Type (Recommended If No Flavor Support)

Add a dedicated build type for public testing, for example:

```kotlin
create("kmkPublicTest") {
    initWith(getByName("debug")) // or releaseTest, after Claude evaluates current behavior
    applicationIdSuffix = ".kmk"
    versionNameSuffix = "-kmk-public-test-${getCommitCount()}"
    signingConfig = getByName("debug").signingConfig
    matchingFallbacks.addAll(listOf("debug", "release"))
}
```

However, using `applicationIdSuffix = ".kmk"` on base `app.komikku` yields:

```text
app.komikku.kmk
```

This is exactly the desired separate package ID.

Claude must decide whether to initialize from `debug`, `releaseTest`, or `preview` based on current patterns:

- `debug` is easiest and already signs with the debug key, but also uses `.dev` if inherited incorrectly.
- `releaseTest` is closer to release but currently has `.rt`.
- `preview` already signs with debug and uses `.beta`.

The final package must be exactly:

```text
app.komikku.kmk
```

not:

```text
app.komikku.dev.kmk
app.komikku.rt.kmk
app.komikku.beta.kmk
```

If build-type inheritance adds unwanted suffixes, explicitly set only one suffix or choose a clean base.

## Implementation Option B: New Product Flavor

If the build logic already supports product flavors cleanly, Claude may instead add a product flavor such as:

```kotlin
flavorDimensions += "distribution"
productFlavors {
    create("personal") {
        dimension = "distribution"
        applicationId = "app.komikku"
    }
    create("publicTest") {
        dimension = "distribution"
        applicationId = "app.komikku.kmk"
        resValue("string", "app_name", "Komikku KMK")
    }
}
```

But this is likely more invasive because the current module already relies on existing custom build logic and source sets. Claude should not introduce flavors unless it confirms this will not break existing variant names and CI/test commands.

Default recommendation: use a new build type, not a new flavor.

## App Name / Label Requirement

The public test build must show a distinct app name on Android launcher and in system UI.

Preferred label:

```text
Komikku KMK
```

Possible implementation options:

1. Build-type resource override:
   - Create `app/src/kmkPublicTest/res/values/strings.xml`
   - Override `app_name` as `Komikku KMK`

2. Gradle `resValue`:
   - Add `resValue("string", "app_name", "Komikku KMK")` to the new build type

Claude must verify which approach works with Moko resources and the existing `MR.strings.app_name` usage. Because `app_name` currently lives in `i18n` Moko resources, a standard Android `resValue` may not affect all Compose text that uses `MR.strings.app_name`.

If overriding `MR.strings.app_name` per build type is not straightforward, use a safe fallback:

- Android launcher label may remain `Komikku` only if the README/APK filename clearly marks it as public test; however, this is not ideal.
- Preferred: add a KMK-specific public-test app label resource if compatible with the current resource system.

Claude must document the result honestly.

## Update Checker Requirement

The public test build must not accidentally check official Komikku updates and offer an official APK over the KMK public test app.

Claude must inspect:

- `buildSrc/src/main/kotlin/mihon/buildlogic/BuildConfig.kt`
- `app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateChecker.kt`
- `domain/src/main/java/tachiyomi/domain/release/interactor/GetApplicationRelease.kt`
- update settings / `BuildConfig.UPDATER_ENABLED`

Required behavior:

- Public test builds should not auto-update from official Komikku release feeds unless the user explicitly approves a KMK release feed later.
- If updater is disabled by default unless Gradle property `enable-updater` is passed, document that no code change is required.
- If the public test build can enable updater and point to official Komikku, add a build-config guard to disable updater for `kmkPublicTest`.

Do not introduce a fake update feed.

## Backup / Restore Requirement

Because `app.komikku.kmk` is a separate Android app:

- It will not share app data with `app.komikku`.
- Users must export a backup from official/personal Komikku and restore it into Komikku KMK if they want their library/settings.
- Existing extension trust/install state may not transfer unless covered by backup preferences.
- Downloads may not be shared depending on storage paths and app permissions.

Claude must document this in:

- `docs/community/KMK_PUBLIC_README_DRAFT.md`
- `docs/community/KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION.md` or a new public-test implementation report

## Public Test Documentation Requirement

Update or create documentation that clearly explains:

- `app.komikku` personal build updates/replaces official Komikku.
- `app.komikku.kmk` public test build installs separately.
- Users should create a backup before testing.
- Users can restore the backup into the public test build.
- This is unofficial and experimental.
- Source Evaluation and OCR remain experimental/sensitive.
- The public test APK should not be treated as an official Komikku update.

Recommended new file:

- `docs/community/KMK_PUBLIC_TEST_BUILD_LINE_IMPLEMENTATION.md`

Also update:

- `docs/community/KMK_PUBLIC_README_DRAFT.md`
- `docs/community/KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION.md`
- `docs/community/KMK_PHASE_3_4_RISK_REGISTER.md` if R-030 applicationId conflict changes from blocker to partially mitigated by public-test build line
- `docs/recommendations/NEXT_WORK.md` if it currently lists applicationId as unresolved without mentioning this plan

## APK Naming Requirement

Claude must identify the output APK path after build.

If Gradle does not customize output file names, document the actual output path and provide a recommended manual rename.

The final public test APK should be copied or renamed only if the user asks. For this implementation, building and documenting the path is enough.

Recommended naming convention:

```text
Komikku-KMK-PublicTest-v1.13.6-kmk.7.34-debug.apk
```

If using the Gradle-generated name, document the exact generated filename.

## Versioning Requirement

Do not change `KmkRecsReleaseNotes.VERSION_CODE` or `VERSION_NAME` unless the build-line change itself is treated as a new KMK release.

Recommended:

- Keep `KMK-Recs v0.7.34` for the first public-test build-line implementation if there is no feature behavior change.
- Add documentation saying this is a packaging/build-line change over the current `v0.7.34` feature set.

If Claude chooses to bump the KMK feature version, it must justify why a packaging-only change needs a new feature version.

## Acceptance Criteria

The implementation is successful only if:

1. Personal/default builds still use `app.komikku`.
2. The public test build uses `app.komikku.kmk`.
3. The public test build can install beside official/personal Komikku.
4. The launcher/system app label is distinct if technically feasible.
5. The public test build does not silently overwrite official Komikku.
6. Update behavior is documented and does not accidentally push official Komikku updates into the KMK public test build.
7. Public docs explain backup/restore expectations.
8. Tests/build pass.
9. No recommendation behavior changes are introduced.

## Required Verification

Claude must run:

```powershell
.\gradlew.bat spotlessCheck
.\gradlew.bat :app:testDebugUnitTest
```

Claude must also build the new public test variant. The exact task name depends on the chosen build type/flavor.

If using a build type named `kmkPublicTest`, likely:

```powershell
.\gradlew.bat :app:assembleKmkPublicTest
```

If Gradle creates a different variant name, Claude must list the available tasks or infer the correct one and document it.

Claude should also build the normal personal debug APK to confirm it still exists:

```powershell
.\gradlew.bat :app:assembleDebug
```

Required checks after build:

```powershell
rg -n "applicationId =|applicationIdSuffix|kmkPublicTest|app\\.komikku\\.kmk|Komikku KMK|UPDATER_ENABLED|KmkRecsReleaseNotes" app/build.gradle.kts app/src docs
```

Claude must inspect or report the APK metadata enough to confirm:

- personal/debug package is still `app.komikku.dev` or the existing expected debug ID;
- release/personal base remains `app.komikku`;
- public test package is `app.komikku.kmk`.

If using an APK analyzer or `aapt` is available, use it. If not, rely on Gradle variant configuration and document that limitation.

## Final Report Requirements

Create:

- `docs/community/KMK_PUBLIC_TEST_BUILD_LINE_IMPLEMENTATION.md`

Include:

- build strategy chosen: build type vs product flavor;
- final package IDs for personal and public test lines;
- app label result;
- updater behavior result;
- backup/restore behavior explanation;
- APK output path;
- verification commands and results;
- whether `KmkRecsReleaseNotes` was changed;
- whether any app behavior changed beyond package/build separation;
- remaining limitations.

## Suggested Final Verdict After Implementation

If all acceptance criteria pass:

```text
Private/personal build readiness: Ready -- keeps app.komikku and remains the user's update-style APK.
Public test build readiness: Ready for limited Reddit/community testing -- uses app.komikku.kmk and installs separately, with backup/restore warnings documented.
Public stable release readiness: Not ready -- still needs signed release process, public release channel, issue/support process, and long-term maintenance decision.
Upstream contribution readiness: Not ready -- feature scope remains too broad for direct upstream contribution.
```

## User Decisions To Preserve

The user has decided:

- personal build keeps the current application ID;
- public test build gets a separate application ID;
- public test APK is for possible Reddit/community sharing, not official GitHub/upstream release.

Claude must preserve that distinction.


