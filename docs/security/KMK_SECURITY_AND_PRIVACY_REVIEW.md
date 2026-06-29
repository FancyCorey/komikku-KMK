# KMK Security And Privacy Review

Date: 2026-06-28

Status: Phase 4 review, reconciled against code after KMK-Recs v0.7.20. Several originally identified risks are now mitigated; remaining risks are noted below.

Baseline: Komikku v1.13.6.

Related: `docs/community/KMK_PHASE_3_4_DATABASE_SECURITY_PRIVACY_AUDIT_PLAN.md`

---

## Executive Summary

The KMK fork has a significantly higher security/privacy surface than stock Komikku. The three highest-risk areas are:

1. **Source Evaluation** â€” installs, loads, and executes extension code from third-party repositories as part of a background job. Process death and Shizuku/current-mode cleanup remain security-sensitive, but v0.7.16 adds installer-mode consent reset, accurate prompt-required cleanup status, in-screen cleanup actions, and process-death leftover warnings.

2. **OCR text storage** â€” recognized manga page text is stored in plaintext in the app's private SQLite database. v0.7.16 adds a pre-indexing privacy warning and structural tests confirming OCR text is excluded from backup/sync/export. The index can still grow large.

3. **Recommendation bundle import** — JSON files from potentially untrusted sources are validated with explicit size/count limits. Import only writes library entries (not ratings or seen state). v0.7.16 adds a 200 ms delay between bulk add attempts to reduce network/source pressure. v0.7.17: ambiguous extension matches (multiple available extensions for the same package name) now surface as `AmbiguousSource` state — no silent first-pick. Missing-source extension install still uses the user's global installer preference.

All three areas are correctly marked experimental. Several gaps need addressing before community sharing.

---

## Source Evaluation â€” Installer And Lifecycle

### Installer Modes

Three modes in `SourceEvaluationInstallerPolicy.InstallerMode`:

| Mode | Install | Uninstall | Max Batch | Cleanup Silent |
|---|---|---|---|---|
| `PRIVATE` | Into app's private storage directory | Silent via `extensionManager.uninstallExtension` | 100 | Yes |
| `SHIZUKU` | As a system package via Shizuku | **Prompted** â€” Android system uninstall prompts | 50 | No (`cleanupIsSilent = false`) |
| `CURRENT` | Delegates to user's global installer preference | If global = PRIVATE: silent. Otherwise: prompted per extension | 10 | Depends on global pref |

The user's global installer preference is never mutated during evaluation. An `installerOverride` is passed to `ExtensionManager.installExtension` for the evaluation run only.

Only PRIVATE-installed extensions (`isShared = false`) are removed silently. SHIZUKU/system-installed extensions (`isShared = true`) reach `PromptRequired` in `SourceEvaluationCleanupPolicy`. In standard evaluation paths (`startEvaluation`, `startReassessUpdated`), `promptHeavyCleanupAllowed = false`, meaning Android prompts are not launched automatically during the run. As of v0.7.16, prompt-required cleanup is accurately recorded and the summary card exposes an in-screen action to trigger the system uninstall prompts afterward.

### Evaluation Lifecycle

Install â†’ probe â†’ uninstall are sequential coroutine steps with explicit delays (1500 ms between extensions, 500 ms between sources, 300 ms between search probes). The `finally` block in `evaluateExtension` always attempts `cleanupExtension` and clears the probe marker, so partial runs do trigger cleanup â€” unless the process is killed.

### Process Death

The job runs as a foreground `CoroutineWorker` (`SourceEvaluationJob`). Candidates and options are stored in an in-memory singleton `SourceEvaluationJobState`. If the process is killed:

- The WorkManager task fails immediately on next `doWork()` call (candidates are null).
- A `source_evaluation_state_lost_error` failure is written to the UI.
- The `finally` block does NOT run â€” cleanup of any in-progress extension install does not happen.
- An extension may be left installed if the kill occurred after install but before cleanup.

This is partially mitigated by startup recovery: the probe marker written before risky operations is read at app startup by `SourceEvaluationStartupRecovery`, which quarantines the extension and writes it to the load-block table. However, this does not uninstall the extension â€” it only prevents it from loading.

### Crash Recovery And Quarantine

Probe marker mechanism: before every risky operation (install, load, each network probe), a record is written to `source_evaluation_probe_marker` (singleton row, id=1). On app startup:

- Marker present and < 24 hours old: extension added to `source_evaluation_unsafe_source` quarantine and `unsafe_extension_package` load block. Prevented from re-evaluation and re-loading.
- Marker present and > 24 hours old: cleared without quarantine.
- Marker absent: no action.

`KnownUnsafeExtensionPackages` seed: one entry (Digital Comic Museum, known SIGSEGV/stack overflow) is always applied at startup, blocking it from loading even without a probe marker.

`unsafe_extension_package` load block is enforced in `ExtensionLoader.loadExtensions` and `loadExtensionFromPkgName` before class loading, via `ExtensionLoadSafetyPolicy.shouldBlock(pkgName)`. The user can inspect and remove individual quarantine entries or clear all from the diagnostics UI.

### Risk: 24-Hour Stale Threshold

If the user suspends their device mid-evaluation and resumes more than 24 hours later, a legitimate crash marker is treated as stale and cleared without quarantine. This is a minor safety regression edge case (SEC-10).

---

## Source Evaluation â€” Consent And Warnings

### Pre-Run Consent Dialog

Added in v0.7.11. `SourceEvaluationConsentPolicy.isConsentRequired(consentGiven)` returns `true` when `consentGiven == false`.

Check is applied in all three start paths: `startEvaluation`, `startReassessUpdated`, `continueEvaluation`. Evaluation cannot start until the user confirms. On confirmation, `sourceEvaluationConsentGiven = true` is stored in `SourcePreferences` (key: `"source_evaluation_consent_given"`, default `false`). The pending action (`pendingConsentAction`) is then executed.

**Dialog title:** "Source Evaluation â€” Advanced Feature"

**Dialog body:** "Source Evaluation temporarily installs extension packages, loads source definitions, runs network probes, scores results, and then attempts to uninstall them. Private mode (recommended): installs and removes silently. Current or Shizuku mode: Android may show install/uninstall prompts per extension. Only non-installed extensions are evaluated. Your library, ratings, and already-installed extensions are not changed unless you explicitly install a result. You can view this warning again from the Copy Diagnostics area."

A "View evaluation warning" text button re-surfaces the dialog without triggering evaluation.

### Consent Mode-Change Re-Prompt (SEC-03) - Fixed In v0.7.16

Once `sourceEvaluationConsentGiven = true`, the consent dialog never reappears. If the user switches from PRIVATE (silent cleanup) to SHIZUKU (system install, prompt-required cleanup), the consent text describing "installs and removes silently" no longer matches. The prompt-heavy warning dialog partially covers this for batch size > 1, but the full consent text is not re-surfaced.

### Prompt-Heavy Warning

A separate `AlertDialog` (`showPromptHeavyWarningDialog`) is shown before starting evaluation in SHIZUKU or CURRENT (non-private) mode with batch size > 1. The user can switch to Private or continue with prompts.

### Post-Batch Cleanup Warnings

Shown in `EvaluationSummaryCard` after each batch:

- If `queueState.promptRequiredCleanupCount > 0`: error-colored message "N extension(s) require a manual uninstall prompt. Open Browse > Extensions to complete cleanup."
- If `queueState.cleanupFailedCount > 0`: separate error message with count.

As of v0.7.16, the prompt-required warning includes an in-screen cleanup action for left-behind extensions, avoiding the older requirement to manually navigate to Browse > Extensions.

---

## Source Evaluation â€” Security Risks

| ID | Risk | Severity | Current State |
|---|---|---|---|
| SEC-01 | Shizuku-installed extension left behind on process death | Critical | **Mitigated v0.7.16.** Probe marker triggers quarantine/load-blocking and records `sourceEvaluationLeftoverPkg` when the package is still installed; Source Evaluation shows a persistent uninstall action. |
| SEC-02 | `promptHeavyCleanupAllowed = false` in standard paths | High | **Mitigated v0.7.16.** System prompts are still not launched mid-run, but cleanup status is recorded and the summary exposes an in-app action to trigger uninstall prompts afterward. |
| SEC-03 | One-time consent does not re-trigger on installer mode change | Medium | **Mitigated v0.7.16.** Switching to SHIZUKU or CURRENT resets consent so the warning reappears. |
| SEC-04 | `SourceEvaluationJobState` in-memory only | High | Candidates not serialized to WorkManager input data. Process restart between `start()` and `doWork()` causes immediate graceful failure. No silent state loss â€” failure is visible. |
| SEC-05 | 24-hour stale marker threshold | Low | Device suspended > 24 hours mid-evaluation: crash marker cleared without quarantine. |

---

## OCR â€” Storage And Privacy

### What Is Stored And Where

`ocr_indexed_page` table in the app's private SQLite database. Key fields per row:

- `manga_title`, `chapter_name`: personal library content
- `raw_text`: raw OCR output from ML Kit recognition
- `normalized_text`: search-normalized form for query matching
- `indexed_at`, `ocr_status`, `recognized_text_length`, `recognized_word_count`

This is the full recognized text of every indexed manga page. The database grows proportionally with indexed chapter count.

### Can The User Delete It?

Yes. `OcrIndexRepository` exposes:
- `deleteByManga(mangaId)`
- `deleteByChapter(chapterId)`
- `deleteAll()`
- `deleteEmptyAndFailed()`
- `deleteOldEngineRows(currentEngineVersion)`

The OCR search screen has "Index all" and "Force re-index" confirm dialogs. Whether per-manga deletion is accessible from the manga detail or library UI was not confirmed in this audit.

### Backup/Sync/Export Exclusion

Confirmed: `ocr_indexed_page` does not appear in any `BackupCreator`, `TasteBackupCreator`, `SyncManager`, or `SyncService` backup path. No reference to OCR tables in `exh/recs/share/` (bundle export). OCR text is correctly local-only.

### OCR Logs

Page-level errors are logged at `WARN` level including `manga.title`, `chapter.name`, and `pageIndex` â€” e.g., `"OCR: page error manga=${pageRef.manga.title} ch=${pageRef.chapter.name} page=${pageRef.pageIndex}"`. These appear in debug logcat only, not in the clipboard diagnostics export.

### Privacy Gaps

| ID | Risk | Severity | Current State |
|---|---|---|---|
| SEC-06 | No pre-indexing privacy warning | Medium | **Mitigated v0.7.16.** The OCR index confirmation string now discloses that recognized text is stored locally and not sent to a server. |
| SEC-07 | `raw_text` and `normalized_text` stored in plaintext | Low | Acceptable on unrooted devices (app sandbox). On rooted devices or with device compromise, the full recognized text of all indexed pages is accessible without decryption. No encryption is applied. |
| SEC-08 | Per-manga deletion not confirmed accessible from library UI | Low | Deletion API exists in the repository but surface-level audit did not confirm a per-manga delete action from the manga detail or library screen. |

---

## Recommendation Bundle Import/Export

### Confirmed Schema And Limits

From `RecommendationBundleValidator`:

| Property | Value |
|---|---|
| Schema ID | `"kmk.recommendation.bundle"` |
| Schema version | `1` (integer) |
| Max file size | 2 MB |
| Max items | 500 |
| Max sources | 200 |

All limits enforced before any further processing. Schema ID and version mismatches return typed error results (`WrongSchema`, `UnsupportedVersion`).

### What Import Can Write

Import calls `RecommendationBundleLibraryAdder.addToLibrary`. It writes:
- `favorite = true` (library entry)
- manga categories
- manga metadata if `libraryPreferences.fetchMetadataOnAdd()` is true (network fetch)

Import does **not** write:
- manga ratings / taste profile entries
- seen/read markers
- source evaluation records
- any other KMK preference data

Bundle items have a `score` field used for display only â€” not written to taste tables on import.

### Missing-Source Install

When a bundle item's source is not installed, the item is shown as `MissingSource` with an optional `Extension.Available` reference. The user taps an explicit install button within the import preview screen. This calls `extensionManager.installExtension(ext)` with no installer override â€” it uses the user's global installer preference. This is consistent with normal extension install behavior.

### Bundle Import Risks

| ID | Risk | Severity | Current State |
|---|---|---|---|
| SEC-09 | Network fetch triggered for up to 500 items without rate limiting | Medium | **Partially mitigated v0.7.16.** `RecommendationBundleImportScreenModel.addSelected()` delays 200 ms between items. A user-facing network-impact warning is still not present. |
| SEC-10 | Missing-source install uses global installer preference | Low | Extension installs from bundle import use the user's configured installer (possibly CURRENT with Android prompts), same as normal extension install. Consistent but worth noting for Shizuku users. |

### Bundle Security Posture

The validator, limits, schema check, and preview-before-add flow provide a reasonable trust boundary. A malicious bundle cannot write ratings, seen state, or source evaluation data. The main risk is large import causing library pollution, which the 500-item limit and preview address.

---

## Extension Installer Changes

### What Was Changed

`ExtensionInstaller.kt`:
- `downloadAndInstall` extended with `installerOverride: BasePreferences.ExtensionInstaller? = null`, threaded through to `installApk`. Allows evaluation runner to use a different installer mode without mutating the user's global preference.
- Download ID changed to `pkgName + "_" + signatureHash` for uniqueness per signature hash.
- Temp file cleanup on download error added.
- `CoroutineScope` with `SupervisorJob` and `ConcurrentHashMap` for active job lifecycle management.

`ExtensionLoader.kt`:
- Before loading extensions, all packages are checked against `ExtensionLoadSafetyPolicy.shouldBlock(pkgName)`. Blocked packages produce `LoadResult.Blocked` and are excluded from class loading.
- Both batch and single-extension load paths check the safety policy.

`ExtensionLoadSafetyPolicy.kt` (new):
- Pure stateless object. `shouldBlock(pkgName, userBlockedPackages)` returns true if in `KnownUnsafeExtensionPackages` or in the user-maintained block set.

`LoadResult.kt`:
- New `Blocked` subclass added.

`ExtensionManager.kt`:
- Installer override parameter threading.
- `isShared` detection for deciding cleanup mode.

### Safety Assessment

The installer override is correctly scoped â€” it does not modify the user's global preference. The load safety check is enforced at the extension loader level, which is the correct enforcement point. The safety policy is pure and testable.

The primary remaining Source Evaluation risk is no longer invisible cleanup: v0.7.16 exposes cleanup actions. The remaining risk is inherent to evaluating third-party extension code and to Android prompt-based cleanup for system-installed extensions.

---

## Explicit Source Classifier

### Classification Method

`ExplicitSourceClassifier` uses three signals:

1. **Name substrings** (`isExplicitName(name)`): Checks lowercase source name for 14 substrings: `hentai`, `porn`, `porno`, `pururin`, `tsumino`, `8muses`, `hbrowse`, `luscious`, `doujins`, `multporn`, `xxx`, `erotic`, `smut`, `adult comic`, `adult manga`, `adult manhwa`, `adult manhua`.

2. **Package name substrings** (`isExplicitPackageName(pkgName)`): Checks for `hentai` or `porn` only. Narrower than the name check.

3. **Hardcoded source IDs** (`isExplicitSourceId(sourceId)`): Checks a compile-time set: NHentai, Pururin, Tsumino, 8Muses, HBrowse, and all EHentai/ExHentai extension source IDs.

### Ecchi vs. Explicit Distinction

Deliberate and correctly documented. The classifier does NOT trigger on `ecchi`, `nsfw`, `mature`, `lewd`, or `adult` alone. The `isNsfw` extension flag is the broader signal; this classifier targets only clearly explicit pornographic/hentai sources. Source Evaluation surfaces `ECCHI_HEAVY` and `EXPLICIT_HEAVY` as distinct verdicts.

### Limitations

| Limitation | Impact |
|---|---|
| Name-based matching is evadable | A source with an unusual name not in the substring list will bypass the filter |
| Package name check uses only 2 substrings vs 14 name substrings | `isExplicitPackageName` is narrower than `isExplicitName` |
| Hardcoded ID set does not auto-update | New explicit sources not matching any keyword and not in the ID set will not be classified |
| Compile-time only | Cannot respond to new explicit sources without a code update and release |

The classifier is documented as imperfect and should not be presented as a reliable content classification system. It is a best-effort filter.

---

## Logs And Diagnostics

### What Is Logged

| Location | Level | Content | Sensitive? |
|---|---|---|---|
| `SourceEvaluationRunner` install step | DEBUG | Extension name, pkg name, signature hash, mode | Extension pkg/name: acceptable |
| `SourceEvaluationRunner` probe results | DEBUG/INFO | Source name, verdict, score | Low sensitivity |
| `SourceEvaluationRunner` timeout/error | WARN/ERROR | Extension name, source name, exception message | Low-medium: exception messages may include URLs |
| `OcrIndexService` page error | WARN | `manga.title`, `chapter.name`, `pageIndex` | Medium: personal library content in logcat |
| `SourceEvaluationStartupRecovery` | WARN | Extension name, phase of quarantined extension | Low |

### Copy Diagnostics Clipboard Export

The "Copy Diagnostics" button builds a text blob including: KMK version, timestamp, installer mode, batch size, candidate count, evaluation result count, unsafe count, blocked package count, current extension name, current source name, Shizuku state, last error message, last probe marker (extension name, pkg name, source name, source ID, phase, timestamp).

This does **not** include: manga titles, OCR text, user ratings, taste profile weights, imported bundle contents, auth tokens, or credentials.

Risk level: **low**. The export is safe for user-shared issue reports. The public issue template in `KMK_PUBLIC_README_DRAFT.md` already warns users not to paste private OCR text, personal backups, tokens, or sensitive manga URLs.

### No Auth/Token Logging Observed

No password, token, or credential logging was observed in any KMK-added code path audited.

---

## User-Facing Warning Requirements

For each risky workflow, confirmed requirements:

| Feature | Pre-run Warning | Per-run Consent | Privacy Note | Cleanup Warning | Experimental Label | Status |
|---|---|---|---|---|---|---|
| Source Evaluation (all modes) | Yes | One-time (persists) | Partial â€” consent text mentions "installs extension packages" | Yes â€” post-batch cleanup count | Yes â€” "Experimental â€” Source Evaluation" label in settings | Mostly covered; consent not re-triggered on mode change |
| Source Evaluation (Shizuku/Current mode) | Yes â€” prompt-heavy dialog | n/a | No | Yes | Yes | Prompt-heavy dialog exists for batch > 1 |
| OCR indexing | No pre-index privacy notice | Confirm dialog (describes time/battery, not text storage) | **Missing** | n/a | Yes â€” OCR is separate build line | **Gap: no privacy notice before text is stored** |
| Bundle import | Preview before add | n/a | Partial â€” public README warns not to import from untrusted sources | n/a | Not explicitly labeled in UI | Generally adequate; no explicit "experimental" label in import screen |
| Best Version | User confirms candidates and migration/copy | Per-action confirmation | n/a | n/a | Not labeled | Correct â€” confirmation exists |
| Cross-extension matching | User confirms each match | Per-action | n/a | n/a | Not labeled | Acceptable; false positive risk documented |

---

## Security Posture Assessment

| Feature | Posture | Community-Ready? |
|---|---|---|
| For You recommendations | Low risk â€” local computation, installed sources only | After style/UX cleanup |
| Source Evaluation | High risk â€” extension install/execute | Experimental only; needs cleanup gap fix before community |
| OCR | High privacy risk â€” stores private text | Experimental only; needs privacy notice before community |
| Bundle import | Medium risk â€” untrusted file boundary | After schema documentation; current limits are reasonable |
| Best Version | Medium risk â€” migration/copy | After QA verification |
| Cross-extension matching | Low-medium risk | After link management UI |
| Explicit source filter | Low risk â€” imperfect but documented | With documented limitations |

---

## All Security And Privacy Gaps

| ID | Area | Severity | Finding | Recommended Action | Phase |
|---|---|---|---|---|---|
| SEC-01 | Shizuku/current extension left behind on process death | Critical | **Mitigated v0.7.16.** Still-installed leftover package is recorded and surfaced with an uninstall action; quarantine/load-block prevents re-loading. | Keep device QA around process-death edge cases | Complete |
| SEC-02 | `promptHeavyCleanupAllowed = false` leaves Shizuku extensions installed after normal completion | High | **Mitigated v0.7.16.** Post-batch summary includes in-screen cleanup action for prompt-required extensions. | Keep prompt-flow QA for Shizuku/Current modes | Complete |
| SEC-03 | Consent does not re-trigger on installer mode change | Medium | **Mitigated v0.7.16.** Switching to SHIZUKU or CURRENT resets consent. | None beyond QA | Complete |
| SEC-04 | `SourceEvaluationJobState` in-memory only | High | Process death during evaluation: candidates lost, job fails gracefully with visible error. Not silent. Extension may be left installed (see SEC-01). | Document honest failure; durable resume not required but extension cleanup on restart should be investigated | Phase 5 |
| SEC-05 | 24-hour stale marker threshold | Low | Device suspended > 24h mid-evaluation: crash marker cleared without quarantine. | Consider reducing threshold or surfacing "evaluation interrupted" warning | Phase 5 |
| SEC-06 | No pre-indexing OCR privacy warning | Medium | **Mitigated v0.7.16.** Confirmation text discloses local searchable text storage and no server upload. | Keep OCR docs aligned | Complete |
| SEC-07 | OCR text stored in plaintext | Low | Acceptable on unrooted devices (app sandbox). Plaintext on rooted devices. | Document in OCR public docs; encryption is out of scope for this fork | Phase 9 |
| SEC-08 | Per-manga OCR deletion UI not confirmed | Low | Deletion API exists; surface accessibility from library/manga detail not confirmed in audit. | Verify and add if missing | Phase 9 |
| SEC-09 | Bundle import network fetch: up to 500 items without rate limiting | Medium | **Partially mitigated v0.7.16.** 200 ms delay added between item adds. No user-facing warning yet. | Consider import-size/network warning | Phase 8 follow-up |
| SEC-10 | Missing-source bundle install uses global installer preference | Low | Consistent with normal extension install UX; no installer override. Acceptable but note for Shizuku users who may see system prompts. | Document in bundle import UI | Phase 8 |
