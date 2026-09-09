# KMK-Recs v0.8.3 - Recommendation UI Refinement Plan

Status: **implemented and shipped in KMK-Recs v0.8.5**. See `KMK_RECS_V0_8_2_TO_V0_8_5_FOR_YOU_UI_AND_READING_TIMER_IMPLEMENTATION.md` for the implementation report.

## Scope

Refine the existing For You, Source Evaluation, Recommendation Settings, and rated-manga collection UI so it follows established Komikku Library patterns. Do not change scoring, query strategy, source selection, candidate visibility, evaluation math, or database semantics.

## Required source inspection

Read and compare:

- app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt
- app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt
- app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
- app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
- app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt
- app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
- app/src/main/java/exh/recs/loved/LovedMangaScreen.kt
- app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt
- the Library screen, Library toolbar, Library settings, overflow menus, and existing reusable grid/action components.
- KMR strings and existing theme/color/spacing tokens.

Record which existing composables are reused. If a current component is not reusable, explain why before creating a new one.

## For You screen changes

Keep the current top-level navigation and data flow. Refine only presentation:

1. Keep Loved, Liked, and Disliked shortcuts visible near the existing For You toolbar controls.
2. Give each shortcut a familiar icon, KMR label/content description, and stable touch target.
3. Keep secondary actions in overflow menus or row-level menus. Do not display every action permanently.
4. Preserve Top Picks, source rows, arrow navigation, refresh, retry, no-match, error, outdated, and disabled-source states.
5. Make status labels compact and visually distinct using theme colors, not literal colors.
6. Keep row height stable while loading and avoid overlapping the system navigation area.
7. Ensure text wraps or truncates according to existing Komikku patterns; no clipped source names or action labels.

## Rated collections

Loved, Liked, and Disliked must use one parameterized collection screen/model. The rating is an input that controls title, icon, filter, and empty state. Shared behavior must remain identical:

- sorting;
- source filtering;
- grouping and linked versions;
- duplicate handling;
- sharing/export;
- selection mode;
- action menu;
- group recommendations;
- clear/change rating;
- version list.

Long-press remains selection mode. Recommendations remain explicit actions in the item menu. Do not restore hidden long-press recommendation behavior. Destructive actions retain confirmation and existing undo behavior.

## Recommendation Settings structure

Keep the current screen model/state. Reorganize composable sections in this order:

1. For You behavior: language, display count, known/rated/Not Interested visibility, chapter threshold, refresh.
2. Source priority: reorder, top-three behavior, recommendation preference.
3. Source evaluation: batch size, installer mode, continuation, stale reassessment, compatibility, diagnostics.
4. Source management: installed/hidden/disliked/quality-marked rows and recovery.
5. Discovery/cache management.

Use existing preference-section headers, dividers, collapsible section state, and menu components. Do not move destructive actions into the primary tap path. Preserve scroll position and state across navigation/recomposition.

## Source Evaluation layout

Keep the evidence and action data. Present:

- summary counts and current run state at the top;
- one primary action row;
- secondary actions in overflow or an expandable management area;
- grouped sorting/filter controls;
- compact source rows with expandable Details;
- diagnostics hidden until requested;
- quarantine/blocked sources behind explicit show controls;
- clear empty/offline/running/canceled/completed/outdated/error states.

Use FlowRow or the established phone-safe wrapping pattern for installer controls and action rows. Do not hardcode widths.

## Theme/accessibility contract

All colors use MaterialTheme.colorScheme or existing Komikku theme tokens. Verify light, dark, red/blue accent variants, and dynamic recomposition. All icon buttons have content descriptions/tooltips where needed. Touch targets follow existing minimums. Menus/dialogs dismiss via back and outside tap where appropriate. No action may trigger from recomposition.

## Tests and manual QA

Add Compose/unit coverage for rating collection parameterization, settings section state, status rendering, and action visibility. Manually inspect phone and tablet layouts in light/dark themes, rotation, scrolling, menus, selection, dialogs, loading/error/empty states, and accessibility labels. Record unavailable device testing.

## Acceptance criteria

No recommendation result, score, source, cache, preference meaning, or database row changes because of this phase. The four screens look and behave like existing Komikku screens, all actions remain discoverable, phone layouts do not clip/overlap, and all new strings are KMR-localized.



## Code-level implementation addendum from source review

The For You screen model already owns source-order state, row results, statuses, Top Picks results, loading state, and refresh behavior. Keep that state model unchanged. The UI pass must not move filtering or sorting into composables. Composables should render state and dispatch existing model actions.

The rated collection is implemented by LovedMangaScreen/LovedMangaScreenModel and already has source filtering, sorting, grouping, selection, and action paths. Refactor only if the current screen is not already parameterized; if it is still Loved-named internally, introduce a narrow RatedMangaCollectionArgs/Rating filter rather than copying the screen three times. Preserve the existing screen-model queries and use the rating filter at the query boundary.

In SourceEvaluationScreen, preserve the current expandable details, continuation/reassessment actions, diagnostics, hidden-source controls, and error-key mapping. The UI cleanup must not remove the controls that distinguish unassessed, outdated, no-match, error, blocked, quarantined, installed-hidden, and source-quality-disliked states. If a section is collapsed by default, its state must be local UI state and must not alter evaluation data.

When moving actions into menus, verify every current callback has exactly one visible path. Specifically test source reorder/save, start/continue/reassess, cancel, retry, copy diagnostics, clear marks, show installed, show disliked, and reset discovery. No action may be triggered from a composable body or duplicated by recomposition.


