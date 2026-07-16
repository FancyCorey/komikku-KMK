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


