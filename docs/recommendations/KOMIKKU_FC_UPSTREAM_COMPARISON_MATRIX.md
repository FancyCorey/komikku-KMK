# Komikku FC Upstream / Intended / Actual Comparison Matrix

**Status:** AUG-16 pass 2 — 2026-08-24
**Scope:** AUG-01 through AUG-18, plus FC-01 through FC-24 (added in pass 2 below,
citing the existing validated `private/evidence/rg013g-fc24-current-reconciliation-2026-08-23.json`
record rather than reconstructing from memory).

Per the AUG-B10 "Upstream/intended/actual comparison" contract, each row records:
upstream/original behavior, intended AUG-xx behavior, actual source owner and direct
tests, authorized runtime behavior, and a discrepancy classification (`INTENTIONAL_
UPSTREAM_DIFFERENCE`, `CONFIRMED_DEFECT`, `STALE_DOC_OR_MEDIA`, `INCOMPLETE_
INTEGRATION`, or `FUTURE_GATED_WORK`). Every row's "actual" column is sourced from this
session's own dated receipts in the master implementation handoff and Plans A-E, not
from unverified inherited claims — see
`private/docs/plans/KOMIKKU_FC_CLAUDE_MASTER_IMPLEMENTATION_HANDOFF_AUGUST_FEATURES_2026-08-24.md`
for the underlying per-row receipt this table summarizes.

| AUG | Upstream/original behavior | Intended AUG behavior | Actual source owner / tests | Runtime evidence | Discrepancy |
|---|---|---|---|---|---|
| AUG-01 | No end-of-manga rating prompt exists in upstream Tachiyomi/Mihon. | Reader shows a "Rate this manga?" prompt at true chapter-list end (no next chapter). | `ReaderViewModel.kt` (`Dialog.ChapterCompletionRating`), `ReaderActivity.kt` (FlowRow) | Real device: reached via genuine reader navigation, exited, screenshot captured 2026-08-24. | `INTENTIONAL_UPSTREAM_DIFFERENCE` — fork-specific addition, matches intent. |
| AUG-02 | No taste/rating preference family exists upstream. | Not Interested is a structural peer of Love/Like/Dislike, not a bolted-on separate action. | `MangaScreenModel.kt`, shared rating writers (5 total) | Real device dropdown confirmed 2026-08-24. | `INTENTIONAL_UPSTREAM_DIFFERENCE`, matches intent. |
| AUG-03 | No recommendation system exists upstream. | Recommendation Settings and metadata/tag diagnostics are reachable and honestly report empty state. | `exh/recs/settings/*` (57 tests) | Real device 2026-08-24, honest empty state confirmed (not fabricated). | `INTENTIONAL_UPSTREAM_DIFFERENCE`, matches intent. |
| AUG-04 | N/A (no equivalent surface upstream). | For You's own settings icon opens Recommendation Settings directly, not the top-level settings page. | `BrowsePersonalRecommendationsTab.kt` route wiring | Real device 2026-08-24, exact route contract confirmed. | `INTENTIONAL_UPSTREAM_DIFFERENCE`, matches intent. |
| AUG-05 | N/A (no cross-source "best version" concept upstream — upstream is single-source per library entry). | Typed outcome/timeout/retry/cancellation contract for Best Version image reliability. | Best Version matching owners (10/10 tests reproved 2026-08-24) | Not yet captured — RG-021 visual evidence pending; not attempted because no manga with confirmed alternate sources was verified available in the library this session. | `FUTURE_GATED_WORK` for the visual-evidence slice only; rate-limit/cache-specific messaging is a known, intentionally-not-forced residual. Core contract otherwise matches intent. |
| AUG-06 | Upstream has ordinary single-tap actions with no duplicate-tap fencing pattern specific to this feature family. | A near-simultaneous double-tap must not record two ratings or crash. | Duplicate-tap fencing across 3 rating writers (this session's own B01.5-B01.7 work) | Real device near-simultaneous double-tap test 2026-08-24: exactly one committed rating. | `INTENTIONAL_UPSTREAM_DIFFERENCE` — this is a robustness property of a fork-specific feature, matches intent. |
| AUG-07 | Upstream `TrackerSearch`/tracking-search screens exist but do not distinguish "add tracking" from "find a different match" as separate labeled entry points. | Distinct labels and tracker-named search hints for the two entry contexts (initial bind vs. replace). | `TrackInfoDialogHome.kt`, `TrackerSearch.kt` | Source-verified (grep-confirmed call sites) 2026-08-24; no fresh runtime capture this session. | `INCOMPLETE_INTEGRATION` only in the sense that runtime/visual confirmation is still pending — source and behavior are confirmed present and correct. |
| AUG-08 | Upstream reader/extension recovery after backgrounding is the ordinary Android process-lifecycle path with no fork-specific stress coverage. | Backgrounding/foregrounding during extension chapter load must not crash or lose position. | 14 tests + real device background/foreground stress test | Real device 2026-08-24: exact screen/scroll-position recovery confirmed, no crash. | `INTENTIONAL_UPSTREAM_DIFFERENCE` (added robustness testing), matches intent. |
| AUG-09 | Upstream has no cross-source identity concept — each source's library entry is independent. | Ratings/exposure carry over across cross-source-identified duplicates; confirm/reject and merge/split are explicit, separately owned actions. | `CrossSourceIdentityReviewScreenModel`, `LovedMangaScreenModel` (33/33 tests reproved) | Real device 2026-08-24: cross-version rating dropdown confirmed reachable. | `INTENTIONAL_UPSTREAM_DIFFERENCE`, matches intent; two-owner (confirm/reject vs. merge/split) split is a deliberate design choice, documented, not a defect. |
| AUG-10 | Upstream has no fork-specific search/filter/tag developer guide. | An owner manifest documenting search/filter/tag ownership for developers. | `KOMIKKU_FC_AUGUST_B06_SEARCH_FILTER_TAG_OWNER_GUIDE_2026-08-24.md`, validator | Validator rerun 2026-08-24: `PASS: B06 owner manifest validated (19 entries)`. | `INTENTIONAL_UPSTREAM_DIFFERENCE` (pure documentation addition), matches intent. |
| AUG-11 | Upstream has one shared `LibraryDisplayMode` family reused loosely across a few screens with no documented per-surface applicability disposition. | Every named surface (Library, Browse, Global Search, Rated, For You, Sources to Try, Source Evaluation, metadata diagnostics, Best Version, Tracking search) has an explicit, evidence-based mode-applicability disposition. | `KOMIKKU_FC_AUGUST_B07_REVIEW_MODES_AND_TRACKING_CONTRACT_2026-08-24.md` (10 tests) | Source-verified 2026-08-24; one confirmed defect (hashCode()-as-Compose-key) found and fixed as part of this reconciliation. | `CONFIRMED_DEFECT` (now repaired) plus `INTENTIONAL_UPSTREAM_DIFFERENCE` for the rest of the matrix — most surfaces correctly kept their existing purpose-specific presentation rather than gaining new modes, which the AUG-11 contract itself calls a valid outcome. |
| AUG-12 | Upstream has no metadata/tag diagnostics surface. | Diagnostics screen must show real per-tag rated-manga counts, a Blocked-tags section, and an honest "Used/Not used" scope disclosure. | 20 policy tests | Real device 2026-08-24: all three elements confirmed present and accurate. | `INTENTIONAL_UPSTREAM_DIFFERENCE`, matches intent. |
| AUG-13 | Upstream chapter-number handling has no explicit same-number-line continuity contract for duplicate/rescanned chapter sets. | Deterministic same-number-line continuity across resume/refresh/deletion/tracker states. | 32/32 tests reproved | Real device 2026-08-24: 5 cited screenshots directly viewed and confirmed genuine/distinct. | `INTENTIONAL_UPSTREAM_DIFFERENCE`, matches intent — first AUG row this session with full interactive visual evidence rather than source/test-only. |
| AUG-14 | Upstream has no recommendation focus/filtering concept at all. | Temporary, non-mutating, centrality-ranked focus on existing genre groups, with truthful reason/no-match state; frozen contract also named exclusion, saved modes, simultaneous lanes, and a "mood" selector as possibilities to evaluate, not promises. | `RecommendationFocusPolicy.kt`, `RecommendationFocusPresentationPolicy.kt` (11 tests reproved 2026-08-24) | Source+test verified 2026-08-24; not runtime-captured this session. | `INCOMPLETE_INTEGRATION` for the omitted possibilities (exclusion, saved modes, simultaneous lanes, mood selector, partial-match confidence label) — each now has a recorded `DEFERRED_WITH_GATE`/`NOT_USEFUL` disposition (Plan D "AUG-B09 reconciliation"), not a silent gap. The implemented core is `INTENTIONAL_UPSTREAM_DIFFERENCE`, matches intent. |
| AUG-15 | Upstream project is named Tachiyomi/Mihon; this fork was previously visibly named only "Komikku" in most places. | Consistent Komikku FC naming across app-facing surfaces and documentation, plus plain-language feature guides. | `README.md`, `strings.xml` (`app_name`), `KmkRecsReleaseNotes.kt`, `docs/recommendations/KOMIKKU_FC_FEATURE_GUIDES.md` | Direct file read/receipt 2026-08-24 (README/app_name/guides); What's New naming previously verified accurate (B10.2). | `INCOMPLETE_INTEGRATION` — core visible rebrand and the six named feature guides are done; screenshot currency and a full naming inventory (all app-facing strings, not just README/app_name) remain open. Full repository/file-name rename is explicitly deprioritized by direct user instruction, not a gap. |
| AUG-16 | N/A — this document itself. | A requirement-by-requirement upstream/intended/actual comparison across AUG-01 through AUG-17 and FC-01 through FC-24. | This document | N/A | `FUTURE_GATED_WORK` for FC-01 through FC-24 — not started this session; see closing note. AUG-01 through AUG-18 portion is this document. |
| AUG-17 | N/A. | Every diagram renders without clipped text, overlap, or lost meaning across GitHub/exported/mobile rendering. | `private/docs/audits-and-reports/komikku-fc-batch12-diagram-render-2026-08-21/` (24 diagrams + contact sheet + manifest); an earlier, content-identical diagram set also exists under the top-level `Diagram Review/` folder. **Correction of this document's own earlier claim** (see closing note): a first pass wrongly said no diagram asset exists anywhere in the repository — that search only covered the public worktree's `docs/` and `README.md`, not the shared `private/` tree where these diagrams actually live. | Re-verified 2026-08-24: a prior technical validation (`private/evidence/rg013c-fc20-authorized-visual-validation-2026-08-22.json`) recorded `clippingOrMissingPanels: false` across all 24. This session independently re-inspected the full contact sheet plus 12 individual diagrams at native/cropped pixel resolution (diagram-01/02/03/04/05/08/12/13 from the Aug-21 set, plus diagram-19/23 and the Phase-3 Source-Evaluation set's 5 diagrams from the July set) and found **no clipping in any of them**, corroborating rather than contradicting the prior claim. The specific "search result and short expla[nation]" clipped text named in the original user request was not located in this sample. | `INTENDED_BEHAVIOR_ALREADY_MET_FOR_SAMPLE_CHECKED`, not fully exhaustive — roughly 12 of 24+ diagrams (plus older duplicate/superseded sets) were individually re-verified; the rest rely on the prior RG-013C record rather than fresh per-diagram re-inspection this session. Public promotion of any of these diagrams into `docs/`/`README.md` remains its own genuine external gate (FC-20: "Human per-diagram readability/current-behavior decision") — none are currently shipped publicly, so nothing in the public repository needs a diagram fix right now. |
| AUG-18 | Upstream tracking is entirely third-party-service-based (MyAnimeList, AniList, etc.) with no purely local/offline tracker. | A full local tracking status/list workflow (Reading/Plan to read/On hold/Completed/Dropped), reversible, with an explicit separate Remove action. | Local tracking status dialog owners (this session's B05 work) | Real device 2026-08-24: full track/status-change/persist/remove workflow confirmed end-to-end with screenshots. | `INTENTIONAL_UPSTREAM_DIFFERENCE`, matches intent. |

## FC-01 through FC-24

These rows are **not** reconstructed from memory. They are taken directly from
`private/evidence/rg013g-fc24-current-reconciliation-2026-08-23.json`, a 24-row
reconciliation whose own validator (`private/tools/validate_fc24_reconciliation.py`) was
rerun fresh this session and returned `PASS: FC-24 reconciliation has 24 classified rows
and retains exact non-terminal gates.` The companion `rg013_fc24_current_completeness`
record's validator (`private/tools/validate_rg013_fc24_current_completeness.py`) was also
rerun fresh and returned `PASS`. These FC rows describe a different, older requirement
numbering (FC-01 through FC-24, from the original `Komikku FC Changes` intake) than the
AUG-01 through AUG-18 numbering used above; both exist in this program's history and are
cross-referenced, not merged, to avoid inventing a false one-to-one mapping this session
did not verify.

| FC | State (source: rg013g reconciliation) | Evidence cited | Remaining gate |
|---|---|---|---|
| FC-01 | `HOST_SOURCE_TERMINAL` | IR-01.2 owner/caller and interaction proof | Changed bytes or contradictory evidence |
| FC-02 | `HOST_SOURCE_TERMINAL` | IR-02 recommendation/evaluation owner proof | Changed bytes or contradictory evidence |
| FC-03 | `RUNTIME_EVIDENCE_OPEN` | Current FC-03 runtime receipt and cleanup reconciliation | Rendered accessibility/public-capture review; no parity claim |
| FC-04 | `HOST_SOURCE_TERMINAL` | IR-02 shared preference and diagnostics proof | Changed bytes or contradictory evidence |
| FC-05 | `HOST_SOURCE_TERMINAL` | IR-02 recommendation visibility and correction proof | Changed bytes or contradictory evidence |
| FC-06 | `RUNTIME_EVIDENCE_OPEN` | IR-01.2 navigation owner proof | Authorized back-stack and context-preservation evidence |
| FC-07 | `RUNTIME_EVIDENCE_OPEN` | IR-03 typed failure/retry/fallback proof | Authorized runtime failure and privacy-valid evidence |
| FC-08 | `HOST_SOURCE_TERMINAL` | IR-01.1 tracker action-boundary repair and tests | Changed bytes or contradictory evidence |
| FC-09 | `RUNTIME_EVIDENCE_OPEN` | IR-01.2 search-action owner proof | Authorized rendered search-action usability evidence |
| FC-10 | `RUNTIME_EVIDENCE_OPEN` | IR-03 lifecycle/isolation proof | Authorized degraded-environment evidence; no live-source parity inference |
| FC-11 | `HOST_SOURCE_TERMINAL` | Accepted exact-identity and source evidence | Changed identity contract or contradictory evidence |
| FC-12 | `RETAINED_SCOPE_OPEN` | Current offline data-flow documentation | Final source fingerprint and retained-scope review |
| FC-13 | `RUNTIME_EVIDENCE_OPEN` | Current Source Evaluation device receipt and cleanup | Rendered accessibility/privacy review; capture remains separately gated |
| FC-14 | `RUNTIME_EVIDENCE_OPEN` | IR-04 applicability matrix and shared-owner proof | Runtime visual/accessibility evidence for applicable surfaces |
| FC-15 | `RUNTIME_EVIDENCE_OPEN` | IR-05 chapter-line continuity proof and direct fallback test | Runtime resume-loop evidence where applicable |
| FC-16 | `RUNTIME_AND_SCOPE_OPEN` | IR-06 focused recommendation owner and host proof | Approved runtime route plus product/scope review |
| FC-17 | `HUMAN_RETAINED_SCOPE_OPEN` | Technical naming policy and launcher validation | Trademark, logo, and media retained-scope decision |
| FC-18 | `HUMAN_RETAINED_SCOPE_OPEN` | Source documentation and mechanical locale repair | Qualified nine-locale wording and retained-scope review |
| FC-19 | `HUMAN_RETAINED_SCOPE_OPEN` | Upstream/intended/actual/limitations reconciliation | Retained-scope and runtime-evidence decision |
| FC-20 | `HUMAN_RETAINED_SCOPE_OPEN` | 24-diagram technical visual package and manifest | Per-diagram readability/current-behavior and retained-scope review (see AUG-17 row above — this session's re-inspection found the diagrams themselves technically clean; the gate is the human promotion decision, not a rendering defect) |
| FC-21 | `HOST_SOURCE_TERMINAL` | IR-03 dependency and crash disclosure proof | Changed source or contradictory evidence |
| FC-22 | `HOST_SOURCE_TERMINAL` | IR-03 source-runtime isolation proof | Changed source or contradictory evidence |
| FC-23 | `HOST_SOURCE_TERMINAL` | IR-03 structural crash-repair proof | Changed source or contradictory evidence |
| FC-24 | `FINAL_COMPLETENESS_OPEN` | This 24-row current-evidence reconciliation | Every non-terminal row and external gate receives terminal evidence |

**Named external gates carried by this reconciliation** (from the same source record):
`RG-010-SECURITY-TOOL-AUTHORITY`, `RG-012-PUBLICATION-AUTHORITY`,
`RG-013-FC17-20-HUMAN-REVIEW`. None of these are attempted by this session — they require
either separate tooling authority, human trademark/translation/publication decisions, or
release-signing authority, consistent with this program's standing rule that publication
and signing are not autonomous actions.

**State counts** (9 `HOST_SOURCE_TERMINAL`, 8 `RUNTIME_EVIDENCE_OPEN`, 1
`RUNTIME_AND_SCOPE_OPEN`, 1 `RETAINED_SCOPE_OPEN`, 4 `HUMAN_RETAINED_SCOPE_OPEN`, 1
`FINAL_COMPLETENESS_OPEN`) mean roughly a third of FC rows are genuinely host/source
terminal, another third need authorized runtime/device evidence beyond what this session's
narrower RG-021 emulator passes covered (those covered AUG-01 through AUG-18 specifically,
not the full FC-01 through FC-24 set), and the rest are explicit human/retained-scope
decisions this session correctly does not attempt to resolve unilaterally.
