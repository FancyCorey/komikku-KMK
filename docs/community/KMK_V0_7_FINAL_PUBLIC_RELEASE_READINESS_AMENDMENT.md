# KMK-Recs v0.7 Final Public Release Readiness Amendment

Date: 2026-07-12  
Applies to: `docs/community/KMK_V0_7_FINAL_PUBLIC_RELEASE_READINESS_PLAN.md`  
Status: planning amendment; no app code has been changed

## Purpose

This amendment clarifies that the final v0.7 pass is not only a public-readiness pass. It is the final v0.7 closure pass.

Claude must treat the original public-readiness plan and this amendment as one combined implementation plan.

The final output should include:

- a final private/update-style APK for the user's personal install line;
- a final public side-by-side APK for community testing;
- all approved final v0.7 features and polish items completed before the release-readiness checks;
- no broad new v0.8-style experiments.

## Amendment 1: Final Feature Window Before v0.7 Freeze

The original plan says not to add unrelated features. That remains true.

However, a small final feature/polish window is approved before public release hardening begins. Claude must complete these items before producing final builds. After these are complete, v0.7 feature work is frozen.

### Final Feature A: Rated Manga Grouping Default

The Rated Manga collection screens should default to grouped/duplicate-collapsed display where that behavior makes sense.

Context:

- The user wants grouped versions in Loved, Liked, and Disliked manga to be the default/base experience.
- Same-manga match preselection is already default-on and is a different setting.
- Earlier audit observed that `LovedMangaScreenModel.load(...)` may initialize `groupDuplicates = false`; Claude must verify current code before changing it.

Required behavior:

- Loved/Liked/Disliked rated manga views default to grouped duplicate display.
- The user can still toggle grouping off to see a flat list.
- Grouping must use the existing conservative same-manga/link-group logic, not title-only matching.
- Similar or identical titles that are not confirmed as the same manga must not be over-merged.

Required tests:

- default state is grouped;
- toggle switches to flat;
- confirmed cross-source linked entries collapse;
- unrelated same-title/different-work entries do not collapse.

### Final Feature B: Top Picks Contribution Visibility

If `SourceFitStats.topPicksContributionCount` is still tracked but not surfaced, expose it in a compact, non-noisy way.

Context:

- The code tracks how often each source contributes to final Top Picks.
- This can help users understand which sources are genuinely useful.

Required behavior:

- Show contribution count in the source evaluation/settings area where it helps source decisions.
- Keep phone UI compact.
- Do not add large diagnostic blocks to rows.
- If the count is obsolete or already visible, document why no change is required.

Required tests:

- `SourceFitStats` serialization/parsing preserves the count.
- Any pure display formatter handles zero and nonzero counts safely.

### Final Feature C: Current Approved Closure Items Only

Claude must scan active documentation before implementation:

- `docs/recommendations/KMK_RECS_V0_7_CLOSURE_AUDIT.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/CURRENT_STATE.md`

Only include additional closure items if they meet all of these conditions:

- already discussed or approved by the user;
- small enough for v0.7 finalization;
- directly improves public readiness, safety, clarity, or polish;
- does not introduce a new subsystem;
- does not belong naturally to v0.8.

If uncertain, defer to v0.8 and document the deferral.

## Amendment 2: Final Private And Public Builds

The original plan focuses on public release readiness. The final implementation must produce both private and public artifacts.

### Private Build

Purpose:

- update-style build for the user's existing/personal install line.

Requirements:

- must follow the existing private APK naming convention;
- must not use the public package ID;
- should update/replace the user's personal Komikku/KMK line as previous private builds did;
- must include the same final v0.7 feature/security/readiness fixes as the public build unless a package-identity-only difference requires otherwise.

Recommended filename after this pass:

```text
Komikku-v1.13.6-kmk.7.45-debug.apk
```

If Claude increments beyond v0.7.45, use the actual final KMK version.

Recommended output folder:

```text
C:\Users\USER\Downloads\Komikku\private\
```

### Public Build

Purpose:

- community/public test build that installs beside official Komikku and beside the private/personal KMK line.

Requirements:

- must use `kmkPublicTest`;
- package ID must be `app.komikku.kmk`;
- launcher label should remain clearly distinct, e.g. `Komikku KMK`;
- must not overwrite official Komikku;
- must not overwrite the private/personal KMK build;
- must clearly document that it has separate app data and requires backup/restore to transfer library/settings.

Recommended filename after this pass:

```text
Komikku-KMK-PublicTest-v1.13.6-kmk.7.45-debug.apk
```

If Claude increments beyond v0.7.45, use the actual final KMK version.

Recommended output folder:

```text
C:\Users\USER\Downloads\Komikku\public\
```

## Amendment 3: Build Timing

Claude must not produce or copy either final APK until:

- final approved v0.7 features are complete;
- public-readiness hardening is complete;
- docs are reconciled;
- security/privacy contradictions are resolved;
- OCR inclusion/exclusion is clarified;
- `spotlessCheck` passes;
- `:app:testDebugUnitTest` passes;
- `:app:assembleKmkPublicTest` passes;
- private/debug build task needed for the private artifact passes;
- implementation report is written.

The final APKs should be the last output of the process, not an intermediate checkpoint.

## Amendment 4: Documentation Output

Claude must update the implementation report required by the original plan to include:

- final private APK path/name;
- final public APK path/name;
- whether both artifacts were built from the same final source state;
- package IDs for both artifacts;
- whether OCR is included in each artifact;
- any known difference between private and public builds.

The implementation report should be:

```text
docs/community/KMK_V0_7_FINAL_PUBLIC_RELEASE_READINESS_IMPLEMENTATION.md
```

## Combined Interpretation

Claude must read:

1. `docs/community/KMK_V0_7_FINAL_PUBLIC_RELEASE_READINESS_PLAN.md`
2. `docs/community/KMK_V0_7_FINAL_PUBLIC_RELEASE_READINESS_AMENDMENT.md`

Together, they define the final v0.7 closure work.

The original plan defines the public-readiness/security/quality bar.

This amendment adds:

- final small feature inclusion before freeze;
- final private APK output;
- final public APK output;
- stricter build timing.

Do not treat this amendment as permission to add unrelated features.

