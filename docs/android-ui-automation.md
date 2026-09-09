# Android UI Automation

This project uses a host-side capture coordinator at `tools/capture_coordinator.py` and an official AndroidX UI Automator test runner for Evaluation Mode evidence capture. The legacy `tools/android_ui.py` helper remains available for bounded diagnostics only; it is not the preferred route-execution engine.

Read `docs/community/KMK_ANDROID_UI_AUTOMATION_CAPTURE_WORKFLOW_CORRECTION_2026-07-22.md` before capture work. The coordinator owns privacy policy, route status, sidecars, and review state. AndroidX UI Automator owns semantic device interaction. Python `uiautomator2` is not required.

## Project defaults

- Debug package: `app.komikku.dev`
- Source tree: the repository root containing this document
- ADB: the bundled `.tools/android-sdk/platform-tools/adb.exe` when present, otherwise `ADB_PATH` or `adb` on `PATH`
- Artifacts: `artifacts/android/` (keep local artifacts out of commits)
- Legacy diagnostic routes: `tools/routes.json`
- Capture inventory: `tools/capture_routes.json`

## First use

From the repository root:

```powershell
python tools/android_ui.py status
python tools/android_ui.py current-activity
python tools/android_ui.py elements
```

If more than one device is connected, set one explicit target before running a route:

```powershell
$env:ANDROID_SERIAL = "DEVICE_SERIAL"
```

The helper refuses to choose arbitrarily when no explicit serial is set and multiple devices are online.

The host tooling has no Python UI-automation dependency. Device interaction belongs to the AndroidX UI Automator test runner configured in the app's `androidTest` source set.

## Selector priority

Use selectors in this order:

1. Resource ID or stable Compose test tag
2. Content description
3. Exact visible text
4. Partial visible text
5. Compact element inspection
6. Coordinates only as a documented fallback

Normal output is compact. Use `dump` only when the raw UI hierarchy is specifically needed.

## Commands

```powershell
python tools/android_ui.py status
python tools/android_ui.py elements
python tools/android_ui.py click-id "app.komikku.dev:id/example"
python tools/android_ui.py click-description "Search"
python tools/android_ui.py click-text "For You"
python tools/android_ui.py scroll-to-text "Source Priority" --click
python tools/android_ui.py input "example" --clear
python tools/android_ui.py back
python tools/android_ui.py home
python tools/android_ui.py app-start
python tools/android_ui.py app-stop
python tools/android_ui.py assert-visible --text "For You"
python tools/android_ui.py screenshot
python tools/android_ui.py route for-you
```

Every route has bounded steps and a final assertion. A failed step stops the route; the helper does not blindly continue or retry indefinitely.

## Screenshots and logs

Screenshots are evidence for visual questions, not a replacement for semantic navigation. Save them under `artifacts/android/screenshots/` unless a task explicitly requires a separate evidence directory.

Do not stream unrestricted Logcat. Clear logs before a single reproduction, then collect a bounded crash buffer or app-process-filtered excerpt. Do not inspect unrelated applications or private device content.

## Safety

The helper does not expose uninstall, data clearing, factory reset, account changes, purchases, permission escalation, or external communication commands. Do not add destructive commands without an explicit, separately reviewed requirement. Coordinate fallback requires a current screenshot, a documented reason that semantics are unavailable, and immediate verification.

## Adding testability

For important Compose controls, prefer stable function-based tags and meaningful accessibility descriptions:

```kotlin
Modifier
    .testTag("source_priority_button")
    .semantics { contentDescription = "Open source priority" }
```

Use stable domain IDs for repeated manga cards. Do not put private user data, source credentials, or transient list positions in tags or descriptions. Keep user-facing accessibility wording meaningful rather than adding labels only for automation.

## Adding routes

Add only routes verified against the current app. Each route should:

1. begin from a known state where practical;
2. use a semantic selector for each step;
3. wait through asynchronous transitions with a finite timeout;
4. stop on the first missing selector;
5. assert the destination;
6. avoid destructive actions.

## Limitations

The helper is intentionally small and uses Android's UI Automator XML as its default transport. Custom canvas content may still require visual inspection. The current route file contains only safe routes that were observed on the connected Komikku debug build; extend it as screens gain stable semantics.
