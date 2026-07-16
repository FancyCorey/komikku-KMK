# KMK-Recs v0.8.9 What's New and Recommendation Settings Search Plan

**Status:** Planning only; no code changes approved or performed  
**Build channel:** Internal development handoff; this wording is not app-facing text  
**Baseline:** KMK-Recs v0.8.8 plus the current official-Komikku reconciliation work  
**Related reconciliation:** `docs/community/KMK_UPSTREAM_1_14_DETAILED_RECONCILIATION_PLAN.md`

## Objective

Improve two navigation/documentation surfaces without creating a second design system:

1. Reformat the KMK What's New experience so it follows the presentation pattern used by official Komikku.
2. Add searchable navigation to Recommendation/For You settings by reusing the existing main Settings search architecture.

The implementation must preserve the full historical KMK release information and all current settings behavior. This is a presentation and discoverability change, not a scoring, database, or recommendation-algorithm change.

## Evidence and Official Formatting Target

The official Komikku changelog at [komikku-app.github.io/changelogs](https://komikku-app.github.io/changelogs/) presents releases using:

- a version heading;
- a release date/version context line;
- a `What's Changed` heading;
- compact category headings such as `New`, `Improve`, and `Fix`;
- concise bullets with a feature-area prefix such as `Extension:`, `Library:`, or `Reader:`;
- inline code formatting for technical names and settings;
- links/contributor information kept separate from the main change list.

The official 1.14.0 page is the reference for hierarchy and density, not a request to copy its web page wholesale into the app. The Android UI must use the existing Komikku/Mihon typography, spacing, theme, accessibility, and Markdown rendering components.

## Preflight Requirements

Before coding, Claude must read:

- `AGENTS.md`;
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`;
- `docs/recommendations/DOCUMENTATION_RULES.md`;
- `docs/recommendations/CURRENT_STATE.md`;
- `docs/recommendations/NEXT_WORK.md`;
- `RECOMMENDATION_VERSIONING.md`;
- the current v0.8.8 implementation report;
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`;
- `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/KmkRecsWhatsNewDialog.kt`;
- `app/src/main/java/eu/kanade/tachiyomi/ui/more/KmkRecsWhatsNewScreen.kt`;
- the official main Settings search screen/model and its row metadata implementation;
- `app/src/main/java/exh/recs/settings/RecommendationSettingsIndexScreen.kt`;
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt`;
- all extracted v0.8.8 Recommendation Settings screens.

Claude must verify the actual current paths and names before editing because the Recommendation Settings screen was split during v0.8.8.

## Part A: What's New Data Model and Content

### Existing behavior to preserve

The current release notes contain useful historical details, including separate v0.8.2, v0.8.3, v0.8.4, v0.8.5, v0.8.6, v0.8.7, and v0.8.8 entries. Those entries must remain individually accessible. The implementation must not collapse them into a single v0.8.8 paragraph.

The feature must continue to support:

- opening What's New from About/More;
- showing the current KMK feature version in the About entry;
- newest-first ordering;
- dismissal/back behavior;
- any existing “show once” or last-seen release behavior;
- both dialog and full-screen paths if both are still present.

### Required release-note structure

Refactor `KmkRecsReleaseNotes.kt` from an undifferentiated Markdown string into a structured release-note representation if the current renderer can support it safely. Prefer a small immutable model such as:

- release version;
- release date or date label;
- short summary;
- ordered sections;
- ordered bullets per section;
- optional inline emphasis/code spans.

If a structured model would require unnecessary renderer risk, retain Markdown as the source format but enforce a documented content schema and add a parser/rendering test. Claude must decide from the existing code and document the decision.

Each release entry must follow this shape:

```text
## KMK-Recs v0.8.9
Short one-sentence summary.

#### What's Changed

##### New
- **Recommendations:** concise change description.
- **Settings:** concise change description.

##### Improve
- **UI:** concise change description.

##### Fix
- **Source Evaluation:** concise change description.
```

Rules:

- Use `New`, `Improve`, and `Fix` only when a section has content.
- Do not create empty headings.
- Keep each bullet to one primary behavior and one short explanation.
- Put implementation details such as class names, database tables, and test counts in the linked implementation report, not in every user-facing bullet.
- Use bold for the area label only when the existing renderer supports it reliably.
- Use inline code only for actual technical names/settings, not for ordinary prose.
- Avoid internal terms such as “Claude”, “Codex”, “private build”, “public build”, “implementation plan”, “AI”, or “development artifact”.
- Preserve meaningful user-facing behavior from historical notes, but rewrite repetitive or overly technical wording into concise user language.
- Do not claim device QA or upstream compatibility unless the corresponding evidence exists.

### Required history audit

Before changing release-note content, build a table from the current `KmkRecsReleaseNotes.kt` and all v0.8 implementation reports:

| Version | Existing entry | User-visible changes | Implementation report | Keep/rewrite/split |
|---|---|---|---|---|

The final history must include every created v0.8 version, including fix builds, in chronological order. If a version was built but never user-facing, Claude must retain its historical entry but label the content factually without exposing internal workflow language.

### Renderer work

Inspect `KmkRecsWhatsNewDialog.kt` and `KmkRecsWhatsNewScreen.kt` and then:

1. Identify whether the current Markdown renderer already supports headings, bold, bullets, inline code, links, and paragraph spacing.
2. Reuse the official Komikku/Mihon text renderer and typography where possible.
3. If the current renderer cannot produce the required hierarchy, add the smallest local adapter needed. Do not introduce a web renderer, HTML dependency, or a second Markdown library.
4. Ensure headings have stable typography and vertical spacing on both phone and tablet.
5. Ensure bullet indentation, text wrapping, and long translated strings do not clip or overlap.
6. Preserve dark/light theme colors and sufficient contrast.
7. Ensure links are keyboard/TalkBack accessible and do not become accidentally selectable as one giant text block.
8. Ensure the scroll position starts at the newest entry and that long history remains performant.
9. Ensure the dialog/full-screen surface can be dismissed through back, close, and accessibility actions.

### What's New tests

Add focused tests for:

- historical version ordering;
- no missing v0.8 entries;
- no duplicate version entries;
- section omission when a category is empty;
- Markdown/structured-note rendering of heading, bold, bullet, code, and link tokens;
- malformed or missing note content failing safely;
- long text wrapping and translated-resource fallback where the existing test framework allows it.

Add a manual QA checklist for phone and tablet because Compose text layout cannot be fully proven by unit tests alone.

## Part B: Recommendation Settings Search

### Goal

Allow users to search within Recommendation/For You settings for settings and actions covering:

- For You behavior;
- source priority and ordering;
- taste and tag preferences;
- source evaluation;
- sources to try;
- background execution, network, and installer behavior;
- management, diagnostics, reset, and cache actions;
- same-manga matching, group recommendations, and Best Version controls;
- any additional Recommendation Settings sections currently present.

The search must navigate to a relevant setting or section. It must not change preference values merely because the user searched.

### Reuse strategy

The existing main Settings search is the behavioral reference. Claude must inspect its:

- searchable item model;
- indexing/flattening process;
- query normalization;
- token matching/ranking;
- navigation callback;
- state restoration;
- empty-result behavior;
- accessibility labels;
- theme and row components.

The Recommendation Settings search should reuse those primitives or extract a shared search helper if the current implementation has duplicated logic. Do not build an independent search algorithm with different matching semantics unless the existing search cannot represent section-local destinations.

### Search index design

Create a Recommendation Settings search entry for every actual destination/control, not just each top-level category. Each entry must contain:

- stable internal key;
- display title;
- concise searchable summary;
- category/section label;
- synonyms and user vocabulary;
- destination identifier;
- optional anchor/scroll target for controls within a screen;
- accessibility label;
- visibility predicate when the setting is conditional.

Examples of search terms that should resolve naturally:

- `source priority`, `reorder sources`, `top three`, `extension order`;
- `preferred tags`, `blocked tags`, `aliases`, `taste`;
- `evaluate sources`, `reassess`, `outdated evaluations`, `recommendation quality`;
- `install extensions`, `sources to try`, `private installer`, `Shizuku`;
- `group recommendations`, `same manga`, `match other versions`, `results per extension`;
- `cache`, `reset discovery`, `diagnostics`, `backup`, `sync`;
- `For You`, `known manga`, `hide disliked`, `minimum chapters`, `languages`.

The index must use KMR for KMK-only visible strings and must not add translated locale entries outside the base resource directory.

### Navigation behavior

1. Add a search affordance in the Recommendation Settings index/top app bar using the same icon, semantics, and transition pattern as main Settings search.
2. Opening search must preserve the current Recommendation Settings navigation stack.
3. Selecting a top-level result navigates to its dedicated screen.
4. Selecting a setting inside a dedicated screen navigates to that screen and scrolls/highlights the target control if the screen supports a stable anchor.
5. If a setting is currently inside a shared long screen, extract a stable section destination or implement an explicit anchor contract; do not depend on fragile list indices.
6. If a result is unavailable because the relevant source/installer feature is disabled, show the destination with a truthful disabled/unavailable state rather than silently doing nothing.
7. Back returns from search to the Recommendation Settings screen, not directly to the root app screen.
8. Search cancellation and empty queries restore the ordinary Recommendation Settings index.
9. Query updates must be debounced or otherwise bounded so typing does not trigger repeated expensive recomposition or database work.

### UI requirements

- Match the main Settings search layout and toolbar behavior.
- Keep the current clear section organization visible when search is not active.
- Show result category/context so similarly named settings are distinguishable.
- Use compact rows that fit narrow phones.
- Ensure result titles and summaries wrap without clipping.
- Provide clear empty-state text and a clear-query action.
- Preserve touch, keyboard, TalkBack, and large-font behavior.
- Avoid showing implementation class names or internal database terminology to users.

### Search tests

Add unit tests for:

- case-insensitive matching;
- whitespace and punctuation normalization;
- title, summary, category, and synonym matching;
- ranking exact title above partial summary matches;
- stable ordering for equal scores;
- hidden/conditional settings;
- duplicate labels in different sections;
- empty/no-result queries;
- navigation keys and anchor identifiers.

Add UI/instrumentation or Compose tests if the project test setup supports them. Otherwise add a deterministic index snapshot test and a manual matrix for:

- phone portrait;
- tablet portrait/landscape;
- dark/light theme;
- TalkBack;
- large font/display size;
- search, result selection, back, and rotation.

## Part C: Documentation and What's New Entry

After implementation:

1. Add the v0.8.9 release entry to `KmkRecsReleaseNotes.kt` using the cleaned official-style structure.
2. Update `docs/recommendations/CURRENT_STATE.md` with the actual behavior and known limitations.
3. Update `docs/recommendations/NEXT_WORK.md` to remove completed items and retain real QA gaps only.
4. Update `docs/recommendations/README.md` with this plan and its implementation report.
5. Update `RECOMMENDATION_VERSIONING.md` and the encyclopedia with the final version and APK naming.
6. Create a focused implementation report containing files changed, tests, build outputs, manual QA, and deviations.
7. Do not call the feature public/private in app-visible copy.

## Completion Gate

Claude must not build the final handoff APK until:

- the historical What's New entries are complete and individually visible;
- the official-style hierarchy renders correctly on phone and tablet;
- Recommendation Settings search uses the established settings-search behavior;
- every search result navigates to a real, stable destination;
- all visible strings follow KMR/MR/SYMR ownership rules;
- unit tests and available UI tests pass;
- `spotlessApply`, `spotlessCheck`, and the intended build pass in JDK 17+;
- manual QA confirms search/navigation and What's New layout;
- the APK filename and embedded version metadata match;
- the implementation report and encyclopedia are updated.
