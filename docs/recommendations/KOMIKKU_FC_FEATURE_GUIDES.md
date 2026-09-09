# Komikku FC Feature Guide

This guide introduces the main Komikku FC additions in plain language. It
describes the current development version, not necessarily the APK in the
latest GitHub release. Final names and behavior may change before release.

## For You

For You brings recommendations from compatible installed sources into one
place. It uses your ratings, selected sources, languages, filters, and saved
preferences to rank results.

Open **Browse > For You** and refresh to look for more manga. Results may take
time because each source responds independently. A problem with one source
should not prevent working sources from returning results.

Use Focus to temporarily narrow the current results by available filters. This
does not change your permanent ratings or source preferences.

## Ratings

You can mark manga as **Loved**, **Liked**, **Disliked**, or **Not interested**.
These choices help shape For You. Not interested also keeps that manga out of
future recommendations.

Ratings can be changed or cleared later. When matching versions are found on
other sources, the app can offer to apply an action to those versions too. You
remain in control of which matches are confirmed.

## Matching Manga Versions

Different sources may carry the same manga under the same or a similar title.
Komikku FC can search installed sources for possible matches and present them
for confirmation.

Exact title matches may be selected automatically according to your settings.
Review the selection before applying it, especially when titles are short or
generic. Confirmed matches can share selected rating and local-tracking
actions; rejected matches should remain separate.

## Best Version

Best Version helps compare available versions of a manga across installed
sources. It can check chapter availability and preview pages without changing
your library immediately.

Choose a candidate only after reviewing its title, source, chapters, and
preview. Keeping the current version makes no change. A migration happens only
after confirmation.

## Local Tracking

Local tracking keeps reading status, chapter progress, score, and dates on the
device without requiring an online tracker account. It is separate from adding
a manga to the Library and separate from external services such as AniList or
MyAnimeList.

When enabled in settings, confirmed versions of the same manga can share local
tracking updates. Ambiguous matches should ask for confirmation instead of
silently combining unrelated manga.

Back up before migration or major updates. A complete backup should preserve
local tracking and the confirmed relationships that make shared behavior work.

## Source Evaluation

Source Evaluation samples the information a source provides and explains how
useful that source may be for recommendations. It can show that a source looks
promising, has limited metadata, returned no useful matches, or encountered a
problem.

An evaluation is guidance, not a guarantee about every manga on that source.
Sources vary in naming, filters, metadata, reliability, and update speed.

## Sources To Try

Sources To Try uses available evaluation information to suggest compatible
sources that are not currently installed. Review the source name, language,
and reason before installing it. Komikku FC does not bundle manga or operate
the external sources it can connect to.

## Reader Timer and Schedule

The reading timer counts active reading time and pauses when you leave the
reader. Reading schedules let you set a future reading window and return to the
same manga afterward.

These tools are optional and should not change chapter progress, ratings, or
tracking unless you perform the related action yourself.

## Settings and Recovery

Recommendation Settings groups controls for ratings, matching, source order,
evaluation, discovery, and diagnostics. Defaults are intended to be useful,
but network, storage, memory, and source behavior differ between devices.

When an operation fails, retry only the items that failed. Successful items
should remain complete. If a source repeatedly fails, open its WebView or check
the source separately before assuming the whole app is offline.

## Privacy and Limitations

- Ratings, local tracking, and confirmed version relationships are personal
  data and should be included only in backups you choose to create.
- External trackers have their own accounts, privacy policies, and matching
  behavior.
- Search and recommendations depend on installed source extensions and network
  responses that Komikku FC does not control.
- A source can change or stop working without an app update.

For release-specific compatibility, known limitations, checksums, and update
instructions, always use the notes attached to the APK you download.

## Contributor References

Implementation plans, test evidence, and architecture records are maintained
separately from this user guide. Contributors should start with
[the recommendation documentation index](./README.md) and verify current code
before treating a historical report as present behavior.
