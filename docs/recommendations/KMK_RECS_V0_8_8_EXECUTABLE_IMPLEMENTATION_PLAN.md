# KMK-Recs v0.8.8 Executable Implementation Plan

**Scope:** Latest-chapter rating prompt, recommendation-settings navigation, and outdated-evaluation continuation.
**Dependency:** v0.8.7-fix1 must remain correct.
**Build rule:** one final 0.8.x APK only after all phases and gates pass.

**Status:** Implemented and verified in code (2026-07-16, gap-closing pass), VERSION_CODE 758,
VERSION_NAME "KMK-Recs v0.8.8" (unchanged — this is a navigation-only follow-up within the same
release, not a new one). Phase A (rating prompt) and Phase C (outdated-evaluation fix) implemented as
specified, with the Phase C fix taking the form of a reconciliation policy rather than one merged
candidate-selection policy (a live-code-verified architectural decision — see the implementation
report). Phase B (settings navigation) now fully splits all seven categories into distinct screens:
Evaluation and Background/Installer both route to `SourceEvaluationScreen` (the latter has no
distinct content elsewhere — a confirmed structural fact); the other five each got their own new
screen, extracted verbatim (zero preference/behavior change) from the former single
`RecommendationsSettingsScreen`, which has been deleted. 36 total new tests across the three plans'
phases, including a 250-synthetic-source suite for Phase C; no new tests for the Phase B split itself
(no new pure logic — see the report). Device/phone+tablet QA not performed — no physical device
available. See `docs/recommendations/KMK_RECS_V0_8_7_FIX1_AND_V0_8_8_IMPLEMENTATION.md` for the
complete, itemized breakdown.

## Preflight

Read the implementation standard, encyclopedia, current state, next work, v0.8.7 plans, rated-manga/group-recommendation reports, and source-evaluation reports. Inspect the live symbols in `ReaderActivity.kt`, `ReaderViewModel.kt`, reader chapter progress/loading code, rating repository/use case, rated-manga screen/model, cross-extension matching screen/model, `RecommendationsSettingsScreen.kt`, `RecommendationsSettingsScreenModel.kt`, source-evaluation screen/model/repository/use case/worker, evaluation SQLDelight schema/queries, More/settings components, and localization resources. Record actual symbols, dispatchers, lifecycle owners, persistence, and tests before editing.

## Phase A: chapter-completion rating

Trace page progress -> genuine completion -> authoritative chapter ordering -> latest-chapter decision -> reader navigation. Implement a pure decision helper using existing chapter ordering; never compare chapter names as strings.

Show a compact dismissible prompt only after genuine completion of the latest available chapter. Do not show it for older chapters, opening/exiting, page restore, rotation, or duplicate callbacks. Actions are Love, Like, Dislike, existing Seen/Not Interested behavior, and close. Reuse the existing exclusive rating mutation.

After a rating, show a second step only if confirmed alternate versions exist. Yes opens the existing cross-extension selector; No returns to manga detail; cancel/close returns safely while retaining the first rating; no group skips an empty selector. Do not rate unconfirmed search candidates silently. Store only stable identifiers/state; never serialize screen or match-mode objects.

Test latest/non-latest, duplicate completion, dismissal, every rating, exclusive overwrite, no alternate versions, selector confirmation/cancel, recreation, backgrounding, and schedule restriction.

## Phase B: settings UX

Reuse the existing More/settings section-row and navigation components. Convert recommendation settings to a concise index with sections for For You display, source priority, taste/tags, evaluation, non-installed discovery, background/network/installer behavior, and diagnostics/reset. Use localized strings, existing theme tokens, accessible icons, phone-safe wrapping, stable touch targets, and preserved back-stack state.

Source Evaluation must keep remaining counts, active progress, cancellation, primary action, and blocking errors visible. Place secondary controls in detail sections for batch/mode, filters, past evaluations, compatibility, quarantine/blocked sources, and diagnostics. Do not hide active jobs or errors behind navigation.

Test navigation/back/state restoration, loading/empty/error states, narrow phone layout, accessibility, source reorder persistence, and existing setting behavior.

## Phase C: outdated-evaluation continuation

Reproduce the reported state where visible `Outdated — reassess needed` rows lead to immediate `evaluation failed` or zero candidates. Trace displayed count -> UI event -> screen model -> candidate query -> filters -> stable order/cursor -> worker -> installer/load -> evaluation -> database write -> aggregation -> refresh. Record the real exception and filter values.

Create or reuse one pure candidate policy shared by count and worker. It must distinguish unassessed, outdated, explicit reassessment, current, installed, blocked, quarantined, language/category-filtered, missing metadata, and retryable failure. Explicit reassessment must include outdated rows even when normal skip-evaluated is enabled.

Continuation must use stable source identity and deterministic ordering, select the next batch after completed work, continue beyond 10/25/50/100, advance after isolated success/failure/skip, support cancellation/resume, and avoid duplicate processing. It must not clear all evaluations or restart at offset zero.

Separate no-candidates, filtered candidates, offline/timeout, installation/load, probe, persistence, cancellation, and unexpected errors. Isolate one source failure, persist safe diagnostics, and provide retry. Never turn an empty result into generic evaluation failure.

Test at least 250 synthetic sources over multiple batches; success, mixed failure, cancellation/resume, outdated inclusion, filters/count agreement, stable ordering, no duplicates, empty explanations, persistence failure, retry, and UI refresh.

## Phase D: integration gate

Verify v0.8.7-fix1 schedule enforcement, reader exit, rating exclusivity, group recommendations, settings navigation, source-evaluation cancellation/notifications/background behavior, and network recovery. Run formatting, focused tests, full unit tests, build, and phone/tablet device QA for prompts, rotation, lock screen, backgrounding, narrow layout, network loss, and multi-batch continuation.

Only after all gates pass update implementation report, current state, next work, encyclopedia/index, version history, and What's New. Keep 0.8.x versioning, align APK naming with actual metadata, place the APK in the established private handoff directory, and keep internal release-channel terminology out of the app UI.
