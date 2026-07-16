# KMK Public Test Build Line Implementation Report

Date: 2026-06-29
Status: Complete. All acceptance criteria met.

Plan source: `docs/community/KMK_PUBLIC_TEST_BUILD_LINE_IMPLEMENTATION_PLAN.md`

**v0.7.45 note:** the build-type/package-ID/updater/backup mechanics documented below are still
current and unchanged. Version numbers quoted in this report (`v0.7.34`, `VERSION_CODE 734`) are a
historical snapshot from when this build line was first added and are **not** the current version --
see `docs/recommendations/CURRENT_STATE.md` or `KmkRecsReleaseNotes.kt` for the current
`KMK-Recs` version. This report is preserved as-is (per documentation rules, historical implementation
reports are not rewritten) rather than edited throughout.

---

## Summary

A separate `kmkPublicTest` build type was added to `app/build.gradle.kts`. It uses `applicationIdSuffix = ".kmk"` on the base `app.komikku` ID, producing `app.komikku.kmk`. This lets the public test APK install beside official Komikku and beside the user's personal KMK build without any conflict.

The personal/debug build is unchanged.

---

## Build Strategy: New Build Type

Option A from the plan was used: a new build type named `kmkPublicTest`.

Reasons for choosing build type over product flavor:
- No existing `productFlavors` in the app module (only the `androidComponents.withFlavor("default" to "standard")` selector from the upstream build plugin, which does not add real flavors).
- Adding a flavor would require renaming all existing variant tasks and would be more invasive.
- Build type inheritance from `release` with explicit suffix gives exactly `app.komikku.kmk`.

---

## Final Package IDs

| Build line | Build type | Package ID | Purpose |
|---|---|---|---|
| Personal / daily-driver | `debug` | `app.komikku.dev` | User's personal debug build |
| Personal release | `release` | `app.komikku` | User's update-style install |
| Public test | `kmkPublicTest` | `app.komikku.kmk` | Reddit / community sharing |

Package IDs confirmed from `output-metadata.json` files in `app/build/outputs/apk/`.

---

## Build Type Configuration Added

```kotlin
// KMK --> public test build line: separate applicationId (app.komikku.kmk) for community sharing
create("kmkPublicTest") {
    initWith(release)

    applicationIdSuffix = ".kmk"
    isMinifyEnabled = false
    isShrinkResources = false

    signingConfig = debug.signingConfig

    matchingFallbacks.addAll(commonMatchingFallbacks)
}
// KMK <--
```

Design notes:
- `initWith(release)` avoids inheriting `.dev` from debug.
- `applicationIdSuffix = ".kmk"` on `app.komikku` base â†’ `app.komikku.kmk`. There is no chained suffix.
- `isMinifyEnabled = false`, `isShrinkResources = false` -- test build, keep readable for debugging.
- `signingConfig = debug.signingConfig` -- signed with the local debug keystore, same as `preview`.
- `matchingFallbacks.addAll(commonMatchingFallbacks)` -- resolves dependency variants to release where no kmkPublicTest variant exists.

---

## App Label Result

The public test build displays as `Komikku KMK` in the Android launcher and system UI.

Implementation:
- Created `app/src/kmkPublicTest/res/values/strings.xml` with:
  ```xml
  <string name="app_name" translatable="false">Komikku KMK</string>
  ```
- Android merges build-type resources with higher priority than dependency module resources. The `app_name` from the `i18n` Moko resources module (`Komikku`) is overridden by the build-type-specific file.
- The AndroidManifest uses `android:label="@string/app_name"`, so the override propagates to the launcher label automatically.
- No `resValue` was used; the source set file approach is cleaner and consistent with how `app/src/debug/res/` and `app/src/beta/res/` are already used in this project.

---

## Updater Behavior

**No code change required.**

`BuildConfig.UPDATER_ENABLED` is set in `defaultConfig` from `Config.enableUpdater`, which is `project.hasProperty("enable-updater")`. This is `false` unless the Gradle property `enable-updater` is explicitly passed at build time. The public test build inherits this behavior:

- Default kmkPublicTest build: updater disabled.
- If someone passes `-Penable-updater` at build time: updater would be enabled and would check `komikku-app/komikku` (the official repo), because `AppUpdateChecker.getGithubRepo()` returns `komikku-app/komikku` for non-preview builds. This would be wrong for the KMK public test build.

For now this is acceptable because:
- The updater is disabled by default.
- No KMK release feed exists.
- The plan does not require creating a release feed.
- If a KMK release feed is created later, a `BuildConfig` guard (`isKmkPublicTestBuildType`) should be added and `getGithubRepo()` should return the KMK repo for that build type.

**Do not enable the updater for kmkPublicTest without also pointing it to a KMK release feed.**

---

## Backup / Restore Behavior

Because `app.komikku.kmk` is a separate Android package:

- Android stores app data per package. `app.komikku.kmk` data does not overlap with `app.komikku` or `app.komikku.dev` data.
- Users installing the public test build on a device with official Komikku or the personal KMK build will get a clean slate.
- To transfer library/settings: export a Komikku backup from the existing app, then restore it in Komikku KMK.
- Extension install state (trusted extensions) is not transferred by backup. Users must re-install extensions in the new app.
- Downloads directory path may differ if scoped storage paths use the package name. Downloads are typically not transferred by backup; users should not expect download history to carry over.
- The taste profile (manga ratings, tag preferences, etc.) is covered by the KMK taste backup (proto 620-626) and will restore correctly if the backup was made with the taste profile option enabled.

---

## Provider / Authority Separation

The AndroidManifest uses `${applicationId}.provider` and `${applicationId}.shizuku` as content provider authorities. Because the public test build uses `app.komikku.kmk`, these automatically become:
- `app.komikku.kmk.provider`
- `app.komikku.kmk.shizuku`

No manifest conflict with official Komikku (`app.komikku.provider`) or the personal debug build (`app.komikku.dev.provider`).

---

## APK Output Path

Gradle-generated output:
```
app/build/outputs/apk/kmkPublicTest/app-universal-kmkPublicTest.apk   (universal)
app/build/outputs/apk/kmkPublicTest/app-arm64-v8a-kmkPublicTest.apk   (ARM64 -- recommended for modern devices)
app/build/outputs/apk/kmkPublicTest/app-armeabi-v7a-kmkPublicTest.apk (ARMv7 -- 32-bit ARM)
app/build/outputs/apk/kmkPublicTest/app-x86-kmkPublicTest.apk         (x86)
app/build/outputs/apk/kmkPublicTest/app-x86_64-kmkPublicTest.apk      (x86_64)
```

For community sharing, the recommended manual filename (copy/rename as needed):
```
Komikku-KMK-PublicTest-v1.13.6-kmk.7.34-debug.apk
```

For most Android phones, share `app-arm64-v8a-kmkPublicTest.apk` (ARM64). Use `app-universal-kmkPublicTest.apk` if device architecture is unknown.

Build command:
```powershell
$env:JAVA_HOME = "C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk17\jdk-17.0.19+10"
.\gradlew.bat :app:assembleKmkPublicTest
```

---

## KmkRecsReleaseNotes

**Not changed.** This is a packaging/build-line change with no feature behavior change. `VERSION_CODE` remains `734`, `VERSION_NAME` remains `KMK-Recs v0.7.34`.

---

## App Behavior Changes

None. The only changes are:
- New build type added to `app/build.gradle.kts` (KMK-marked).
- New `app/src/kmkPublicTest/res/values/strings.xml` for the launcher label.

Recommendation logic, Source Evaluation, backup/restore behavior, migration numbers, and proto field numbers are all unchanged.

---

## Verification Commands And Results

```powershell
.\gradlew.bat spotlessCheck          -- BUILD SUCCESSFUL
.\gradlew.bat :app:testDebugUnitTest -- BUILD SUCCESSFUL (267 actionable tasks)
.\gradlew.bat :app:assembleKmkPublicTest -- BUILD SUCCESSFUL (371 actionable tasks)
.\gradlew.bat :app:assembleDebug     -- BUILD SUCCESSFUL (350 actionable tasks)
```

Package ID confirmation (from `output-metadata.json`):
```
debug variant:         applicationId = "app.komikku.dev"
kmkPublicTest variant: applicationId = "app.komikku.kmk"
```

---

## Files Changed In This Pass

### Code / build changes
- `app/build.gradle.kts` -- added `kmkPublicTest` build type (KMK-marked)
- `app/src/kmkPublicTest/res/values/strings.xml` -- NEW: `app_name = "Komikku KMK"` override

### Documentation changes
- `docs/community/KMK_PUBLIC_TEST_BUILD_LINE_IMPLEMENTATION.md` -- THIS FILE (new)
- `docs/community/KMK_PUBLIC_README_DRAFT.md` -- added public test build section
- `docs/community/KMK_PHASE_3_4_RISK_REGISTER.md` -- R-030 updated (partially mitigated)
- `docs/community/KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION.md` -- addendum added

---

## Remaining Limitations

1. **No release signing**: The public test build uses the local debug keystore. A separate release keystore and signing workflow would be needed for a proper public release.
2. **No update feed**: If updater is later enabled, `getGithubRepo()` must be updated to return a KMK-specific repo, not `komikku-app/komikku`.
3. **Launcher icon not differentiated**: The `kmkPublicTest` build uses the same launcher icon as the personal build. Only the label differs (`Komikku KMK`). A distinct icon would make the builds easier to distinguish on-device.
4. **No automated APK rename**: APK must be manually copied/renamed for distribution. See recommended filename above.
5. **applicationId for personal release build**: `app.komikku` (no suffix) still matches upstream Komikku's release package ID. The personal release build would still overwrite official Komikku if both are installed. This is intentional and unchanged from before.

---

## Final Readiness Verdicts

```
Private/personal build readiness: Ready -- keeps app.komikku and remains the user's update-style APK.
Public test build readiness: Ready for limited Reddit/community testing -- uses app.komikku.kmk and installs separately, with backup/restore warnings documented.
Public stable release readiness: Not ready -- still needs signed release process, public release channel, issue/support process, and long-term maintenance decision.
Upstream contribution readiness: Not ready -- feature scope remains too broad for direct upstream contribution.
```

