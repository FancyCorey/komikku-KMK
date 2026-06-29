# KMK Personal Recommendations Documentation Rules

Date: 2026-06-14

Status: required process for all future recommendation work.

## Non-Negotiable Rule

Every implementation session must update or create a markdown file before the task is considered complete.

Do not claim a feature is implemented because it appears in a plan. Verify against code.

## Before Coding

Before any new implementation, read:

- `docs/recommendations/README.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `RECOMMENDATION_IMPLEMENTATION_AUDIT.md`
- `RECOMMENDATION_VERSIONING.md`

Then create or update a focused implementation plan and wait for explicit user approval.

## During Implementation

Keep the scope tied to the approved plan.

If the implementation deviates from the plan, document the deviation and why it happened.

## Required Implementation Note Fields

Every implementation note must include:

- Date
- Feature version/build label if applicable
- User-approved scope
- Goal
- Files changed
- Behavior changed
- Tests run
- APK/build output if applicable
- Known limitations
- Follow-up recommendations
- Deviations from the approved plan

## Versioning

Use the local feature label:

```text
KMK-Recs vMAJOR.MINOR.PATCH
```

Use APK names that include the upstream app version and KMK feature version, for example:

```text
Komikku-v1.13.6-kmk.3.2-debug.apk
Komikku-v1.13.6-kmk.3.2-release.apk
```

Update `RECOMMENDATION_VERSIONING.md` whenever an APK is handed off.
