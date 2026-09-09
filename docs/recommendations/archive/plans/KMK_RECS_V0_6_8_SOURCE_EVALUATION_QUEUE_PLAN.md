# KMK-Recs v0.6.8 Source Evaluation Queue Plan

Status: planning. Do not implement until the user explicitly approves or provides this plan to Claude for implementation.

Target version: `KMK-Recs v0.6.8`

## Purpose

Design a one-at-a-time source/extension evaluation queue that can temporarily install candidate extensions, inspect the actual manga/catalog behavior exposed by their sources, store a local evaluation record, then remove or disable the extension when evaluation is complete.

This is intended to solve the major limitation of the current non-installed source recommendation and explicit-source blocking systems:

- before install, Komikku mostly knows source names, extension names, base URLs, language, repo, package name, NSFW flag, and source IDs;
- it does not know what manga the source actually returns;
- therefore recommendations and explicit-source blocking are currently mostly metadata/name based;
- source names can be misleading, irregular, translated, stylized, or unrelated to the actual catalog quality.

The new system should gather real, local, bounded evidence from source results so the app can recommend, reject, block, or prioritize sources more intelligently.

## Versioning

Use:

`KMK-Recs v0.6.8`

Reason:

- This continues the v0.6 extension/source management and non-installed-source discovery track.
- It builds on Sources To Try, source like/dislike, explicit-source filtering, and selected install/uninstall features.
- Do not jump to a new major topic number.

Expected debug APK naming pattern:

`Komikku-v1.13.6-kmk.6.8-debug.apk`

Only use this exact name if source metadata confirms it is the next correct version.

## User Idea Restated

The user wants an evaluation mode where Komikku can:

1. select a batch size, such as 10, 25, 50, 100, or a custom amount;
2. process candidate non-installed extensions one at a time;
3. temporarily install an extension;
4. inspect the sources inside it;
5. run lightweight catalog/search probes to understand what kind of manga it actually has;
6. store summarized evaluation data locally;
7. classify whether the source is useful, weak, explicit-heavy, ecchi-only, or worth trying;
8. uninstall or disable the trial extension afterward;
9. continue to the next extension until the batch completes, is paused, or is cancelled.

Important clarification:

- Batch size means number of extensions to evaluate in this run.
- Batch size does **not** mean parallel installs.
- Evaluation should be one extension at a time by default.

## Why One-At-A-Time Is Preferred

Do not install 25/50/100 extensions at once.

One-at-a-time evaluation is preferred because it:

- minimizes memory and storage pressure;
- avoids loading many unwanted sources temporarily;
- avoids flooding Android with install/uninstall operations;
- makes pause/resume/cancel practical;
- makes crash recovery simpler;
- makes it clear which extension failed;
- avoids polluting recommendation/search state with many temporary extensions at once;
- allows the app to uninstall/reject a poor source before moving on.

## Installer Mode Requirement

This feature depends heavily on installer behavior.

Existing installer modes:

`app/src/main/java/eu/kanade/domain/base/BasePreferences.kt`

```kotlin
enum class ExtensionInstaller {
    LEGACY,
    PACKAGEINSTALLER,
    SHIZUKU,
    PRIVATE,
}
```

Existing preference:

`app/src/main/java/eu/kanade/domain/base/ExtensionInstallerPreference.kt`

- default is `PACKAGEINSTALLER` unless MIUI requires `LEGACY`;
- `SHIZUKU` is automatically rejected back to default if Shizuku is not installed;
- entries include Shizuku and Private where allowed.

Existing installer behavior:

`app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionInstaller.kt`

- `LEGACY`: starts Android install activity and normally requires user confirmation;
- `PACKAGEINSTALLER`: uses foreground service/package installer and may still need system UI/permission;
- `SHIZUKU`: uses Shizuku service and can install more smoothly after permission is granted;
- `PRIVATE`: installs into Komikku's private extension storage without system package install.

Existing Shizuku behavior:

`app/src/main/java/eu/kanade/tachiyomi/extension/installer/ShizukuInstaller.kt`

- checks Shizuku binder;
- requests Shizuku permission if needed;
- uses package installer status callbacks to continue queue.

## Installer Recommendation

For source evaluation queue:

### Best Modes

1. `PRIVATE`
   - Best for temporary evaluation if compatible.
   - Installs extension into Komikku private extension storage.
   - Uninstall can remove private extension directly without Android uninstall prompt.
   - Most appropriate for temporary probe/evaluate/remove behavior.

2. `SHIZUKU`
   - Good for smoother install flows if Shizuku is installed and permission granted.
   - Still needs careful uninstall handling because normal installed packages may still need package uninstall behavior unless a Shizuku uninstall path exists or is added.

### Risky Modes

3. `PACKAGEINSTALLER`
4. `LEGACY`

These may require repeated Android confirmation prompts. They should be allowed only for small batches or after a warning.

## Installer UX Requirements

Before starting an evaluation batch, show an installer readiness section/dialog:

- Current installer mode.
- Whether Shizuku is installed and available.
- Whether Private installer is available in this build.
- Whether the selected batch size is safe with the current installer mode.

Recommended behavior:

- If current installer is `PRIVATE`: allow normal evaluation.
- If current installer is `SHIZUKU`: allow normal evaluation if Shizuku is installed/permission ready.
- If current installer is `PACKAGEINSTALLER` or `LEGACY`:
  - allow only small evaluation batch by default, such as 5 or 10;
  - warn that Android may ask for install/uninstall confirmation for each extension;
  - recommend switching to Shizuku or Private installer.

## Can Shizuku Be Applied By Default?

Do **not** silently force Shizuku by default.

Reasons:

- Shizuku must be installed and running.
- The app must have Shizuku permission.
- Some devices/users will not have it.
- Automatically changing installer mode could surprise users.

Recommended implementation:

- Add a button in evaluation setup:
  - `Use Shizuku installer`
  - enabled only when Shizuku is installed/running or explain how to set it up.
- When clicked, set `basePreferences.extensionInstaller()` to `BasePreferences.ExtensionInstaller.SHIZUKU`.
- If Shizuku is missing, show existing Shizuku unavailable dialog or navigate to the existing extension installer settings.
- If Shizuku permission is needed, trigger the normal Shizuku permission flow by starting a small readiness check or by relying on first install.

Also add:

- `Use private installer for evaluation` if `PRIVATE` is available in this build.

Important:

- The evaluation queue should read the active installer mode before starting.
- It should not start a large batch if the installer mode will create dozens/hundreds of confirmation prompts.

## Private Installer Evaluation Mode

The best long-term option is a dedicated "temporary/private evaluation install" path.

Potential behavior:

- Download extension APK.
- Install it via `PRIVATE`.
- Load its sources.
- Evaluate.
- Remove the private extension file.
- Notify source manager/extension manager to refresh.

Advantages:

- no system APK install clutter;
- no Android uninstall prompt;
- better for temporary scanning;
- easy cleanup;
- safer for large evaluation batches.

Implementation caution:

- Claude must inspect whether the `PRIVATE` installer can load all extension types reliably.
- If `PRIVATE` is debug-only or hidden in release builds, document that limitation.
- If the user uses debug APKs, Private may be available and ideal.

## Feature Scope

This feature should be built as a staged system, not a giant one-shot change.

Recommended stages:

### Stage 1: Planning / Database / UI Skeleton

- Add source evaluation data model/table.
- Add evaluation settings screen/state.
- Add installer readiness checks.
- Add queue state model.
- Add basic "Evaluate N" controls.
- Do not run deep source probes yet.

### Stage 2: One-At-A-Time Trial Evaluation

- Install one extension.
- Wait for extension/source manager to expose sources.
- Collect basic source metadata.
- Run tiny sample probes.
- Store evaluation.
- Uninstall/remove private extension.
- Continue to next.

### Stage 3: Scoring Integration

- Use stored evaluations in Sources To Try.
- Use stored evaluations in For You source priority.
- Use explicit/evaluation scores in explicit blocking.
- Show evaluated badges/statuses in settings.

### Stage 4: Pause/Resume/Retry

- Persist queue state.
- Resume after app restart.
- Retry failed extension.
- Skip recently evaluated sources.

## UI Proposal

Location:

- Recommendation Settings > Sources To Try section, or
- a new nested screen from Recommendation Settings:
  - `Source evaluation`

Preferred:

Add a `Source evaluation` screen, because this is too complex for the main settings list.

Controls:

- `Evaluate 10`
- `Evaluate 25`
- `Evaluate 50`
- `Evaluate 100`
- custom amount later
- `Skip already evaluated`
- `Skip disliked/rejected`
- `Wi-Fi only`
- `Charging recommended` or `Only while charging`
- `Use private installer`
- `Use Shizuku installer`

Progress UI:

- `Evaluating 12 / 25`
- Current extension name
- Current source name
- Current phase:
  - downloading
  - installing
  - loading sources
  - sampling popular/latest
  - running liked-title probes
  - scoring
  - uninstalling/cleanup
- pause
- cancel
- view results

## Candidate Selection

Candidate pool:

- available non-installed extensions from extension repos;
- not dismissed/rejected;
- not already installed;
- not untrusted;
- matching recommendation language settings;
- matching broad NSFW/explicit block preferences unless user explicitly includes them for evaluation.

Priority order:

1. liked non-installed suggestions;
2. similar-to-installed source names;
3. same repo as liked/high-quality installed sources;
4. matching preferred languages;
5. unevaluated sources;
6. stale evaluations older than a configurable time.

Avoid:

- known rejected sources;
- blocked explicit sources when explicit block is enabled, unless user is running an explicit-classification audit;
- recently failed sources unless retry requested.

## Evaluation Probe Design

The evaluation must be bounded and respectful.

For each installed trial source:

### Basic Metadata

Collect:

- extension package/signature;
- extension name;
- source id;
- source name;
- language;
- base URL when available;
- isNsfw;
- repo name/url;
- source count in extension.

### Catalog Samples

Use very small caps:

- popular manga page 1, if supported;
- latest manga page 1, if supported;
- up to 10-20 items total per source initially.

### Search Probes

Use user-profile-driven probes:

- a few loved manga titles;
- a few liked manga titles;
- preferred tags/genres if source supports filters/search;
- maybe 3-5 total searches per source, not dozens.

### Content Signals

From returned manga where available:

- title;
- description;
- genre/tags;
- status;
- author/artist if available;
- chapter count only if already included or cheap to fetch.

Do not fetch every manga detail page unless needed. Prefer metadata already returned in listing/search result.

## Evaluation Scores

Store source-level scores:

- `qualityScore`
- `recommendationFitScore`
- `searchReliabilityScore`
- `explicitContentScore`
- `ecchiSignalScore`
- `preferredTagMatchScore`
- `blockedTagMatchScore`
- `likedTitleMatchScore`
- `resultDiversityScore`
- `errorScore`

Verdicts:

- `strong_fit`
- `worth_trying`
- `weak`
- `poor_search`
- `explicit_heavy`
- `ecchi_heavy`
- `rejected`
- `error`
- `needs_manual_review`

Important:

- Explicit-heavy and ecchi-heavy must remain separate.
- Explicit-heavy should help the porn/hentai blocker.
- Ecchi-heavy should not be treated as explicit-heavy.

## Data Storage

Create persistent storage, likely SQLDelight, for source evaluations.

Suggested table:

`source_evaluation`

Fields:

- `source_id`
- `extension_pkg_name`
- `signature_hash`
- `extension_name`
- `source_name`
- `lang`
- `base_url`
- `repo_name`
- `repo_url`
- `evaluated_at`
- `evaluation_version`
- `sample_count`
- `search_count`
- `search_success_count`
- `popular_count`
- `latest_count`
- `liked_title_match_count`
- `preferred_tag_match_count`
- `blocked_tag_match_count`
- `explicit_signal_count`
- `ecchi_signal_count`
- `quality_score`
- `fit_score`
- `explicit_score`
- `ecchi_score`
- `verdict`
- `error_message`

Also consider:

`extension_evaluation`

For extension-level summary across multiple sources.

## Queue Storage

For pause/resume:

`source_evaluation_queue`

Fields:

- queue id
- started at
- updated at
- target count
- completed count
- status
- current extension key
- candidate list serialized or child table
- installer mode used
- options flags

This can be deferred if v0.6.8 is too large. A first version can keep queue in memory and document that app restart cancels evaluation.

## Integration With Recommendations

Once evaluations exist:

Sources To Try ranking should use:

- evaluated high-fit sources first;
- unevaluated sources as "Needs testing";
- explicit-heavy sources hidden if explicit block enabled;
- poor-search/rejected sources hidden or ranked low;
- ecchi-heavy sources visible unless user separately blocks ecchi.

For You should use:

- evaluated fit scores as source-priority boost;
- error/poor-search scores as penalty;
- explicit-heavy filter when explicit block enabled;
- rejected sources excluded.

Recommendation Settings should show:

- evaluated badge/status;
- last evaluated time;
- verdict;
- option to reset evaluation;
- option to reject/approve source.

## Interaction With Existing Features

### v0.6.0-0.6.7 Sources To Try

The existing Sources To Try section remains metadata-based for unevaluated sources.

New behavior:

- if evaluation exists, use evaluation data;
- if no evaluation exists, show "Needs testing";
- allow user to start evaluation for one or more suggestions.

### v0.6.7 Explicit Source Filter

The explicit source filter should eventually use evaluation results:

- name/package classifier remains a fast pre-install heuristic;
- source evaluation can override/confirm explicit-heavy status;
- ecchi-heavy should not be blocked by explicit filter.

### v0.6.6 Selective Uninstall

If trial installed via normal system installer and kept installed, selected uninstall can help clean up. But preferred evaluation cleanup should avoid needing manual uninstall where possible.

## Safety Limits

Default limits:

- one extension at a time;
- one source at a time inside extension;
- max 10-20 listing items per source;
- max 3-5 search probes per source;
- batch default 10 or 25;
- require confirmation for 50+;
- strongly warn/block 100+ unless Private/Shizuku mode is active.

Avoid 500/1000 in first implementation.

500/1000 should be treated as advanced/custom and only considered after:

- pause/resume exists;
- Private/Shizuku evaluation is proven stable;
- rate limiting exists;
- battery/network safeguards exist;
- failure recovery exists.

## Rate Limiting And Device Safeguards

Add:

- delay between extensions;
- delay between source probes;
- Wi-Fi-only option;
- optional charging-only option;
- stop when battery is low if easy to access;
- cancel button;
- skip on repeated errors;
- per-host cooldown if many extensions hit same website.

## Failure Handling

Each extension evaluation should end in one of:

- completed;
- rejected;
- skipped;
- failed install;
- failed load;
- failed probe;
- cleanup failed.

One failed extension must not stop the whole queue unless the failure is installer-wide.

If cleanup fails:

- record cleanup failure;
- surface it to user;
- do not continue installing hundreds of extensions without warning.

## Uninstall / Cleanup Strategy

Preferred cleanup:

- If installed via `PRIVATE`, call private extension removal directly.
- If installed via system package installer/Shizuku, use the existing uninstall flow or add a Shizuku uninstall path only if safe and already compatible.

Important:

- Current `ExtensionInstaller.uninstallApk(pkgName)` uses Android uninstall intent if the package is system-installed.
- It removes private extension directly only if Android package is not installed.
- For automatic large evaluation runs, private installation is much better.

Future enhancement:

- Add explicit Shizuku uninstall support if needed.
- Do not assume Shizuku uninstall exists just because Shizuku install exists.

## User Approval / Control

Before starting:

- show summary of candidate count;
- show installer mode;
- show expected prompts risk;
- show network/battery warning;
- allow user to choose evaluation count.

After finishing:

- show summary:
  - evaluated count;
  - strong fit count;
  - worth trying count;
  - explicit-heavy count;
  - rejected/error count;
- allow user to review results.

## Documentation Requirements

When implemented, Claude must create:

`docs/recommendations/KMK_RECS_V0_6_8_SOURCE_EVALUATION_QUEUE_IMPLEMENTATION.md`

And update:

- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`
- `RECOMMENDATION_VERSIONING.md`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

Documentation must describe:

- installer mode behavior;
- Shizuku/private requirements;
- batch size meaning;
- one-at-a-time queue behavior;
- evaluation probes;
- scoring fields;
- limitations;
- test results;
- APK produced.

## Open Questions For Implementation

Claude should answer these before coding:

1. Is `PRIVATE` installer available in the target debug APK and stable enough for temporary source evaluation?
2. Can private-installed extensions be loaded/unloaded repeatedly without app restart?
3. Is there an existing Shizuku uninstall path, or only Shizuku install?
4. How does source manager refresh after private extension removal?
5. Which source APIs can be safely probed without requiring heavy metadata fetches?
6. Should first implementation persist queue state, or keep queue in memory only?
7. Where should the UI live: Recommendation Settings, Extensions screen, or a nested Source Evaluation screen?

## Recommended First Implementation Scope

For a realistic first implementation, do **not** build the full 500/1000 source auditor immediately.

Build:

- nested Source Evaluation screen;
- installer readiness check;
- evaluate 10/25/50;
- one-at-a-time private/Shizuku-preferred evaluation;
- source-level evaluation table;
- basic metadata + tiny popular/latest/search probes;
- cleanup after each extension;
- results list;
- integration into Sources To Try ranking and explicit-heavy blocking.

Defer:

- 500/1000 batch sizes;
- fully persistent pause/resume queue;
- Shizuku uninstall if not already supported;
- advanced per-host rate limiting;
- automatic source priority rewriting.

## Acceptance Criteria

This feature is ready only when:

- User can start a bounded evaluation batch.
- Evaluation processes extensions one at a time.
- The app clearly warns if installer mode is prompt-heavy.
- Private/Shizuku installer paths are favored for evaluation.
- The app stores source-level evaluation records.
- The app separates explicit-heavy and ecchi-heavy signals.
- Sources To Try can use evaluation results where available.
- Explicit-source filtering can use evaluation results where available.
- Trial extensions are cleaned up safely.
- User can pause/cancel or at least cancel safely in first version.
- Failures do not crash the queue.
- Documentation and release notes are updated to `KMK-Recs v0.6.8`.
- A debug APK is produced using current versioning rules.


