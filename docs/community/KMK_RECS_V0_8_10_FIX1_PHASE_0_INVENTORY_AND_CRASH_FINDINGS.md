# KMK-Recs v0.8.10-fix1 — Phase 0 Findings: Repository Inventory And Crash Investigation

Date: 2026-07-17

Actual model/effort for this phase: **Opus, high effort** (matches the plan's Phase 0/1 recommendation).

Status: **Phase 0 gate deliverables complete** — both the repository inventory and the crash
investigation are documented below. This is the mandatory gate that must precede any Phase 2+ UI
conformance work.

HEAD at time of writing: `e407d54c1` (doc indexes linked) plus this phase's commits.

---

## Part A — Application-wide crash investigation

### A.1 Reported symptom

Settings opens fine, but navigating to Library, For You, manga detail, Browse, reader, Source
Evaluation, rated collections, or What's New "can close the app." No reliable stack trace was
provided. The strongest structural signal is the split: pure-preference **Settings works** while
nearly every manga-/content-bearing screen is reported unstable.

### A.2 Environment limitation (disclosed, not worked around)

There is **no physical or emulated Android device available in this environment** — confirmed
directly: `adb devices` returns an empty device list, and no emulator image is installed. The app
therefore cannot be launched, navigated, backgrounded, or process-killed here. Runtime reproduction
of a device-only crash is impossible in this environment. This is the same standing limitation that
applied to every prior phase's device matrix, now also applicable to the crash-reproduction gate.

Per the plan, static root-cause analysis was exhausted first before falling back to the
device-only conclusion.

### A.3 What was checked, and what it ruled out (with evidence)

| # | Hypothesis | Method | Result |
| --- | --- | --- | --- |
| 1 | KMK Injekt module not registered (would crash every taste-using screen while Settings works) | Grepped registration | **Ruled out.** `KMKDomainModule` is imported at `App.kt:144` (`Injekt.importModule(KMKDomainModule())`). Every interactor a crashing screen resolves (`GetTasteProfile`, `GetTasteSuggestions`, `GetTasteDiagnostics`, `GetCrossSourceGroupPrimary`, …) is registered, constructor arities match. |
| 2 | Broken `Manga` Java-serialization proxy (the 1.14.0 reconciliation made `Manga` proxy Java serialization through kotlinx JSON; Android uses Java serialization to save instance state on backgrounding/process death; this is a factor shared by every manga-bearing screen but not by Settings) | New JVM test driving the real `ObjectOutputStream`/`ObjectInputStream` round trip | **Ruled out as broken.** `MangaSerializationRoundTripTest` (3 tests) proves a default non-favorite `Manga`, a `Manga` with a populated `memo` JsonObject, and a `Manga` with a full set of populated optional fields all survive the exact Android background-save path intact (`writeReplace` → `JavaToKotlinXSerializable` → `readResolve`). The reconciliation's fix is sound for the common case. Nothing previously exercised this real round trip — the reconciliation verified compile + unit tests only — so this closes a genuine coverage gap even though it did not surface the crash. |
| 3 | Migration 63 / schema mismatch causing first-DB-query crash while preference-only Settings works | Re-ran real-SQLite migration suites | **Ruled out.** `KmkMigrationTest` (24 tests, real `JdbcSqliteDriver`) and `Kmk114ReconciliationMigrationTest` (real SQLite through migration 63) pass; migrations 1–63 are gapless and append-only; `mangas.memo`/`chapters.memo` column order is consistent between `63.sqm` and the `.sq` definitions. |
| 4 | `rememberSaveable` holding a non-Bundle-safe value (crash on state restoration) | Grepped every `rememberSaveable` in `exh/recs` | **Ruled out for KMK code.** All are nullable/primitive (`String?`, `Long?`, `Boolean`, `Int`) or a bare enum (Java-`Serializable`, Bundle-safe). No non-serializable objects. |
| 5 | KMK Voyager `Screen` holding a non-serialization-safe constructor arg (Voyager `Screen : java.io.Serializable`; the whole back stack is Java-serialized on background) | Enumerated every KMK `Screen` constructor | **Ruled out for KMK code.** Every KMK screen arg is primitive identity only (`Long`, `String`, `Int?`, `Boolean`, `ArrayList<Long>`, or a `sealed interface Args : Serializable` whose variants carry only primitives) — there is even an explicit in-code comment enforcing "primitive identity only … Voyager Screen must stay Serializable/Parcelable-safe." No KMK screen holds a `Manga`/`Source`/`Chapter` directly. |
| 6 | Synchronous throw in a central screen model `init` (crash-on-open) | Read `BrowsePersonalRecommendationsScreenModel.init` (For You) | **Ruled out for For You.** `init` only does `screenModelScope.launch { load(...) }`; no synchronous work, and a coroutine failure is scope-contained/logged, not an app-killing uncaught throw. |
| 7 | Unsafe source cast after the 1.14.0 `CatalogueSource → Source` widening / `baseUrl → getHomeUrl()` change (compiles, crashes at runtime for some source types) | Grepped source casts in `exh/recs` | **Ruled out for KMK code.** All source casts are safe (`as? HttpSource ?: return`, `source?.baseUrl ?: ""`, `(source as? HttpSource)?.baseUrl`). No unsafe `as CatalogueSource`. |
| 8 | KMK-Recs What's New rendering crash (What's New is the structural outlier in the crash list) | Read `KmkRecsWhatsNewScreen` | **No defect found.** It is argless and delegates to the official `WhatsNewScreen`/`MarkdownRender`. The one side effect (mark-seen) is correctly inside a `LaunchedEffect`. Note: the historical Markdown itself is being reworked in Phase 6 (lossless New/Improve/Fix conversion) with validity tests — that work will independently prove the Markdown contains only supported constructs. |

### A.4 Existing crash-diagnostics infrastructure (already present — must NOT be duplicated)

Komikku already ships a complete uncaught-exception capture path, and the conformance rules forbid
building a parallel one:

- `eu.kanade.tachiyomi.crash.GlobalExceptionHandler` installs a `Thread.setDefaultUncaughtExceptionHandler`
  that logs the throwable and launches `CrashActivity` with the **full stack trace**
  (`Throwable.stackTraceToString()`), then delegates to the default handler.
- `eu.kanade.tachiyomi.crash.CrashActivity` + `eu.kanade.presentation.crash.CrashScreen` display the
  crash and expose a "Dump crash logs" action.
- `eu.kanade.tachiyomi.util.CrashLogUtil` writes a shareable crash/log dump.

**Implication:** the reason "no reliable stack trace was provided" is a *reporting* gap, not a
tooling gap. The app already captures the exact first meaningful exception with its full stack trace
on any real device. The correct path to a definitive fix is to reproduce on a device and read the
existing CrashActivity output / dumped log — not to add new diagnostics (which would violate the
"do not create a second error-reporting architecture" rule) and not to guess a fix.

### A.5 Honest conclusion for the crash gate

- Thorough static analysis was performed and the major structural crash vectors were **ruled out
  with evidence** (table A.3).
- **No single definitive, statically-reproducible root cause was found** in the current tree, and
  the app **cannot be run on a device in this environment** to capture the real exception.
- This is therefore a **narrowed non-reproduction**, which the plan explicitly permits when static
  analysis is exhausted. It is **not** claimed as "fixed."
- One genuine, permanently valuable regression test was added in the course of the investigation
  (`MangaSerializationRoundTripTest`) — it guards the reconciliation's background-save serialization
  fix, a previously untested hot path shared by every manga screen.
- **Device-only follow-up required before the release gate:** on a real phone and tablet, reproduce
  by navigating to each reported screen (fresh launch, after backgrounding, and after
  process-recreation via "Don't keep activities"), and capture the CrashActivity stack trace / dumped
  log via the existing `CrashLogUtil`. That captured first-exception + failing symbol is the input
  needed to fix at the narrowest correct layer and add a targeted regression test. This is recorded as
  an explicit external-device blocker, not a completed item.

---

## Part B — KMK repository surface inventory

Scale (mechanical counts): 153 `exh/recs/**` Kotlin files, 14 `exh/ocr/**` files, **454** files
under `app/src/main/java/**` carrying `// KMK -->` markers, 19 KMK migrations (45–63), 918 KMR
strings in `i18n-kmk` base locale, 5 KMK backup models, 9 reader timer/schedule files, 102 test
files under `app/src/test/java/exh/**`.

Because a 454-row per-file table is neither maintainable nor decision-useful, the inventory is
organized by **surface**, per the plan's requested columns (surface, entry point, screen/model,
domain/repo/prefs, localization, route, tests, conformance status). "Conformance status" classifies
each surface's divergence from official Komikku 1.14.0 as: **intentional** (a deliberate KMK feature
difference to preserve), **mechanical** (a type/rename port already reconciled), **stale** (docs/
comments out of date), **accidental** (an unintended divergence to correct), or **unresolved** (needs
Phase 2+ work or device verification).

| Surface | Entry point | Screen / ScreenModel | Domain / repo / prefs | Localization | Tests | Conformance status |
| --- | --- | --- | --- | --- | --- | --- |
| Recommendation Settings (index + 7 categories + search) | Gear icon in For You tab; `RecommendationSettingsIndexScreen` | `RecommendationSettings*Screen` (For You, Source Priority, Taste & Tags, Discovery, Diagnostics), `RecommendationSettingsSearchScreen`, `RecommendationsSettingsScreenModel` | `SourcePreferences`, taste interactors | KMR | anchor-scroll, search-index, section-summary tests | **Intentional** (parallel search index because these screens are NOT built on the official `Preference`/`SearchableSettings` DSL). **Unresolved for Phase 2**: the plan's core requirement is to move these toward official `SearchableSettings`/`PreferenceScaffold`/`PreferenceItem` patterns rather than the current bespoke `LazyColumn` + custom rows. |
| Source Evaluation | Recommendation Settings index → direct route; `SourceEvaluationScreen` | `SourceEvaluationScreenModel` (+ runner, job, policies) | `SourceEvaluationRepository`, safety repos, `SourcePreferences` | KMR | large policy/reducer suite (candidate filter, continuation, reconciliation, completion lifecycle, …) | **Intentional** feature. v0.8.10 Phase F already corrected the completion-lifecycle staleness. **Unresolved for Phase 2**: first-viewport density (quarantine/blocked sections), full official top-bar/dialog conformance. |
| For You / Browse recommendation rows | Browse → For You tab; `BrowsePersonalRecommendationsTab` | `BrowsePersonalRecommendationsScreenModel` | taste interactors, `RecommendationCache`, discovery memory/progress repos, `SourcePreferences` | KMR | scorer, visibility, query planner, dedup, group-preview coordinator tests | **Intentional** feature. **Unresolved for Phase 3**: official lazy-list/card spacing + stable-key/cancellation audit. |
| Rated collections (Loved/Liked/Disliked) + grouping | Library shortcut / manga menu; `RatedMangaScreen`, `LovedMangaScreen` | `LovedMangaScreenModel`, `RatedSelectionReducer`, `LovedMangaDuplicateGrouper` | `TasteRepository`, cross-source link/group interactors | KMR (`rated_manga_search_hint`, …) | sort/grouping/exclusivity/search-filter tests | **Intentional** feature. **Unresolved for Phase 4**: shared collection scaffold conformance, localized-title-before-MangaScreen guarantee. |
| Cross-extension matching / Best Version | manga menu → `CrossExtensionMatchScreen`, `BestVersionCompareScreen` | respective screen models | quality-signal repo, `SourcePreferences` | KMR | matcher/sampler/selection tests | **Intentional** feature. **Unresolved for Phase 4**: cancel/rotation/back state safety re-verification. |
| Reader completion prompt / timer / schedule | `ReaderActivity`/`ReaderViewModel`; `eu.kanade.tachiyomi.ui.reader.timer`/`schedule` | reducers + dialogs | `SourcePreferences`, schedule store | KMR | reducer/codec/entitlement tests | **Intentional** feature. v0.8.10 Phase A already deferred the prompt to reader exit. **Unresolved for Phase 5**: official reader dialog/time-picker conformance re-check. |
| OCR downloaded-text search | `exh/ocr/**` | OCR screens/interactors | `OcrIndexRepository` (migration 55 status columns) | KMR | classifier/normalizer/ranker/tile/skip tests | **Intentional** feature. **Unresolved for Phase 5**: official settings-navigation conformance where OCR UI lives. |
| Historical What's New | About → KMK-Recs What's New; `KmkRecsWhatsNewScreen` | official `WhatsNewScreen`/`MarkdownRender` | `KmkRecsReleaseNotes.MARKDOWN` | in-file Markdown | `KmkRecsReleaseNotesTest` | **Unresolved for Phase 6**: 83 pre-v0.8.9 entries remain in flat-bullet format. The v0.8.10 Phase G "keep-as-is" decision is **explicitly overridden** by this pass — a lossless New/Improve/Fix conversion of all ~84 entries with a before/after audit test is required. |
| Database / migrations 45–63 | SQLDelight | — | `.sqm` 45–63, `.sq` tables | — | `KmkMigrationTest`, `Kmk114ReconciliationMigrationTest` | **Mechanical/intentional**, verified append-only in v0.8.10 Phase I. `63.sqm` stale "not-yet-applied" wording already fixed. No open item beyond the plan's request for one combined multi-area upgrade test (Phase 7). |
| Backup / sync / proto 620–629 | `data/backup/**` | — | `Backup.kt` fields 620–627, taste creators/restorers, `BackupDecoderErrorPolicy` | — | round-trip + decoder-hardening tests | **Intentional**, additive-only, verified in v0.8.10 Phases H/I. |
| `Manga` domain model serialization | shared by all manga screens | — | `domain/.../Manga.kt` | — | **new** `MangaSerializationRoundTripTest` | **Mechanical** (1.14.0 reconciliation port). Now covered by a real round-trip regression test (this phase). |

### B.1 Divergences flagged for correction in later phases

- **Accidental / to-correct (Phase 2+):** the Recommendation Settings screens use a bespoke
  `LazyColumn` + custom control rows + a parallel search index instead of the official
  `SearchableSettings`/`Preference` DSL. This is the central conformance target of the whole pass.
- **Unresolved (Phase 6):** the 83 pre-v0.8.9 historical What's New entries in flat-bullet format.
- **Unresolved (device-only):** the reported application-wide crash — narrowed, not reproduced (Part A).
- **Stale (already fixed):** `63.sqm` "not-yet-applied" wording (v0.8.10 Phase I).

### B.2 Confirmed sound (no action needed)

Injekt registration graph; `Manga` background-save serialization; migrations 1–63; KMK
`rememberSaveable`/Voyager-arg Bundle-safety; KMK source-type casts; existing crash-capture
infrastructure.

---

## Phase gate assessment

Both required gate deliverables are complete: the crash investigation is documented (thorough static
analysis, evidence-based rule-outs, honest narrowed non-reproduction with an explicit device-only
blocker) and the repository inventory is documented (surface table + divergence classification).
Phase 2+ UI conformance work may proceed, with the understanding that the crash's definitive fix
remains gated on a real-device stack-trace capture that cannot be produced in this environment.
