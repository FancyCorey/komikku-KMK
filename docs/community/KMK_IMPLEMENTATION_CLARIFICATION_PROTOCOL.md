# KMK Implementation Clarification Protocol

**Status:** Required planning procedure for future KMK-Recs work.

## Purpose

Implementation plans should not force the user or Claude to guess about UI intent, device behavior,
or the expected result of a change. Before planning a fix, collect the smallest set of direct
answers and evidence needed to make the implementation executable.

## Dependency Rule

If a crash or blocker prevents the user from observing a later screen, fix or isolate that blocker
first. Do not ask the user to describe UI behavior they cannot currently access. For example, the
missing `UpdateMangaFromRemote` registration must be resolved and verified before asking for detailed
For You, rated-collection, or other screen comparisons.

## Required Questions By Work Type

### Crash or fatal error

Ask for or collect:

- exact user action;
- route/screen open at failure;
- full crash log, not only the visible error title;
- app/upstream/KMK version and APK filename;
- fresh install versus upgrade;
- whether restart, cache clear, or process recreation changes it;
- device, Android version, orientation, and font scale.

Then identify the first meaningful application frame, missing dependency/resource/state, and exact
source symbol before proposing a fix.

### UI alignment or visual change

Ask for:

- official Komikku reference screen or component;
- current KMK screen screenshot;
- device size and orientation;
- light/dark theme and font scale;
- exact elements that must match;
- whether behavior, layout, wording, or only styling should change;
- whether the change applies to one screen or every KMK surface using the same pattern.

### Search, recommendation, or evaluation behavior

Ask for:

- exact input/query or rating/group seed;
- expected results;
- actual results;
- source count and installed/uninstalled state;
- filters, language, visibility, and hide-known settings;
- whether the problem occurs on first load, refresh, expansion, or background resume;
- timing and relevant logcat/network errors.

### Data, migration, backup, or sync behavior

Ask for:

- previous and current APK versions;
- upgrade, reinstall, restore, or sync path;
- representative data that disappeared or changed;
- backup availability;
- whether the issue affects ratings, groups, library, preferences, OCR, timer/schedule, or source
  state;
- whether the issue reproduces on a clean database.

### Performance or loading behavior

Ask for:

- exact action and screen;
- approximate duration;
- number of sources, linked manga, pages, or results;
- first load versus refresh versus expansion;
- whether leaving the screen cancels work;
- visible progress, repeated rows, or error messages;
- available logs or request diagnostics.

## Question Format

Questions must be concrete and answerable. Prefer:

```text
When you open [exact screen] from [exact route] on [device/orientation], does [specific control]
show [expected state], or does it show [actual state]? Is the intended change [behavior], [layout],
[wording], or all three?
```

Avoid broad prompts such as “How should the UI be improved?” when a screenshot, reference component,
or exact state can be requested instead.

## Planning Gate

Before creating the implementation plan, record:

1. What is directly verified from code or logs.
2. What the user directly observed.
3. What remains inaccessible because of a blocker.
4. Which blocker must be fixed first.
5. Which questions still require the user’s answer.

Do not begin implementation of an inaccessible UI change until the blocker is fixed or the user has
provided sufficient visual evidence. Do not make code changes merely to obtain screenshots unless
that change is the separately approved blocker fix.
