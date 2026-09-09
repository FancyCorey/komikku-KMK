# KMK Future Recommendation Quality Plan: Latest Catalogue Discovery

Date: 2026-08-08
Status: OPEN - FUTURE IMPLEMENTATION ONLY
Implementation state: NOT STARTED
Scope: Browse > For You recommendation quality; no source or ranking behavior changes in this pass.

## Intent

The current For You results are materially better than the earlier baseline, but a portion of
the result set still feels generic or over-exposed. User observation indicates that some source
detail recommendation pages produce stronger discovery than the broad Popular catalogue lane,
especially when the source's Latest catalogue exposes newer or less familiar titles. This plan
records a structural investigation and implementation proposal; it does not authorize coding,
database migration, APK installation, or live-device validation.

The proposal is to use a source's Latest catalogue as a bounded discovery signal alongside the
existing personalized/search and Popular inputs. Latest must not become the universal primary
source because catalogue capabilities, metadata quality, ordering semantics, pagination, and
network behavior differ by source.

## Problem Statement

- Popular is a historical prominence signal and can repeatedly surface familiar or generic titles.
- Latest can expose newer and less-seen catalogue items, but recency alone is not relevance or
  quality.
- A source may not expose a reliable Latest page, may return weak metadata, or may use a Latest
  label with different semantics.
- Existing safeguards must remain authoritative: blocked-tag exclusion, minimum chapter count,
  known/seen/rated filtering, cross-source deduplication, source-fit eligibility, metadata
  confidence, privacy controls, and per-source failure isolation.

## Required Audit Before Coding

Trace the real call paths and document exact symbols before edits:

1. Source catalogue capability discovery and category names, including Popular and Latest.
2. For You discovery planning, pagination, retry/backoff, candidate memory, and cache keys.
3. Shared candidate visibility and source-selection policies.
4. Source Evaluation catalogue sampling and source-fit persistence.
5. Cross-source identity matching and deduplication.
6. Existing minimum chapter, blocked-tag, language, seen, rated, and known-title filters.
7. UI provenance/explanation surfaces and any Action History or Evaluation Mode boundary.

The audit must distinguish a real Latest catalogue route from a generic search, feed, or source
detail recommendation endpoint. It must also record sources where Latest is unavailable or its
ordering cannot be trusted.

## Proposed Structural Design

### 1. Typed discovery lanes

Represent candidate provenance explicitly rather than inferring it from a string label. At minimum:

- `PERSONALIZED_SEARCH` or existing equivalent
- `LATEST_CATALOGUE`
- `POPULAR_CATALOGUE`
- existing detail/source recommendation provenance, if present

Each lane carries source identity, page/cursor, fetched timestamp, capability confidence, and an
exhaustion/failure reason. Cache and candidate-memory fingerprints must include the lane and
source capability version so Popular results cannot masquerade as Latest evidence.

### 2. Bounded Latest exploration

Add a configurable or centrally bounded Latest exploration budget per refresh. Use a source's
Latest lane only when the source capability is confirmed and the source is eligible for the normal
For You pipeline. Respect cancellation, retry/backoff, rate limits, and the existing per-source
failure boundary. If Latest is missing, malformed, empty, or unavailable, continue with the
existing lanes without lowering result integrity.

Latest should contribute a quota or exploration pool, not replace personalized relevance. The
planner must be able to finish without Latest and must not repeatedly crawl the same exhausted
page.

### 3. Novelty and quality-aware ranking

Add a separately named, inspectable signal for underexposure/novelty. Do not use novelty as an
unbounded popularity inverse. The ranking contract should combine, with explicit caps and tests:

- personal relevance and taste evidence;
- source-fit and catalogue metadata confidence;
- freshness/latest provenance;
- novelty or underexposure relative to local known IDs, recent exposures, and prior opens;
- minimum chapter and other existing eligibility rules;
- source safety, language, blocked tags, and deduplication.

The design must prevent a new but irrelevant or low-quality title from outranking a strongly
personalized result solely because it came from Latest. It must also avoid penalizing a title just
because local exposure history is unavailable; missing novelty data must fail open to the existing
ranking contract.

### 4. Source-specific capability and quality handling

Source Evaluation should record whether Popular and Latest were actually available and usable,
without treating a missing Latest route as a source defect. Latest yield metrics may inform future
source-fit diagnostics, but must not silently change the user's source priority or write remote
state. A source with poor Latest metadata can remain usable through another evidence lane.

### 5. Explainability

Where the existing UI supports provenance, expose a privacy-safe, user-readable reason such as
"New from a source you use" or "Matches your taste". Do not expose raw URLs, internal query
strings, private diagnostics, or implementation-only scoring fields. Any new preference must have
localized labels, theme coverage, accessibility semantics, and a documented default.

## Fixture and Evidence Contract

Host tests must use deterministic local catalogue fixtures with separate Popular and Latest pages,
duplicate titles across lanes, blocked tags, low chapter counts, known titles, sparse metadata,
stale pages, empty pages, pagination exhaustion, and per-source failures. No test may depend on
internet access or a live extension.

Required evidence classes for a later implementation pass:

- Latest capability present and absent, with graceful fallback.
- Latest candidate accepted, rejected, deduplicated, and ranked beside Popular/personalized items.
- Generic or over-exposed candidate reduced without hiding a strong personalized match.
- Minimum chapter, blocked-tag, language, seen/rated, and known-title filters still apply.
- Cancellation, retry/backoff, source failure isolation, cache invalidation, and lifecycle restart.
- Privacy-safe provenance and no remote writes.
- Compatibility with existing For You, source evaluation, Sources To Try, Loved/Liked/Disliked,
  and original Browse behavior.

## Acceptance Criteria

This item may move from OPEN to IMPLEMENTED only when a later approved pass demonstrates all of
the following:

1. Latest is an additive, bounded discovery lane with deterministic fallback.
2. Popular remains available and is not silently treated as Latest.
3. Strong personalized relevance still outranks novelty-only candidates.
4. Novelty reduces repetitive/generic exposure using local, privacy-safe history only.
5. Existing filters, source-fit safeguards, deduplication, minimum chapter count, and failure
   isolation remain intact.
6. Sources without a reliable Latest route do not block or degrade the For You page.
7. Focused unit/integration tests and local fixtures cover success, failure, cancellation,
   malformed input, concurrency, lifecycle, compatibility, privacy, and rollback/cache behavior.
8. Documentation, versioning, route evidence, and any UI copy are reconciled before device work.

## Explicit Non-Goals for This Pass

- No implementation or ranking change.
- No source scraping or network calls.
- No APK build/install or ADB/emulator work.
- No database migration or fixture mutation.
- No claim that Latest is universally better than Popular.

## Evidence-Driven Existing-App Extension Gates

This proposal adopts the project's evidence-driven existing-app extension
standard. Before authorization moves from planning to implementation, the
owning batch must complete all applicable gates below:

1. **Repository intake:** inspect the mounted For You routes, source catalogue
   contracts, source-selection and candidate policies, storage owners,
   migrations, export/import/deletion behavior, design primitives, current
   tests, and current-build visual evidence. Classify inherited Komikku
   behavior, accepted KMK behavior, shared integration surfaces, and any
   obsolete surface before proposing edits.
2. **Product reconciliation:** record the user outcome, familiar behavior to
   preserve, explicit non-goals, and the distinction between exposure,
   interaction, and explicit negative feedback. Do not turn an ignored card
   into a dislike or hidden-title record.
3. **Research packet:** verify the mechanism against the current repository,
   official platform/source documentation where applicable, relevant licensed
   implementation evidence, and current recommendation workflows. Record
   limitations, capability assumptions, licensing boundaries, and confidence.
4. **Independent challenge:** try to falsify the Latest and novelty claims
   through missing Latest routes, misleading source ordering, stale pages,
   reload, cancellation, offline behavior, malformed metadata, duplicate
   identities, sparse history, privacy loss, and personalized results being
   displaced by novelty-only candidates. A second recurrence of the same root
   cause requires a dedicated rework decision instead of another blind retry.
5. **Implementation packet:** before coding, name exact files and symbols,
   typed provenance and state transitions, exposure-event ownership, retention
   and deletion, cache/migration/export implications, fallback and recovery,
   privacy/security boundaries, tests, visual evidence, accessibility checks,
   rollback, and rejected alternatives. One batch must own each new behavior.
6. **Visual and interaction validation:** use screenshots from the current
   build on the approved targets. Check the For You quick-access panel and any
   changed settings/detail surface for hierarchy, centering, spacing, density,
   hit targets, themes, localization pressure, accessibility, loading/empty/
   error/offline states, lifecycle, and focus/back behavior.
7. **Completion rule:** the future batch is not complete when code merely
   exists or tests merely pass. Code, focused and complete validation, current
   visual evidence, security/privacy/data review, rollback, and durable
   records must agree. Any unresolved contract or evidence limitation remains
   decision-gated or blocked rather than being presented as proven.

## Decision Gate

Keep this plan OPEN until the user explicitly approves a future implementation pass and the source
capability audit confirms that the required Latest catalogue contract exists for enough sources to
justify the bounded lane. If the audit finds that Latest is not a stable cross-source contract,
retain the plan as a source-specific opt-in experiment rather than adding a global assumption.

## Product Decisions Recorded - 2026-08-08

### Ignored is not negative feedback

The fact that a title was visible and not opened must not be interpreted as dislike, a blocked
genre, or proof that the title is low quality. The system may record privacy-safe exposure history
for repetition control, but it must keep these concepts separate:

- **Exposed:** the title was actually shown in a loaded For You result slate.
- **Opened/interacted:** the user opened, rated, saved, dismissed, or otherwise acted on it.
- **Explicit negative feedback:** the user used Not Interested, dislike, blocked-tag, source, or
  another intentional exclusion action.

Only explicit feedback may change eligibility or taste preferences. Exposure can reduce repetition
temporarily, while an absence of interaction remains an unknown outcome.

### Latest applies wherever the source contract supports it

Latest should be evaluated for every source that exposes a reliable Latest catalogue route. It is an
additional evidence lane, not a universal replacement for Popular, personalized search, or detail
recommendations. A source without a trustworthy Latest route must continue through its other lanes
without penalty or fabricated Latest evidence.

### Latest follows the same eligibility contract

Latest candidates remain subject to the existing genre/tag blocks, language rules, minimum chapter
count, known/seen/rated filtering, source safety, metadata confidence, source-fit eligibility,
deduplication, and failure isolation. Latest provenance must never allow a candidate to bypass a
normal exclusion rule.

### Controlled exploration, not deterministic repetition

Personalized matches remain the dominant lane, while Latest receives a small bounded exploration
quota. The future design should use an explore/exploit-style slate policy: most results should
reflect demonstrated taste, while a limited portion may test unfamiliar candidates that pass all
eligibility checks. The exploration portion should adapt from explicit outcomes and source quality,
not from the user's failure to open a card. No opaque machine-learning dependency is required for
the first implementation; a deterministic policy with explicit budgets, decay, and feedback is
preferred for auditability.

### Exposure window must be configurable

"Recently exposed" is a repetition-control signal, not a permanent suppression. The future
settings design should offer a configurable exposure window, with an initial candidate range of
7 or 14 days and an explicit default chosen during implementation. The stored event should be the
privacy-safe fact that a loaded result was shown, not a raw URL or private content snapshot. The
window should decay or expire automatically, and user interaction should be recorded separately.

The product decision is now **14 days by default**. A card counts as exposed when it is present in
a loaded, visible result state, including a source/topic listing or the initial visible For You
slate. Fetching a page that the user never receives as visible UI must not count as exposure.

### Soft reranking instead of removal

Exposure must reorder candidates, never delete or permanently hide them. After hard eligibility
filters, deduplication, and source/topic grouping have completed, apply a bounded soft penalty to a
candidate that has been repeatedly visible across the 14-day window but has no meaningful positive
or explicit negative interaction. The no-interaction state means the title is not in the local
library, has no local rating, and has no confirmed local tracker association; it does not mean the
user dislikes the title.

The penalty should move that candidate lower within its source/topic listing so another eligible,
less-exposed candidate can occupy an earlier visible position. The candidate remains reachable by
scrolling, remains eligible for later refreshes, and may rise again when its exposure signal decays
or when the user interacts with it. This is a presentation-order adjustment, not a blocklist,
negative taste signal, or deletion operation.

The reranking stage must preserve the product's visible structure: source/topic sections remain
recognizable, source quotas and the existing top-result budget remain valid, and a stale exposed
candidate must not displace a strongly personalized candidate merely because the latter is newer.
Stable tie-breakers and a deterministic score explanation are required so refreshes do not cause
unnecessary list jitter.

### Provenance belongs in diagnostics, not on every card

For You cards do not need extra explanatory badges by default. Source Evaluation or its detail
surface may expose a privacy-safe provenance summary for auditing, such as Latest availability,
catalogue yield, or taste-match evidence. Any visible explanation must be localized, accessible,
theme-safe, and free of raw URLs, internal queries, or private diagnostic payloads.

### Separate visual follow-up

The For You quick-access panel for sources, taste and filters, Source Evaluation, Sources To Try,
and Management and diagnostics is a separate open UI-quality item. The reported problems are poor
centering, weak balance, excessive empty space, undersized hit targets, and visual mismatch with
the surrounding settings surfaces. A later screenshot/code audit must inspect the real panel and
its destination navigation, then define a reusable layout fix. It must not be mixed into the
Latest ranking work or treated as solved by this proposal.
