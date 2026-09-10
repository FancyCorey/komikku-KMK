package mihon.core.migration.migrations

import eu.kanade.domain.source.service.SourcePreferences
import exh.recs.SeenRecommendationMangaStore
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.SetMangaTaste
import tachiyomi.domain.taste.model.MangaRating

// KMK v0.8.21-fix2 -->
/**
 * AUG-02 redesign: migrates the legacy `seenRecommendationMangaKeys` preference (Not Interested,
 * previously stored separately from [tachiyomi.domain.taste.model.MangaTaste]) into a real fourth
 * [MangaRating.NOT_INTERESTED] rating row, so a manga has at most one rating-family state and
 * "clear rating" always goes through the same owner regardless of which of the four states it was.
 *
 * This implements the frozen recommendation-preference migration contract.
 *
 * ## Scope: additive backfill only, no legacy-store cutover risk
 *
 * KMK v0.8.21-fix3: R1 correction -- no live code path writes new entries into the legacy
 * `seenRecommendationMangaKeys` preference anymore (the dual-write design this doc originally
 * described was replaced by a full cutover: `MangaScreenModel.markSeen()`/`clearSeen()` were
 * deleted, and every Not-Interested write across the app now goes through
 * `setMangaTaste`/`clearMangaTaste`/`setMangaTasteBatch` like any other rating, with `MangaTaste`
 * as the single source of truth end to end). This migration itself is unaffected by that cutover
 * -- it only ever *reads* the preference (to backfill pre-existing legacy data) and never writes
 * to it, so it remains exactly as idempotent as before. It backfills `MangaTaste` rows for manga
 * the preference already marks Not Interested, so every *read* path (isNotInterested display,
 * Rated Manga grouping, For You exclusion) sees them without waiting for the user to re-trigger
 * the action.
 *
 * ## Conflict rule (contract item 2)
 *
 * A [SeenMangaKey][exh.recs.SeenMangaKey] migrates into a `NOT_INTERESTED` row only when no
 * `MangaTaste` row already exists for that manga. An existing explicit rating (Love/Like/Dislike)
 * always wins and is never overwritten -- this mirrors the pre-existing one-directional
 * `MangaScreenModel.setMangaTaste()` transition (clearing Not Interested when a rating is chosen),
 * applied symmetrically here rather than invented fresh.
 *
 * ## Idempotence (contract items 1 and 9)
 *
 * The preference is never modified, so re-running this migration is naturally idempotent: any key
 * already migrated finds an existing `MangaTaste` row and is skipped via the conflict rule above.
 * A key whose manga cannot be resolved locally is simply skipped for this run and re-checked the
 * next time the migration is invoked. Version 92 follows the version-90 development builds that
 * introduced this migration body and the version-91 recovery attempt. Version 91 proved that the
 * migration could be selected while silently returning early on a missing production dependency;
 * required owners now fail the migration chain instead of consuming the global version marker.
 */
class NotInterestedRatingMigration(
    private val sourcePreferencesOverride: SourcePreferences? = null,
    private val getMangaOverride: GetManga? = null,
    private val getMangaTasteOverride: GetMangaTaste? = null,
    private val setMangaTasteOverride: SetMangaTaste? = null,
) : Migration {
    override val version: Float = 92f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val sourcePreferences = sourcePreferencesOverride
            ?: checkNotNull(migrationContext.get<SourcePreferences>()) { "Not Interested migration requires SourcePreferences" }
        val getManga = getMangaOverride
            ?: checkNotNull(migrationContext.get<GetManga>()) { "Not Interested migration requires GetManga" }
        val getMangaTaste = getMangaTasteOverride
            ?: checkNotNull(migrationContext.get<GetMangaTaste>()) { "Not Interested migration requires GetMangaTaste" }
        val setMangaTaste = setMangaTasteOverride
            ?: checkNotNull(migrationContext.get<SetMangaTaste>()) { "Not Interested migration requires SetMangaTaste" }

        val pref = sourcePreferences.seenRecommendationMangaKeys()
        val raw = pref.get()
        if (raw.isBlank()) return@withIOContext false

        val keys = SeenRecommendationMangaStore.parse(raw)
        if (keys.isEmpty()) return@withIOContext false

        var migratedCount = 0
        var unresolvedCount = 0
        var conflictCount = 0
        for (key in keys) {
            val manga = getManga.await(key.url, key.sourceId)
            if (manga == null) {
                unresolvedCount++
                continue
            }
            val existing = getMangaTaste.await(manga.source, manga.url)
            if (existing != null) {
                // Rule 2: an explicit rating always wins -- leave it alone.
                conflictCount++
                continue
            }
            setMangaTaste.await(
                mangaId = manga.id,
                source = manga.source,
                url = manga.url,
                title = manga.title,
                rating = MangaRating.NOT_INTERESTED,
            )
            migratedCount++
        }

        logcat {
            "Not Interested legacy backfill: parsed=${keys.size}, migrated=$migratedCount, " +
                "conflicts=$conflictCount, unresolved=$unresolvedCount"
        }
        migratedCount > 0
    }
}
// KMK <--
