# KMK-Recs v0.8.2-v0.8.5 Master Implementation Plan

Status: **implemented and shipped in KMK-Recs v0.8.5** (2026-07-15). All four phases complete,
verified, and built. See `docs/recommendations/KMK_RECS_V0_8_2_TO_V0_8_5_FOR_YOU_UI_AND_READING_TIMER_IMPLEMENTATION.md`
for the full implementation report (files changed, tests, deviations, known limitations).

## Authoritative execution order

Claude must use these plans in order:

1. KMK_RECS_V0_8_2_FOR_YOU_DISPLAY_IMPLEMENTATION_PLAN.md
2. KMK_RECS_V0_8_3_RECOMMENDATION_UI_REFINEMENT_IMPLEMENTATION_PLAN.md
3. KMK_RECS_V0_8_4_READING_TIMER_IMPLEMENTATION_PLAN.md
4. KMK_RECS_V0_8_5_READING_SCHEDULE_IMPLEMENTATION_PLAN.md

The plans describe one coordinated body of work. Internal milestones remain v0.8.2, v0.8.3, v0.8.4, and v0.8.5. Do not create four final APK handoffs. Compile and run focused tests after each phase, but build and copy the final APK only after all applicable phases and documentation are complete.

## Non-negotiable boundaries

- The display setting changes ordinary For You row visibility only.
- UI refinement changes presentation and interaction organization only.
- The timer counts active foreground reader time only.
- The schedule is an optional reader-only wall-clock policy.
- Recommendation scoring, source evaluation math, source-selection rules, database semantics, and existing rating/group semantics must not be changed by the UI work.
- The timer must not use a permanent service, network calls, or sensitive logging.
- The schedule must not claim to restrict other applications.
- All work stays under the 0.8.x version line.
- No development-channel language appears inside the app.
- The final APK is copied to C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.8.5-debug.apk only after all work is complete.

## Phase gate A

Before moving from display-count work to UI work, verify:

- preference fallback and cache invalidation tests pass;
- ordinary source rows obey the selected count;
- Top Picks and source-attempt limits remain unchanged;
- no unbounded pagination or enrichment multiplication was introduced;
- KMR and backup/sync decisions are documented.

## Phase gate B

Before moving from UI work to timer work, verify:

- phone and tablet layouts have been inspected;
- light/dark and accent themes have been inspected;
- Loved/Liked/Disliked share one collection implementation;
- all existing action paths remain discoverable;
- no scoring/query/database behavior changed;
- formatting and focused Compose/unit tests pass.

## Phase gate C

Before implementing the schedule, verify:

- timer state-machine tests pass;
- ReaderActivity lifecycle pause/resume is correct;
- rotation/process recreation does not duplicate or reset active timing unexpectedly;
- chapter-grace and one-extra-chapter behavior are bounded;
- no destructive interruption or sensitive logging exists;
- manual reader QA is recorded.

## Final verification

Use the repository's JDK 17 setup and run spotless, focused recommendation/source-evaluation/rated/reader tests, the complete unit suite, and assembleDebug. Record exact commands and failures. Write the combined implementation report:

docs/recommendations/KMK_RECS_V0_8_2_TO_V0_8_5_FOR_YOU_UI_AND_READING_TIMER_IMPLEMENTATION.md

It must list every changed file, preference, migration, KMR string, test, manual QA result, deviation, and known limitation.



