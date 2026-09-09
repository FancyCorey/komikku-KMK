# KMK Personal Recommendations Documentation Rules

Date: 2026-06-14

Status: required process for all future recommendation work.

## Non-Negotiable Rule

Every implementation session must update or create a markdown file before the task is considered complete.

Do not claim a feature is implemented because it appears in a plan. Verify against code.

## Before Coding

Before any new implementation, read:

- `docs/IMPLEMENTATION_PLAN_STANDARD.md`
- `docs/recommendations/README.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `RECOMMENDATION_IMPLEMENTATION_AUDIT.md`
- `RECOMMENDATION_VERSIONING.md`

Then create or update a focused implementation plan and wait for explicit user approval.

The focused plan must follow `docs/IMPLEMENTATION_PLAN_STANDARD.md`. In particular:

- inspect code before writing the solution;
- record current behavior and target behavior;
- name exact files, symbols, policies, tests, and non-goals;
- ask narrow clarification questions when the user's answer changes the implementation contract;
- prefer shared policies/runtime boundaries over repeated local fixes when several screens fail for
  the same reason;
- include a Claude model/effort assignment and APK handoff naming when applicable.

## During Implementation

Keep the scope tied to the approved plan.

If the implementation deviates from the plan, document the deviation and why it happened.

If a local fix reveals a broader structural issue, stop and document that broader issue instead of
silently adding more local patches. The implementation note must distinguish:

- containment applied now;
- structural work still required;
- tests that prove containment;
- tests or device checks still needed to prove the structural fix.

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
- Requested and actual Claude model/effort when Claude Code was used
- Any user clarifications that changed the implementation contract
- Any structural boundary or shared policy introduced or intentionally deferred

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

