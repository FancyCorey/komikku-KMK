# KMK-Recs v0.8.10-fix1 - Final Release Safety Addendum

**Status:** Planning only. This document supplements the master v0.8.10-fix1 plan and release
assurance addendum. It adds final release safeguards without adding new user-facing features.

## 1. Dependency And License Review

Before handoff, inspect every dependency introduced or materially changed during the KMK and Komikku
1.14 work. Record:

- dependency name and version;
- Gradle module using it;
- reason it is required;
- license and notice obligations;
- whether it is upstream Komikku, Mihon/Tachiyomi-derived, KMK-specific, or third-party;
- whether it accesses network, storage, database, OCR content, installer APIs, or user credentials.

Do not add a dependency solely to solve a UI problem when an existing Komikku component already
provides the behavior. Ensure generated APK notices and repository documentation remain compliant.

## 2. Reproducible Build Verification

Document the exact build environment:

- repository commit;
- Android/Gradle plugin versions;
- Gradle wrapper version;
- JDK version and path;
- Kotlin/compiler versions;
- dependency lock/cache state where applicable;
- requested and actual Claude model/effort for the implementation.

From a clean checkout or clean worktree, run formatting, tests, and `assembleDebug`. Confirm the APK
can be rebuilt with the same version metadata and equivalent output identity. Record whether byte-for-
byte reproducibility is possible; if not, document expected nondeterministic fields such as signing,
timestamps, or build metadata.

## 3. Automated Completion Gate

Create or document one repeatable verification sequence that fails on any required step:

1. dependency/license validation;
2. `spotlessCheck`;
3. focused KMK unit tests;
4. migration, backup, sync, OCR, timer, schedule, and reader tests;
5. full `:app:testDebugUnitTest`;
6. static analysis and lint checks available in the repository;
7. `assembleDebug`;
8. APK filename/version/hash validation;
9. documentation link/version consistency checks.

The final implementation report must include the command, environment, result, and test count. A
failed or skipped gate must remain explicitly open; it must not be described as complete.

## 4. Privacy-Safe Crash Export

If the application-wide crash investigation adds or modifies diagnostics, provide a controlled export
or copy action using existing Komikku patterns. The exported diagnostic must:

- include app/KMK/upstream versions, device/API information, route, phase, exception type, and
  sanitized stack information;
- omit manga titles, descriptions, OCR text, authentication tokens, sync credentials, full private
  paths, and unnecessary query parameters;
- clearly tell the user what is being copied/shared;
- work when the failing screen cannot render its normal content;
- avoid silently uploading anything;
- have pure sanitization tests for representative stack traces and URLs.

## 5. Rollback And Recovery Checks

Verify recovery from:

- interrupted or failed database migration;
- malformed/truncated backup;
- invalid or stale cached recommendation data;
- missing or uninstalled extension;
- corrupted OCR index;
- interrupted source evaluation;
- process death during reader prompt, group recommendations, or Source Evaluation;
- failed installer/Shizuku operation.

The app must either recover automatically, preserve the previous valid state, or show a clear retry/
reset action. It must not silently delete ratings, groups, preferences, library entries, or sync
credentials. Any destructive reset must require explicit confirmation and describe its scope.

## 6. Edge-Case Test Fixtures

Add or document reusable fixtures for:

- empty/new database;
- database containing all KMK tables and migrations;
- hundreds of sources with mixed installed, missing, blocked, quarantined, stale, and error states;
- large rated collections with duplicate titles and confirmed groups;
- empty, partial, duplicate, and malformed recommendation rows;
- offline, timeout, cancellation, and partial-network responses;
- unsupported/missing metadata and alternate-language titles;
- small phone width, tablet width, landscape, dark mode, and large font scale;
- malformed Markdown and missing KMR resources;
- malformed backup and OCR index data.

Fixtures must be deterministic and must not contain real user data. Prefer pure policy tests where
possible, then use repository/integration/device tests for lifecycle and rendering behavior.

## 7. Final Safety Gate

The v0.8.10-fix1 handoff is allowed only after:

- dependency and license obligations are recorded;
- a clean/reproducible build procedure is documented;
- the automated completion gate passes;
- crash export is privacy-safe or explicitly documented as unchanged;
- rollback/recovery behavior is verified;
- edge-case fixtures cover the stated failure classes;
- all remaining limitations are visible in the implementation report.

This addendum does not authorize new feature work. It is the final quality and release-safety layer
for the already-approved v0.8.10-fix1 scope.
