# KMK-Recs v0.4.2 Polish And What's New Implementation Plan

Date: 2026-06-14

Status: implementation plan only. Do not implement until the user explicitly approves and requests coding.

Target feature version: `KMK-Recs v0.4.2`

Expected APK naming after implementation:

```text
Komikku-v1.13.6-kmk.4.2-debug.apk
Komikku-v1.13.6-kmk.4.2-release.apk
```

## Purpose

KMK-Recs v0.4.1 is working well, but the current code review found a few areas where the recommendation system can be made more correct, more maintainable, and more native-feeling without changing its overall shape.

This pass should be a focused polish/reliability release:

- make the "Hide known manga" filter reliable even when recommendation results come from cache,
- refine Top Picks duplicate merging so it truly means exact title plus exact author OR exact artist,
- add a local Komikku-style "What's New" flow for KMK-Recs changes,
- update and organize recommendation documentation so future Codex/Claude passes know what has shipped.

Do not add network-heavy features in this pass. Do not add tracker/AniList per-result lookups. Do not fetch chapter lists for recommendation filtering.

## Current Baseline

Current documented feature version:

```text
KMK-Recs v0.4.1
```

Current implemented behavior:

- Browse > For You searches up to 20 priority sources.
- Top three priority sources show up to 20 results; the rest show up to 10.
- Source rows display in priority order.
- Empty source rows are hidden.
- A synthetic Top Picks row appears first and is derived from already-fetched source rows.
- Top Picks uses the personal scorer, source occurrence bonus, boosted-source bonus, and matched-group tiebreaking.
- "Hide known manga" hides rated, favorite/library, started, read, and history-known manga using local DB data only.
- New quality filters fail open.

Important implemented files:

- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/CombinedPicksAccumulator.kt`
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt`
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt`
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt`
- `data/src/main/sqldelight/tachiyomi/data/mangas.sq`
- `domain/src/main/java/tachiyomi/domain/taste/interactor/GetKnownRecommendationMangaIds.kt`
- `app/src/test/java/exh/recs/CombinedPicksAccumulatorTest.kt`

## Findings From This Review

### 1. Hide-known cache correctness gap

In `BrowsePersonalRecommendationsScreenModel.load()`, the profile fingerprint is currently computed before `hideKnownManga` is read:

```kotlin
val fingerprint = profileFingerprint(topTags, profile, aliasMap, disabledSourceIds, storedOrder, recommendationLanguages)
...
val hideKnownManga = sourcePreferences.recommendationHideKnownManga().get()
```

`profileFingerprint()` currently includes languages, profile data, aliases, disabled sources, and source order, but not `hideKnownManga`.

`loadFromCache()` re-filters favorite/rated manga:

```kotlin
if (manga.favorite || shouldHideForYou(manga, tasteByKey, visibility)) return@mapIndexedNotNull null
```

However, cached results do not currently re-apply the known/read/history filter. This means:

- toggling "Hide known manga" can reuse an incompatible cache entry,
- manga read after a cache entry was saved can remain visible until the cache expires,
- the live search path is stricter than the cached path.

This is a correctness issue, not a redesign issue.

### 2. Top Picks duplicate merge is close but not exact to the intended rule

`CombinedPicksAccumulator.conservativeWorkKey()` currently chooses one contributor:

```kotlin
val contributor = if (author.isNotBlank()) author else artist
return "t:$title\u0000c:$contributor"
```

The intended rule is:

```text
same normalized title AND same nonblank author
OR
same normalized title AND same nonblank artist
```

Current behavior handles:

- same title + same author,
- same title + artist only when author is blank.

But it can miss a valid duplicate when both entries have the same artist and different nonblank authors, because author wins and artist is ignored.

There is also a literal NUL separator in the key. It works, but it is harder to inspect and document. A visible delimiter or typed key is more maintainable.

### 3. Existing Komikku "What's New" flow can be mirrored locally

Komikku already has:

- `WhatsNewDialog`
- `WhatsNewScreen`
- About > What's new
- launch-time update dialog logic in `MainActivity`

Relevant files:

- `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`
- `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/WhatsNewDialog.kt`
- `app/src/main/java/eu/kanade/presentation/more/WhatsNewScreen.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/more/WhatsNewScreen.kt`
- `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/AboutScreen.kt`

The existing wrapper `eu.kanade.tachiyomi.ui.more.WhatsNewScreen` requires a remote `releaseLink`, so KMK local release notes should either:

- add a local sibling screen/dialog for KMK-Recs notes, or
- make the shared presentation component support an optional browser action cleanly.

Do not replace upstream Komikku release notes. KMK notes must be additive and isolated.

### 4. Documentation index is stale

Several docs still describe completed plans as awaiting approval, and some diagnostic text still says "Combined Picks" even though the user-facing feature is now "Top Picks."

This does not affect runtime behavior, but it does affect handoff quality. The next implementation pass should update docs in the same session as code.

## Approved Scope For v0.4.2

### Phase 1: Cache correctness for Hide Known Manga

Goal: make cached recommendation rows obey the same known/read filtering as fresh source searches.

Implementation steps:

1. In `BrowsePersonalRecommendationsScreenModel.load()`, read `hideKnownManga` before computing the profile fingerprint.

2. Add `hideKnownManga: Boolean` to `profileFingerprint(...)`.

3. Include the setting in the digest:

```kotlin
update("hideKnown:$hideKnownManga")
```

4. Update all call sites of `profileFingerprint(...)`.

5. Update `loadFromCache(...)` to accept:

```kotlin
hideKnownManga: Boolean
```

6. Inside `loadFromCache(...)`, after loading manga IDs from cache and resolving them through `getMangaInteractor.await(id)`, re-apply the known-manga filter when `hideKnownManga` is true.

Recommended implementation shape:

```kotlin
val resolved = ids.mapIndexedNotNull { index, id ->
    val manga = getMangaInteractor.await(id) ?: return@mapIndexedNotNull null
    if (manga.favorite || shouldHideForYou(manga, tasteByKey, visibility)) return@mapIndexedNotNull null
    IndexedRecommendation(manga, scores.getOrElse(index) { 0.0 })
}

val knownIds = if (hideKnownManga && resolved.isNotEmpty()) {
    runCatching {
        getKnownMangaIds.await(resolved.map { it.manga.id })
    }.getOrElse { error ->
        logcat(LogPriority.WARN, error) { "Top Picks cached known-manga filter failed, keeping cached candidates" }
        emptySet()
    }
} else {
    emptySet()
}
```

Claude does not need to introduce `IndexedRecommendation` as a public type; a local private data class or local list transformation is fine.

7. Keep the existing fail-open behavior. If the known filter query fails on cached results, keep cached candidates and log a warning.

8. Do not change the cache table schema.

9. Do not fetch network metadata or chapter lists.

10. Decide whether to bump the cache key from `personal_v3` to `personal_v4`.

Recommendation: do not bump the key unless necessary. Including `hideKnownManga` in the fingerprint will naturally invalidate old incompatible entries once. If Claude sees a test or migration reason to bump, document it clearly.

Acceptance criteria:

- Turning "Hide known manga" on/off changes cache compatibility.
- Cached rows do not show locally known/read/rated/favorited manga when the setting is enabled.
- If the known filter lookup fails, the cache path fails open instead of blanking the row.
- Existing fresh-search behavior remains unchanged.

Tests:

- Add or update unit tests around fingerprint/cache behavior if there is an existing testable seam.
- If `BrowsePersonalRecommendationsScreenModel` is not currently practical to unit test, document this and rely on focused manual verification plus full unit tests.

Manual verification:

- Rate a manga or read/start a chapter.
- Refresh For You once.
- Leave and return before cache TTL expires.
- Confirm the known manga remains hidden when "Hide known manga" is enabled.
- Disable "Hide known manga" and confirm cached compatibility changes.

### Phase 2: Refine Top Picks duplicate identity

Goal: make conservative duplicate merging match the intended exact rule without title-only false positives.

Implementation steps:

1. In `CombinedPicksAccumulator`, replace the single-key helper with a multi-key helper:

```kotlin
internal fun conservativeWorkKeys(manga: Manga): Set<String>
```

2. Generate independent keys for nonblank author and nonblank artist:

```text
title:<normalized title>|author:<normalized author>
title:<normalized title>|artist:<normalized artist>
```

3. Return an empty set when:

- normalized title is blank,
- both normalized author and normalized artist are blank.

4. In `add(...)`, compute all work keys for the candidate.

5. Resolve the candidate to an existing bucket if any of its work keys already maps to a primary bucket key.

6. If multiple keys map to different existing buckets, choose one deterministic canonical bucket and merge into it. Recommended simplest rule:

- choose the first mapped bucket in insertion order,
- then re-point all candidate keys to that canonical bucket.

This conflict should be rare, but deterministic behavior prevents unpredictable Top Picks ordering.

7. When creating a new bucket, register all work keys to the new raw key.

8. When merging into an existing bucket, register all candidate work keys to the effective bucket key.

9. Replace the literal NUL separator with a visible delimiter or a tiny internal typed key object. Keep it simple.

10. Keep current merge safety:

- malformed metadata must not crash,
- incomplete metadata keeps entries separate,
- title-only matches do not merge,
- different authors and different artists do not merge.

Tests to update/add in `CombinedPicksAccumulatorTest`:

- `conservativeWorkKeys returns empty when title is blank`
- `conservativeWorkKeys returns empty when both author and artist are blank`
- `conservativeWorkKeys returns author and artist keys when both are present`
- same title + same author + different artist merges
- same title + same artist + different author merges
- same title + different author + different artist does not merge
- same title + blank metadata does not merge
- similar but not exact normalized title does not merge
- merged candidate keeps best score and richer representative
- `clear()` clears bucket and work-key maps

Acceptance criteria:

- Top Picks still avoids risky title-only dedupe.
- Duplicates with exact matching artist but differing author metadata can merge.
- Source rows remain independently deduped by their existing row logic.
- The accumulator remains pure and unit-testable.

### Phase 3: Local KMK-Recs What's New

Goal: show KMK-Recs changes in a native-feeling way when the user installs a new KMK feature APK, without interfering with upstream Komikku update notes.

Implementation principles:

- Keep upstream Komikku release notes untouched.
- Use a separate KMK app-state preference.
- Store KMK release notes locally in code.
- Show once per KMK feature version.
- Allow manual access later from About if practical.
- Avoid stacking two dialogs at the same time.

Recommended files:

- New file: `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
- New screen or wrapper if needed: `app/src/main/java/eu/kanade/tachiyomi/ui/more/KmkRecsWhatsNewScreen.kt`
- Possibly new presentation dialog: `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/KmkRecsWhatsNewDialog.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`
- Modify: `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/AboutScreen.kt`
- Modify i18n as needed: `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

Recommended release-note object:

```kotlin
object KmkRecsReleaseNotes {
    const val VERSION_CODE = 402
    const val VERSION_NAME = "KMK-Recs v0.4.2"

    val MARKDOWN = """
        ## KMK-Recs v0.4.2

        - Improved Top Picks duplicate matching.
        - Made Hide known manga consistent with cached recommendations.
        - Added local KMK-Recs What's New notes.
        - Updated recommendation documentation for future handoffs.
    """.trimIndent()
}
```

Use an integer `VERSION_CODE` instead of parsing semantic strings.

Preference key:

```kotlin
Preference.appStateKey("kmk_recs_last_seen_version_code")
```

Launch behavior in `MainActivity`:

1. Keep the existing upstream `showChangelog` logic.

2. Add KMK logic after upstream variables:

```kotlin
val kmkLastSeenVersion = Injekt.get<PreferenceStore>().getInt(
    Preference.appStateKey("kmk_recs_last_seen_version_code"),
    0,
)
var showKmkChangelog by remember {
    mutableStateOf(KmkRecsReleaseNotes.VERSION_CODE > kmkLastSeenVersion.get())
}
```

3. Do not show both dialogs at once.

Recommended flow:

```kotlin
if (showChangelog) {
    // existing upstream dialog
} else if (showKmkChangelog) {
    // KMK dialog
}
```

4. Mark the KMK version as seen only when the KMK dialog is dismissed or when the user opens the KMK notes screen.

5. If upstream release notes are shown first, KMK notes can appear after upstream dialog dismissal or on the next launch. Prefer the cleanest implementation that avoids dialog stacking and unexpected navigation.

Screen behavior:

- Reuse the existing presentation `WhatsNewScreen` if Claude can cleanly support a local/no-link mode.
- If making `onOpenInBrowser` optional is too invasive, create a small KMK-specific Voyager screen that uses the same scaffold/markdown rendering style.
- Do not show a browser/open-release button for local KMK notes unless a meaningful local or repo URL exists.

About behavior:

- Add an About entry for local KMK notes if it can be done cleanly.
- Suggested label: `KMK-Recs What's new`.
- This should open the local KMK notes screen immediately, no network spinner.
- Keep existing upstream `What's new` entry unchanged.

String resources:

Add KMK strings in `i18n-kmk` rather than changing upstream MR strings where practical:

- `kmk_recs_whats_new`
- `kmk_recs_updated`
- optional subtitle if needed.

Acceptance criteria:

- Fresh install/update to a newer KMK-Recs feature version shows KMK release notes once.
- Reopening the app does not show KMK release notes again after dismissal.
- Existing upstream Komikku release notes still work.
- About > What's new remains upstream Komikku notes.
- About can optionally open local KMK-Recs notes separately.

Tests:

- If there are existing preference/state tests, add a small test around version-code comparison.
- Otherwise document manual verification.

Manual verification:

- Install build with `kmk_recs_last_seen_version_code` unset or lower than 402.
- Launch app and confirm KMK dialog appears.
- Dismiss and relaunch, confirm it does not reappear.
- If upstream changelog is also due, confirm dialogs do not overlap.
- Confirm About screen can still open upstream notes.
- Confirm optional KMK notes entry opens local markdown.

### Phase 4: Documentation cleanup and version update

Goal: ensure Codex, Claude, and the user can easily tell what is implemented, what is deferred, and what belongs to the next pass.

Files to update:

- `docs/recommendations/README.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/SOURCE_LIST_DIAGNOSIS.md`
- `RECOMMENDATION_VERSIONING.md`

Required updates:

1. In `README.md`, move implemented plans out of "Active Planning Files" and into an "Implemented Reports" or "Historical Plans" section.

2. Mark these as implemented:

- `FOR_YOU_SOURCE_ORDER_AND_COMBINED_ROW_PLAN.md`
- `FOR_YOU_SOURCE_ORDER_AND_COMBINED_ROW_IMPLEMENTATION.md`
- `TOP_PICKS_FILTERING_AND_EXCEPTION_HANDLING_PLAN.md`
- `TOP_PICKS_FILTERING_AND_EXCEPTION_HANDLING_IMPLEMENTATION.md`

3. Add this v0.4.2 plan as the current active plan until implemented.

4. In `CURRENT_STATE.md`, update status after implementation to v0.4.2 and describe:

- cache-known filtering now applies on cache reads,
- Top Picks duplicate keys use exact title + exact author OR exact title + exact artist,
- local KMK What's New exists.

5. In `SOURCE_LIST_DIAGNOSIS.md`, replace stale "Combined Picks" wording with "Top Picks" while preserving historical context where useful.

6. In `NEXT_WORK.md`, remove items completed by v0.4.2 from future work.

7. In `RECOMMENDATION_VERSIONING.md`, add a new `KMK-Recs v0.4.2` section with:

- exact behavior changes,
- files changed,
- tests run,
- APK filename if built.

8. If an implementation report is created, name it:

```text
docs/recommendations/KMK_RECS_V0_4_2_POLISH_AND_WHATS_NEW_IMPLEMENTATION.md
```

Implementation report must include:

- pre-implementation verification,
- files changed,
- behavior changed,
- tests run,
- known limitations,
- deviations from this plan.

## Explicitly Deferred

Do not implement these in v0.4.2 unless the user separately approves them:

- AniList/tracker known-list cache.
- Minimum chapter count filtering.
- Query-time blocked-tag exclusion.
- Pull-to-refresh.
- Source quality learning.
- Top Picks drill-down screen.
- Local Source as a recommendation source.
- Any per-result tracker lookup.
- Any chapter-list fetching purely for recommendation filters.

## Risk Notes

### Cache path risk

The known-manga filter queries local DB state. It must remain fail-open so a DB hiccup does not blank recommendation rows.

### Duplicate merge risk

Be conservative. False negatives are acceptable; false positives are worse. If metadata is weak, keep entries separate.

### What's New risk

Avoid breaking upstream update behavior. KMK release notes must be isolated with their own preference key and should not reuse upstream remote fetch paths.

### Documentation risk

Do not delete historical markdown files unless the user explicitly asks. Organize and mark status clearly instead.

## Required Validation

Run at minimum:

```text
./gradlew :app:testDebugUnitTest
```

If time allows, also run:

```text
./gradlew :app:assembleDebug
```

If building a handoff APK, name it according to:

```text
Komikku-v1.13.6-kmk.4.2-debug.apk
```

Document every command result in the implementation report.

## Handoff Summary For Claude

Implement KMK-Recs v0.4.2 as a focused correctness/polish pass:

- include `hideKnownManga` in the recommendation cache fingerprint,
- re-apply known/read filtering when loading cached recommendation rows,
- refine Top Picks duplicate merging to support exact title + author OR exact title + artist,
- add local KMK-Recs release notes using a separate version-code preference and native-style launch/About access,
- update recommendation docs and versioning after implementation.

Keep everything local, bounded, fail-open, and aligned with existing Komikku/KMK patterns.
