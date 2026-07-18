# Implementation Plan Standard

Status: living project standard
Purpose: define how future implementation plans must be researched, written, reviewed, implemented, tested, documented, and handed off.

## 1. Core principle

An implementation plan is an executable engineering specification, not a summary of an idea.

A capable implementer should be able to read the plan and determine:

- what currently exists;
- where it exists;
- how the current data and control flow work;
- what must be reused;
- what must change;
- what must be added;
- what must be removed;
- what must remain untouched;
- how errors and edge cases behave;
- how the result is tested;
- what proves the work is complete.

Do not use broad phrases such as â€œimprove the UI,â€ â€œmake it efficient,â€ â€œadd error handling,â€ or â€œalign with the existing systemâ€ without defining the exact meaning, affected files, behavior, and acceptance test.

When a bug appears in several screens, do not plan a collection of local patches first. Identify the
shared boundary, contract, dependency, or lifecycle path that those screens have in common. Local
guards are acceptable only as temporary containment when the plan also records the structural fix or
proves that no shared boundary exists.

## 2. Required planning workflow

### Step 1: establish scope

Record:

- feature name;
- user problem;
- version line;
- whether the work is a fix, minor feature, major feature, refactor, security change, or release-readiness pass;
- whether it is one final build or multiple independently releasable builds;
- explicit non-goals;
- development handoff requirements;
- whether application-visible wording has restrictions.

Never infer a new version line from convenience. Follow the project's established version structure.

### Step 2: inspect the repository

Before writing the solution, inspect:

- current implementation files;
- callers and consumers;
- models, repositories, interactors, and persistence;
- preference declarations and defaults;
- navigation and screen entry points;
- localization resources;
- dependency-injection registrations;
- backup/sync models;
- database schema and migrations;
- existing tests;
- current release notes and documentation indexes;
- official upstream/project conventions.

Use the actual code as the source of truth. Planning documents may be stale. Mark every feature as implemented, partial, deferred, historical, or unverified based on code evidence.

If the code contradicts the planning documents, document the contradiction before choosing a fix.
Do not make Claude infer the missing decision from context; write the exact implementation decision
into the plan.

### Step 2A: ask targeted clarifying questions when behavior is ambiguous

Before finalizing a plan, ask the user exact questions when the answer changes the implementation
contract and cannot be safely discovered from code. Good questions are narrow and actionable, for
example:

- Should Cancel close only the dialog or exit the whole workflow?
- Should this rating affect similar manga or only hide this title?
- Should this action run in the background after leaving the screen?
- Should a source be disliked for recommendation behavior, catalogue quality, explicit content, or all
  of those separately?

Record the user's answer in the plan as an implementation decision. Do not ask broad questions such
as "how should this work?" when the code and prior discussion already provide enough context.

### Step 3: map the current behavior

Document the current flow as a sequence:

1. user action;
2. UI event;
3. screen model/action;
4. domain interactor or policy;
5. repository/database/preference operation;
6. network or extension call;
7. result transformation;
8. cache/state update;
9. rendered UI;
10. backup/sync or notification side effects.

For each step, identify thread/dispatcher, lifecycle owner, cancellation behavior, and failure behavior.

### Step 4: define the target behavior

Specify:

- normal case;
- empty case;
- loading case;
- offline case;
- timeout case;
- partial success;
- cancellation;
- process recreation;
- corrupted persistence;
- duplicate/conflicting data;
- unsupported source/device/theme;
- permission or installer failure;
- user retry and recovery path.

State explicitly what must not happen.

### Step 5: design before implementation

Define:

- data structures;
- state machine;
- preference keys and defaults;
- database changes and migration number;
- backup/sync effects;
- API boundaries;
- reusable components;
- files to create;
- files to modify;
- files to delete;
- files that must remain unchanged.

Do not create an abstraction merely because it sounds clean. Explain what duplication or coupling it removes.

## 3. Plan sizes

### Small fix

Use one focused plan when:

- one bug has one clear root cause;
- fewer than roughly five production files are affected;
- no schema, backup, navigation, or cross-feature contract changes;
- behavior can be tested with focused unit/regression tests.

Required sections:

- confirmed root cause;
- exact file and symbol;
- minimal code change;
- regression test;
- verification command;
- documentation/version update.

### Medium feature

Use one master plan plus one detailed phase document when:

- several screens or layers are involved;
- preferences, cache, or persistence change;
- there are multiple error/recovery paths;
- the feature crosses UI, domain, and data layers.

Required sections:

- current architecture;
- data/control flow;
- exact consumers;
- state transitions;
- preference/schema/backup decisions;
- UI interaction states;
- test matrix;
- migration and rollback considerations.

### Large feature

Use a master plan plus separate phase plans when:

- more than one screen or subsystem is involved;
- there is a new background job, reader lifecycle integration, database state, or public-release concern;
- implementation would exceed one safe review context;
- partial implementation could produce misleading or unsafe behavior.

Each phase document must be independently executable and must state:

- prerequisites;
- exact files/symbols;
- work allowed;
- work forbidden;
- phase-specific tests;
- phase gate;
- what later phases depend on it.

The master plan must define ordering, shared contracts, one-final-build behavior, and final verification.
## 3A. Claude model and effort assignment

Every implementation plan and every Claude handoff prompt must declare the recommended Claude Code model and effort level for that specific plan. Do not use a fixed model by habit. The assignment must match the plan's risk, reasoning depth, expected tool use, and the user's Pro usage budget.

The assignment is a planning requirement, not an application feature. Do not expose model names, effort levels, or development-channel terminology in the app UI, release notes, or user-facing strings.

### Decision factors

Classify the plan against reasoning sensitivity, change surface, autonomy horizon, verification burden, token pressure, and rework cost. A low-effort first pass can cost more overall if it creates architectural, lifecycle, migration, or security mistakes that require repair.

### Model roles

Use the actual model names available in Claude Code's `/model` picker:

| Work type | Recommended model | Effort |
| --- | --- | --- |
| Mechanical formatting, localized wording, documentation indexing, small isolated edits | Sonnet | low or medium |
| Normal feature implementation, focused bug fix, Compose/UI work, tests, and ordinary refactoring | Sonnet | high |
| Token-constrained, well-specified implementation with narrow scope and strong tests | Sonnet | medium |
| Database migration, backup/sync, Android lifecycle, installer/service, security/privacy, concurrency, or cross-module contracts | Opus | high or xhigh |
| Full repository audit, upstream reconciliation, release-readiness review, or difficult debugging after Sonnet has struggled | Opus | high or xhigh |
| Large autonomous multi-phase implementation with few check-ins | Fable, if available | high or xhigh |
| Plan-first then ordinary implementation in one workflow | `opusplan` | Opus planning, Sonnet execution |

This is a risk-based assignment, not a claim that one model is universally superior. The live Claude Code model picker is authoritative if names or availability differ.

### Effort rules

- **Low:** short, mechanical, intelligence-insensitive work only. Never default to it for migrations, persistence, security, lifecycle, recommendation ranking, or crash fixes.
- **Medium:** bounded work with a validated plan, low ambiguity, and strong focused tests. This is the throughput setting, not the quality default for this repository.
- **High:** default for normal KMK implementation and the minimum for intelligence-sensitive work.
- **Xhigh:** complex audits, architecture, concurrency, migration compatibility, or multi-stage verification.
- **Max:** session-only, exceptionally difficult debugging or final reasoning; do not assign it automatically because it can overthink and consume the allowance rapidly.

If a selected model does not support the requested effort level, Claude Code may fall back to the highest supported level below it. Record the effort actually used.

### Required plan fields

Every plan must include a **Claude execution assignment** section:

```text
Recommended model: Sonnet | Opus | Fable | opusplan
Recommended effort: low | medium | high | xhigh | max
Why this assignment fits this plan:
Token/throughput tradeoff:
When to escalate to a stronger model or effort:
When to reduce effort safely:
Required verification before continuing:
```

Assign model and effort separately to each phase. Do not assign Opus or Fable to every phase merely because one phase is difficult. Use Sonnet for bounded execution after architecture and contracts are settled.

### Required Claude prompt syntax

Begin the prompt with an actionable assignment, for example:

```text
/model Sonnet
/effort high
```

or:

```text
/model opusplan
```

Repeat the assignment in prose and tell Claude to verify the active model and effort with `/status` or the visible model indicator before editing. Do not switch models repeatedly in one long session without a reason; model switching can invalidate prompt caching and force context to be reread. Prefer phase boundaries or separate sessions when changing roles.

### Evidence and review requirement

The implementation report must record the requested and actual model/effort, any capability fallback, whether a hybrid workflow was used, whether token pressure changed the assignment, any escalation after failures or review findings, and whether the assignment was adequate for the next phase.

Do not call a model "best" without stating the objective: quality, first-pass correctness, throughput, latency, or total work before a usage limit. For this project, default to **Sonnet high** for ordinary implementation, **Opus high/xhigh** for high-risk reasoning, and **Sonnet medium** only when the plan is exceptionally precise and throughput is the primary constraint.

User preference update: when Codex has already performed the audit and written an implementation
plan with exact files, symbols, contracts, tests, and acceptance criteria, prefer **Sonnet low** for
Claude execution unless the plan itself explains why low effort is unsafe. This is a throughput
preference for implementation after planning, not a license to give Claude vague work. If low effort
is selected for a risk-sensitive task, the plan must compensate by being more explicit and by requiring
Claude to stop and report blockers instead of improvising large architectural decisions.

### Research basis

This policy is based on the current official Claude guidance, not an assumption that the heaviest model is always best:

- [Claude Code model configuration](https://code.claude.com/docs/en/model-config): model selection, `opusplan`, effort levels, supported effort behavior, and prompt-caching implications of switching models.
- [Choosing the right Claude model](https://claude.com/resources/tutorials/choosing-the-right-claude-model): Sonnet as the coding/general default, Opus for deep reasoning, and Fable for the largest long-horizon projects.
- [Claude Pro usage limits](https://support.claude.com/en/articles/8325606-what-is-the-pro-plan): session and weekly limits vary with message length, model, and feature use.

These sources describe model roles and tradeoffs, not a controlled benchmark on this repository. Therefore each plan must state its objective and may revise the assignment after verification results.
## 4. Exact file planning

Every production change should identify one of:

- create: new file and responsibility;
- modify: existing file and exact symbols/sections;
- delete: file and proof that all callers are removed;
- retain: existing code that must not be rewritten;
- inspect-only: code used to validate assumptions.

For each changed symbol, describe:

- current responsibility;
- target responsibility;
- inputs and outputs;
- threading/lifecycle ownership;
- error behavior;
- tests.

If an exact path cannot be known before inspection, the plan must require the implementer to discover and record it before editing. â€œFind the relevant fileâ€ is not enough by itself.

## 5. UI plan requirements

â€œPolishâ€ must be translated into concrete behavior.

Define:

- screen section order;
- primary versus secondary actions;
- menu versus visible action placement;
- selection behavior;
- long-press behavior;
- empty/loading/error/completed states;
- phone-width behavior;
- tablet-width behavior;
- theme and accent behavior;
- spacing, wrapping, stable dimensions;
- back/outside-tap behavior;
- accessibility labels and touch targets;
- animations or transitions only where they clarify state changes.

Reuse existing project components and patterns. Do not hardcode colors, typography, or build-channel language. Do not hide an important function behind an undiscoverable gesture.

## 6. State and lifecycle plan requirements

For asynchronous or lifecycle-sensitive features, define a pure state model where possible.

Specify:

- states;
- events;
- valid transitions;
- invalid events;
- cancellation;
- pause/resume;
- process recreation;
- duplicate observer/job prevention;
- persistence restore;
- corrupted-state fallback;
- foreground/background behavior;
- thread/dispatcher.

Never store non-Parcelable/non-Serializable screen objects in Android navigation or Bundles. Avoid work in composable bodies. Make side effects occur from explicit event handlers or lifecycle effects.

## 7. Performance and efficiency requirements

Every plan must distinguish:

- displayed result limit;
- fetched result limit;
- source count;
- query-attempt count;
- enrichment/detail-call count;
- pagination/discovery limit;
- cache size;
- background concurrency.

Changing one limit must not silently multiply the others.

Define:

- bounded loops;
- timeout;
- retry policy;
- cancellation;
- concurrency limit;
- cache invalidation;
- memory/storage impact;
- battery impact;
- offline behavior.

Prefer one shared pure policy over multiple ad hoc copies when behavior must remain consistent.

When repeated failures, repeated loading behavior, source selection, filtering, or visibility rules
appear in several places, first look for an existing shared policy. If one does not exist and the same
rule must be enforced across screens, create one shared policy or executor rather than duplicating
logic inside each screen model.

## 8. Persistence, migration, backup, and sync

For every stored value, state:

- storage location;
- key/table/column;
- default;
- validation;
- migration number if needed;
- backup behavior;
- restore behavior;
- sync merge behavior;
- conflict policy;
- deletion/reset behavior;
- compatibility with old installations and old backups.

Use additive migrations and preserve existing field numbers. Add round-trip tests for backup data. Never assume preferences or database state sync automatically without checking the implementation.

## 9. Security and privacy

Review:

- sensitive data in logs;
- raw exception text shown to users;
- downloaded/content data storage;
- network calls;
- permissions;
- installer/service behavior;
- exported activities or providers;
- backup exposure;
- untrusted extension/source data;
- input validation;
- denial-of-service risks from unbounded work.

Document data minimization and local-only behavior. Avoid adding telemetry unless explicitly approved.

## 10. Exception handling

For every external boundary, define handling for:

- offline;
- timeout;
- HTTP/source failure;
- malformed source data;
- missing extension;
- install/uninstall failure;
- cancellation;
- database failure;
- migration failure;
- corrupted preference;
- missing dependency injection registration;
- lifecycle cancellation;
- duplicate concurrent operation.

User-facing errors must be localized and actionable. Diagnostics may retain safe technical categories, but must not expose sensitive content or raw uncontrolled exception text.

### Structural runtime-boundary rule

For crashes or errors crossing a reusable external boundary, plan the boundary first and the
individual call-site edits second. Examples include extension source execution, network requests,
installer/service calls, backup decoding, tracker calls, database migrations, reader page loading,
and background jobs.

For extension source execution specifically:

- do not rely only on `catch (Exception)`; extension code can throw `LinkageError` such as
  `NoClassDefFoundError` after a source has already loaded successfully;
- do not classify all `Error` as recoverable;
- preserve `CancellationException`;
- preserve fatal VM errors such as `OutOfMemoryError`, `StackOverflowError`, `ThreadDeath`, and
  `AssertionError`;
- unwrap `ExecutionException`, `CompletionException`, and `InvocationTargetException` before
  classification;
- convert recoverable extension/runtime failures into source-scoped unavailable/error states so
  sibling sources and unrelated screens can continue;
- record source identity and operation in diagnostics;
- add tests proving sibling isolation, not merely classifier behavior.

The v0.8.10-fix2/fix3/fix4 Asura/Zstd crash documents the pattern: a narrow recommendation-path
classifier was useful containment, but the durable fix required one shared source-runtime policy used
by Browse, For You, Source Evaluation, global search, reader/download, library update, matching, and
grouped recommendation paths. A call site is not structurally fixed merely because it has a nearby
`catch (Error)` branch; app-module source-method calls should use the shared runtime boundary unless
a documented module boundary makes that impossible. Do not defer a source call as "lower risk" once
real-device evidence shows it participates in the crash family.

## 11. Test plan

Tests must cover:

- pure policies and reducers;
- default and corrupted preferences;
- normal and boundary values;
- cache invalidation;
- database migration;
- backup/restore round trip;
- sync merge/conflict;
- UI state and action routing;
- lifecycle transitions;
- cancellation and concurrency;
- offline and retry;
- empty/error/partial-success states;
- phone/tablet and theme behavior when visual QA is relevant;
- regression tests for the original bug.

State exact test class names or patterns. Record unavailable device tests honestly. Distinguish new failures from pre-existing failures.

## 12. Documentation and handoff

After implementation:

- write an implementation report;
- update current state;
- update next work/deferred items;
- update version history;
- update the encyclopedia/index;
- archive superseded plans without deleting useful history;
- record deviations and known limitations;
- record exact verification commands and results;
- copy the APK to the required handoff directory;
- ensure the APK name matches the actual version metadata.

The final report cannot claim completion if a required phase, test, build, documentation update, or handoff step is missing.

## 13. Claude execution contract

Claude must:

1. read the encyclopedia and all referenced plans;
2. inspect current code before editing;
3. report contradictions between plan and code;
4. implement phases in order;
5. run focused tests after each phase;
6. not produce the final APK early;
7. keep all user-visible strings localized;
8. update implementation documentation while working;
9. run formatting and full verification;
10. provide a final changed-file summary, test results, deviations, and APK path.

If the code disproves a plan assumption, Claude must stop that phase, document the discrepancy, and correct the plan before implementing rather than guessing.




