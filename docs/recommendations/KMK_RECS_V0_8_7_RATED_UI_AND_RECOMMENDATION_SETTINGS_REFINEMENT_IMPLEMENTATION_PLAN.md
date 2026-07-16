# KMK-Recs v0.8.7 Rated Collections And Recommendation Settings UI Refinement Plan

**Status:** Partially implemented across two passes (2026-07-15). The sibling Reading Schedule plan
shipped in full. This plan shipped a real but partial slice: "Select all in group" added to the Rated
Manga bulk-selection bottom bar (tested pure conflict resolver); state-derived summaries added to 6
of 9 Recommendation Settings section headers (tested pure counting helpers); Undo (Snackbar, reusing
the existing `LibraryTab.kt` pattern) added for 2 of 5 listed bulk actions (Clear Rating, Mark Not
Interested); Source Priority's action layout, Source Evaluation, Sources To Try, and group management
audited and found already compliant. Expand/collapse for Recommendation Settings sections was
explicitly attempted in the second pass and declined — the Source Priority section's drag-and-drop
reorderable list made it unsafe to verify without a physical device, and forcing it was judged a
disproportionate regression risk. Still NOT implemented: summaries for the remaining 3 sections, Undo
for Merge/Remove From Group/Ungroup, Source Priority per-row quality-dislike/explicit-block/reset/
details (new functionality, not yet wired per-row at all), and a line-by-line audit of cross-extension
matching / Best Version workflow. Do not treat this as "implemented and shipped" in full — see
`docs/recommendations/KMK_RECS_V0_8_7_RATED_UI_AND_RECOMMENDATION_SETTINGS_REFINEMENT_IMPLEMENTATION.md`
for the complete, itemized breakdown of both passes.

**Target:** KMK-Recs v0.8.7, remaining within the 0.8.x version line.

**Scope:** Reduce visible action clutter, make rated-manga actions discoverable through Komikku-style menus, improve Recommendation Settings visual hierarchy, and apply the same interaction principles to related recommendation workflows.

**Non-scope:** No recommendation scoring changes, no search-limit changes, no database redesign, no changes to normal global search, no automatic grouping by title, and no removal of existing rating/group behavior.

## 1. Design direction

The current code already has important reusable pieces:

- RatedMangaScreen already has selection mode, a selected-count top bar, a bottom bulk-action bar, sort controls, and per-item menu actions.
- Recommendation Settings already has logical sections, dropdown menus, chips, source suggestion selection, and source-evaluation controls.
- Source Evaluation already has filters, sort menus, installer controls, continuation controls, and diagnostics.
- Global Search already has its own exploration workflow and must remain unchanged.

This work is therefore a UI consolidation/refinement pass, not a rewrite. The implementation must reduce the number of always-visible text actions while preserving discoverability through:

1. top-app-bar actions for screen-level operations;
2. overflow menus for less frequent item-level operations;
3. Library-style selection mode for bulk operations;
4. collapsible settings sections with visual summaries;
5. explicit primary actions that remain visible when hiding them would make the workflow unclear.

Do not hide essential actions merely to make the screen look cleaner.

## 2. Mandatory preflight

Claude must read and follow:

- AGENTS.md;
- docs/recommendations/DOCUMENTATION_RULES.md;
- the recommendation encyclopedia/index;
- CURRENT_STATE.md;
- NEXT_WORK.md;
- the latest v0.8.6 implementation report;
- the official Komikku/Mihon UI patterns used by the current branch.

Before editing, inspect these live files and confirm their current symbols:

- app/src/main/java/exh/recs/loved/RatedMangaScreen.kt
- app/src/main/java/exh/recs/loved/RatedMangaScreenModel.kt
- app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
- app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
- app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt
- app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
- app/src/main/java/exh/recs/RecommendsScreen.kt
- app/src/main/java/exh/recs/links/LinkGroupManagementScreen.kt
- the current Library screen and its selection/overflow action implementation;
- the current extension-management screen;
- the current best-version preview/migration screens;
- app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/SearchScreenModel.kt.

Record discrepancies in the implementation report instead of assuming the old plan matches the current tree.

## 3. Rated Manga UI

### 3.1 Shared rated collection behavior

Love, Like, and Dislike must continue using the same reusable rated collection screen and the same state model. Do not create three separate UI implementations.

The screen must retain:

- Love, Like, and Dislike filtering;
- installed-source-only behavior where already required;
- grouping and duplicate handling;
- sorting by recent, oldest, title, and source;
- linked-version visibility;
- cross-source group management;
- rating exclusivity;
- recommendations and group recommendations;
- mark-not-interested/seen behavior as currently implemented;
- empty, loading, and error states.

### 3.2 Screen-level quick access

Use the existing top app bar rather than a permanent side rail on phones.

The normal rated screen top bar should expose:

- a visible selection icon/action;
- a compact rating filter or tab control showing Love, Like, Dislike, or All Rated;
- an overflow menu for infrequent screen-level actions.

The overflow menu may contain:

- Sort;
- Filter;
- Group/duplicate options;
- Refresh;
- Open settings;
- Clear screen-level filter state.

Do not put destructive bulk actions in the normal overflow menu unless they require an explicit selection and confirmation.

The selected-count top bar must remain the source of truth while selection mode is active. Its navigation/back action must exit selection mode, not unexpectedly leave the screen.

### 3.3 Item action menu

Each rated manga item/group should use one compact overflow action entry point. Existing per-item actions must be retained, reorganized, and conditionally shown:

Recommendation Actions:

- See Recommendations;
- See Group Recommendations only when the confirmed group has at least two linked versions;
- Find Other Versions.

Rating Actions:

- Change Rating;
- Clear Rating;
- Mark Not Interested;
- Favorite Other Versions when applicable.

Group Actions:

- Manage Group;
- View Linked Versions;
- Select All In Group;
- Merge Selected Into Group;
- Remove From Group;
- Ungroup;
- Set Primary Version if the current data model supports it.

Conditional rules:

- Do not show group actions when no confirmed group exists.
- Do not show See Group Recommendations for a single ungrouped manga.
- Do not show Remove From Group or Ungroup when the item is not linked.
- Do not show Favorite Other Versions when there are no eligible linked versions.
- Preserve the current explicit user-confirmed grouping requirement.
- Never auto-merge by title.

### 3.4 Selection mode and bulk actions

Long-press must remain selection, not recommendation navigation.

Selection mode must support:

- selecting multiple entries;
- Select All;
- Select All In Group when a selected group exists;
- Change Rating;
- Clear Rating;
- Merge Selected Into Group;
- Remove From Group;
- Mark Not Interested where applicable;
- undo for reversible actions;
- conflict handling when selected entries belong to different groups or have different ratings.

Keep the bottom action bar compact. Show only the most frequent actions directly:

- Change;
- Clear;
- Group.

Place less frequent actions under More:

- Remove From Group;
- Mark Not Interested;
- Select All In Group;
- Undo.

All actions must be disabled or hidden when their preconditions are not met. Destructive actions require confirmation dialogs. Confirmation text must include the affected count and explain whether group links, ratings, or library entries will change.

### 3.5 Linked-version presentation

Open Version List/View Linked Versions must remain available for grouped entries. The list should clearly show:

- source name;
- title;
- installed/not installed status;
- rating;
- favorite status;
- primary status if supported;
- group membership.

Do not place all of these details permanently on the main rated card. Use the version list for transparency.

## 4. Library quick access

Review the current Library top-bar layout and add rated quick access only if it fits the existing Library navigation model.

Preferred behavior:

- compact Love, Like, Dislike, and optionally Not Interested icons near the existing Library filters;
- counts may be shown in the destination screen or as compact badges;
- tapping an icon opens the corresponding rated collection;
- icon content descriptions and tooltips are mandatory;
- the quick-access controls must not interfere with category tabs, search, update, or download actions;
- narrow phone layouts may move secondary rated filters into the Library overflow menu.

The Library batch filter system must not be replaced. These are navigation shortcuts to the rated collection screens, not a new batch-filter implementation.

## 5. Recommendation Settings refinement

### 5.1 Section structure

Keep the existing logical order unless live code review proves it is incorrect:

1. For You;
2. Ratings and Known Manga;
3. Tags;
4. Source Priority;
5. Same-Manga Matching;
6. Sources To Try;
7. Source Evaluation;
8. Management;
9. Experimental/advanced controls where currently present.

Each section should have:

- a leading icon following existing Komikku icon conventions;
- a concise section title;
- a one-line state summary;
- expand/collapse behavior;
- controls rendered only when expanded;
- consistent vertical spacing;
- a stable visual hierarchy on phone and tablet.

Do not place a card inside another card. Do not introduce decorative section containers that conflict with the surrounding Komikku settings style.

### 5.2 Visual summaries

Examples of acceptable summaries:

- For You: current language, result budget, and whether known manga are hidden;
- Ratings and Known Manga: current visibility mode and chapter threshold;
- Tags: preferred, neutral, and blocked counts;
- Source Priority: enabled source count and top source name;
- Same-Manga Matching: current results-per-source and default selection mode;
- Sources To Try: number of visible suggestions and selected installer mode;
- Source Evaluation: evaluated, outdated, remaining, quarantined, and blocked counts;
- Management: available cleanup/reset tools.

Summaries must be derived from state, not hardcoded. They must update after the related preference or process changes.

### 5.3 Wordiness reduction

Audit every visible string in Recommendation Settings:

- remove duplicate explanations;
- keep one concise supporting explanation beside the relevant control;
- move advanced rationale into an expandable information action where useful;
- replace repeated “this affects...” paragraphs with a short supporting line;
- preserve necessary warnings for destructive, slow, network-dependent, or installer-dependent actions;
- use existing KMR/i18n conventions for all new or changed strings.

Do not remove explanations that are needed to distinguish:

- normal global search from recommendation matching;
- For You evaluation from source evaluation;
- Private/current/Shizuku installer behavior;
- initial preview limits from full-source expansion;
- recommendation-quality dislikes from source-quality dislikes.

### 5.4 Control choices

Use the existing control type that matches each setting:

- Switch for binary behavior;
- segmented/filter chips for small mutually exclusive sets;
- dropdown for larger enumerations;
- slider/stepper only for numeric values where the current app already uses that pattern;
- overflow menu for reset, clear, export, diagnostics, and other infrequent actions;
- visible button for a primary operation such as Start Evaluation, Continue Evaluation, or Retry.

Never replace a necessary primary operation with an icon that lacks an accessible label.

### 5.5 Source Priority

Keep source priority as a dedicated visible section because ordering is a core workflow.

Improve it with:

- clear drag handles;
- stable saved ordering;
- a compact status summary;
- per-source overflow actions for Like, Dislike, source-quality dislike, explicit-source block, reset, and details;
- no permanently visible row of text buttons;
- no accidental reset caused by touch/reorder;
- no automatic reordering after manual reorder;
- clear confirmation for reset priority.

The top three boost and saved profile ordering must remain unchanged.

### 5.6 Sources To Try

Keep the current bulk-selection capability, but move secondary row actions into an overflow menu:

- Install;
- Like;
- Dislike;
- Dismiss;
- Source details.

Retain a visible primary Install action when not in selection mode. In selection mode, use the existing selection action bar for Install Selected, Dismiss Selected, and Clear Selection.

Do not apply any global-search result limit to this screen.

### 5.7 Source Evaluation

The Source Evaluation screen is already information-dense. Do not hide its primary workflow:

- batch size;
- installer mode;
- Start/Continue Evaluation;
- reassessment;
- current progress/remaining count.

Use collapsible or overflow organization for secondary tools:

- Show installed;
- quarantine/blocked visibility;
- sorting;
- diagnostics;
- copy diagnostics;
- warning details;
- cleanup/reset actions.

Keep the Shizuku setup card visible only when Shizuku is selected/relevant, as required by the current implementation. Private/current evaluation behavior must not show Shizuku setup prompts.

Source rows should use compact status chips and a details expansion area instead of repeating every diagnostic field in the collapsed row.

## 6. Related screens

### 6.1 Group management

Reuse the current LinkGroupManagementScreen and model. Improve only discoverability and action placement:

- visible View Linked Versions;
- overflow for merge, remove, ungroup, and destructive actions;
- clear group identity and member count;
- no duplicate grouping algorithm.

### 6.2 Recommendation results

Keep source-row expansion and retry behavior. Do not add new limits or alter the v0.8.6 performance implementation in this UI fix.

If a row has multiple actions, use the existing row header/overflow pattern. Do not make global search inherit recommendation-preview controls.

### 6.3 Cross-extension matching

Retain the existing default-selection preference. Use selection-mode action bars for Select All, Deselect All, Confirm, and Cancel. Keep the normal global-search workflow unchanged.

### 6.4 Best Version workflow

Do not redesign the chapter/image-quality algorithm in this fix. Only ensure that any action overflow or compact controls preserve:

- chapter selection;
- preview sample size;
- zoom/pan;
- Set as Best Version;
- migration confirmation;
- Cancel returning to the preview screen.

### 6.5 Extension management

Inspect the current extension screen before editing. If its selection/install/uninstall actions already follow Komikku conventions, reuse them rather than creating a second selection framework. Any bulk delete/uninstall action must require confirmation and report partial failures individually.

## 7. State and persistence requirements

The UI changes must not create duplicate state systems.

Persist only through existing mechanisms:

- selected rated filter/tab if current navigation already persists such state;
- sort mode if the current rated screen persists it;
- expanded settings sections only if the repository already persists section expansion;
- quick-action customization only if approved as an actual setting.

If customization is added, use a small ordered preference with:

- stable action IDs;
- validation against supported actions;
- fallback when an action is removed;
- no database migration unless existing settings architecture requires it.

## 8. Accessibility and phone layout

Every icon-only action must have:

- content description;
- tooltip where the project uses tooltips;
- accessible state description for selected/unselected filters;
- touch target compliant with existing Komikku dimensions.

Manual QA must cover:

- narrow phone portrait;
- tablet portrait;
- tablet landscape;
- long source names;
- translated strings with longer text;
- selection mode with one and many selected items;
- empty Love/Like/Dislike lists;
- grouped and ungrouped rated entries;
- expanded and collapsed settings sections;
- Source Evaluation with long diagnostics;
- TalkBack/basic accessibility traversal where available.

No text may overlap, clip, or force important actions off-screen.

## 9. Required tests

Add or update tests for:

- shared Love/Like/Dislike screen state;
- filter switching and empty states;
- item-menu visibility rules;
- group-action preconditions;
- selection-mode bulk-action enablement;
- conflicting group selection behavior;
- confirmation requirements;
- quick-access navigation;
- Recommendation Settings section summaries;
- summary updates after preference changes;
- default collapsed/expanded behavior;
- source-priority ordering persistence;
- no changes to global-search behavior;
- no changes to recommendation scoring or v0.8.6 loading policy;
- KMR string extraction and formatting;
- state restoration after configuration change/process recreation where current test infrastructure supports it.

Run the repository-approved checks, at minimum:

    ./gradlew spotlessCheck
    ./gradlew :app:testDebugUnitTest
    ./gradlew assembleDebug

Perform real-device UI QA on a phone and tablet. Do not claim the UI is complete based only on unit tests.

## 10. Documentation and versioning

Create:

- docs/recommendations/KMK_RECS_V0_8_7_RATED_UI_AND_RECOMMENDATION_SETTINGS_REFINEMENT_IMPLEMENTATION_PLAN.md

After implementation, create the corresponding implementation report and update:

- CURRENT_STATE.md;
- NEXT_WORK.md;
- README.md;
- the documentation encyclopedia/index;
- KmkRecsReleaseNotes.kt with a complete v0.8.7 What's New entry while preserving all prior v0.8.x entries.

The plan remains proposed until Claude completes implementation and verification. The final APK must use the established v0.8.7 naming convention and be copied to:

C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.8.7-debug.apk

Internal distribution terminology must not appear in user-facing app strings.

## 11. Acceptance criteria

The implementation is complete only when:

1. Rated screens no longer expose unnecessary rows of repetitive text buttons.
2. Item actions remain discoverable through a consistent overflow menu.
3. Long-press selection and bulk actions remain intact.
4. Group-specific actions appear only when valid.
5. Library rated shortcuts work without disrupting existing Library controls.
6. Recommendation Settings is scannable, visually grouped, and usable on a phone.
7. Important warnings and explanations remain present.
8. Source Evaluation keeps its primary actions visible and secondary diagnostics organized.
9. Global Search, recommendation scoring, grouping, and v0.8.6 loading behavior remain unchanged.
10. Existing destructive-action confirmations remain enforced.
11. Tests, formatting, builds, and device QA pass.
12. Documentation and What's New accurately describe v0.8.7.

## 12. Claude handoff contract

Treat this document as an implementation contract. Verify every referenced symbol against the live source tree, follow AGENTS.md and official Komikku/Mihon conventions, reuse existing components, and document deviations. Do not build the final APK after only a partial UI pass. Complete the code, tests, documentation, static checks, build checks, and phone/tablet QA first, then build once and copy the APK to the specified private handoff path.
