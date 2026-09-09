package exh.recs.sources

import eu.kanade.tachiyomi.source.model.FilterList
import java.util.concurrent.ConcurrentHashMap

// KMK HR-2026-08-26-FOCUS-CRITERION-CATALOG-COMPLETENESS (C3/E4.3) -->
/**
 * Process-lifetime, per-source cache of [SourceGenreFilterExtractor.extract]'s output, so the For
 * You focus criterion catalog is not limited to whatever happens to be in the currently loaded
 * recommendation result set -- once a source's filter capabilities have been seen even once (in
 * any past load, this session), they stay available to the catalog for the rest of the process,
 * independent of whether that source produced any candidates in the CURRENT load.
 *
 * Deliberately reuses [BrowsePersonalRecommendationsScreenModel]'s own existing
 * `SourceRuntime.run(source, SourceRuntimeOperation.FilterList) { getFilterList() }` call (already
 * made once per matching source, every refresh, to build search queries) rather than issuing a
 * second network round-trip just for cataloging -- [record] is called as a free byproduct right
 * after that existing call already has the raw `Result<FilterList>` in hand.
 *
 * KMK F2-03 corrective history (see the fix2 handoff's F2-03 sections for full dated receipts):
 * 2026-08-27 fixed failure/successful-empty conflation, added deterministic snapshot ordering, and
 * added [pruneIneligibleSources]. 2026-08-28 (first pass) fixed a tombstone-generation defect by
 * splitting the generation barrier and the labels into two separate maps -- which then introduced a
 * NEW defect an independent review correctly found: because acceptance (checking the barrier) and
 * mutation (writing labels) were two separate operations across two separate maps, they were not
 * atomic together. An older call could pass its acceptance check, be paused (thread scheduling,
 * GC, or genuine concurrent contention), and then write its (now stale) labels AFTER a newer call
 * had already accepted and written its own -- the two-map split reintroduced exactly the race the
 * single-map tombstone was meant to prevent, just one map removed from where it was visible. The
 * same problem applied to [pruneIneligibleSources]: an older prune could still remove labels a
 * newer, already-completed record had just written.
 *
 * KMK F2-03 atomicity correction (2026-08-27): back to ONE map, [bySource], but never deleting an
 * entry once created (the actual defect in the ORIGINAL, pre-2026-08-27 design was deleting the
 * whole entry on a successful-empty result or a prune, which lost the generation barrier along with
 * the labels -- not the fact that it was one map). [Entry] now always carries both the current
 * labels (possibly empty, meaning "no labels currently active," not "unknown") and the generation
 * that produced them, and every mutation -- [record]'s three outcomes and [pruneIneligibleSources]
 * -- goes through [ConcurrentHashMap.compute], whose remapping function [ConcurrentHashMap] executes
 * atomically and exclusively per key (per the JDK's own contract: "the entire method invocation is
 * performed atomically... some attempted update operations on this map by other threads may be
 * blocked while computation is in progress"). Reading the current entry, deciding whether the
 * incoming generation is stale, and writing the new entry therefore all happen as a single
 * indivisible step for a given source id -- there is no window between "accepted" and "written" for
 * a slower, older call to land after a faster, newer one. See
 * [SourceGenreCatalogCacheConcurrencyTest] for real multi-threaded proof of this property, not just
 * sequential arrival-order tests (which cannot exercise a race that depends on genuine thread
 * interleaving).
 *
 * KMK F2-03 production-caller-boundary correction (2026-08-27): a THIRD independent review found a
 * narrower gap one level above the atomicity fix: [pruneIneligibleSources] could only protect a
 * source id it could currently SEE -- either already cached, or present in the calling refresh's own
 * `eligibleSourceIds`. A source that was eligible in an OLDER refresh, never recorded anything (no
 * cache entry yet), and was then uninstalled before a NEWER refresh ran was invisible to BOTH of
 * those sources of truth at once, so no barrier was ever raised for it, and the older refresh's
 * delayed first `record()` call could still repopulate it. [everEligibleSourceIds] (see its own doc)
 * closes this by making the cache itself the durable "which source ids has this process ever known
 * about" owner, rather than requiring [BrowsePersonalRecommendationsScreenModel] to track that
 * history itself.
 *
 * Bounded two ways: [SourceGenreFilterExtractor] itself caps labels per source, and [snapshot] caps
 * the total unioned catalog size so an installation with many verbose sources can never make the
 * focus picker unbounded. [everEligibleSourceIds] (see its own doc) is bounded too, by the total
 * number of DISTINCT source ids this process has ever seen installed-and-enabled -- a small,
 * realistic count (this app's own installed-extension total, typically dozens, never attacker- or
 * caller-controllable), not an unbounded or ever-growing history store.
 */
internal object SourceGenreCatalogCache {

    /** Caps the total catalog size across every cached source -- see the class doc. */
    private const val MAX_TOTAL_LABELS = 2000

    /**
     * [labels] is the currently active label set for a source (possibly empty -- see the class
     * doc), and [generation] is the highest generation that has ever produced the CURRENT value of
     * [labels] for this key -- once a key has an [Entry] at all, it is never removed from
     * [bySource]; only its [Entry] is atomically replaced, which is what lets an empty [labels] set
     * still carry forward a real, un-erasable generation barrier.
     */
    private data class Entry(val labels: Set<String>, val generation: Long)

    private val bySource = ConcurrentHashMap<Long, Entry>()

    /**
     * KMK F2-03 production-caller-boundary correction: every source id this process has EVER seen
     * passed as `eligibleSourceIds` to [pruneIneligibleSources], retained for the life of the
     * process. Monotonically grows, never shrinks -- see the class doc for why this is a bounded,
     * realistic set rather than an unbounded history store.
     *
     * This is what makes [SourceGenreCatalogCache] itself the "refresh-participation owner" an
     * independent review required: without it, [pruneIneligibleSources] could only see a source id
     * that was CURRENTLY known (either already cached, or passed in this exact call's eligible set)
     * -- so a source that was eligible in an OLDER refresh, never recorded anything before being
     * uninstalled, would be invisible to a NEWER refresh's own eligible-set computation (it is gone
     * from `sourceManager.getVisibleSources()` entirely) and would therefore never get a barrier
     * raised for it. [everEligibleSourceIds] closes this: because every refresh's [pruneIneligibleSources]
     * call folds its own eligible set into this running union FIRST, a source id the CURRENT call's
     * `eligibleSourceIds` omits but a PAST call's `eligibleSourceIds` included is still discoverable
     * as "known to this process, currently ineligible" -- without the caller needing to track or
     * pass any historical information itself. The caller-facing contract stays exactly "here is
     * what's eligible right now"; the cache remembers the rest.
     */
    private val everEligibleSourceIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    /**
     * Records the outcome of a genre-filter fetch for [sourceId], made at [generation] (a
     * caller-owned monotonic counter -- see the class doc and
     * [BrowsePersonalRecommendationsScreenModel]'s own atomic capture of its per-refresh
     * generation).
     *
     * The entire decision -- reject as stale, or accept and compute the new [Entry] -- happens
     * inside one [ConcurrentHashMap.compute] call for [sourceId], so it is atomic with respect to
     * every other concurrent [record] or [pruneIneligibleSources] call for the SAME source id (see
     * the class doc for why this matters). Concurrent calls for DIFFERENT source ids never contend
     * with each other at all -- [ConcurrentHashMap]'s per-key locking is independent per bucket.
     *
     * - [generation] strictly older than the source's current entry: discarded outright, whichever
     *   branch below it would otherwise have taken.
     * - [result] success, non-empty extraction: the entry's labels become the fresh extraction.
     * - [result] success, empty extraction: the entry's labels become empty -- a genuinely
     *   successful "no genre filters" result must prune any stale labels from an earlier fetch, but
     *   the entry (and its generation) is never deleted, so the generation barrier survives.
     * - [result] failure: the entry's labels are carried forward UNCHANGED from whatever they were
     *   -- a transient fetch failure must never erase a previously known-good catalog contribution
     *   -- but the entry's generation still advances to this call's [generation], so a later, even
     *   older call cannot sneak in behind this failure either.
     */
    fun record(sourceId: Long, generation: Long, result: Result<FilterList>) {
        bySource.compute(sourceId) { _, existing ->
            if (existing != null && generation < existing.generation) return@compute existing
            val filterList = result.getOrNull()
            if (filterList == null) {
                Entry(existing?.labels ?: emptySet(), generation)
            } else {
                Entry(SourceGenreFilterExtractor.extract(filterList), generation)
            }
        }
    }

    /**
     * Clears active labels and raises the generation barrier (to at least [generation] -- the
     * pruning refresh's own immutable generation) for every source id this process has ever known
     * about that is not present in [eligibleSourceIds]: every currently-cached key (covers a source
     * that has since been uninstalled, whose stale cache entry would otherwise linger) UNIONED with
     * every id in [everEligibleSourceIds] (covers a source that was eligible in a PAST refresh,
     * never recorded anything, and has since disappeared entirely -- see that property's own doc for
     * why this is required, not merely convenient). [eligibleSourceIds] is folded into
     * [everEligibleSourceIds] FIRST, so the running history is always current. Each touched source id
     * goes through the same atomic [ConcurrentHashMap.compute] step [record] uses, so a concurrent
     * [record] call for the same source id can never race this operation for that key.
     *
     * The caller-facing contract is deliberately minimal: pass only THIS refresh's own currently
     * eligible source ids and its own generation. The caller never needs to track, diff, or persist
     * any historical source-id information itself -- see [everEligibleSourceIds]'s own doc for why
     * that responsibility belongs here instead.
     *
     * Deliberately independent of any per-refresh LANGUAGE filter -- a language filter is transient,
     * per-refresh, user-togglable state, not a durable "this source is gone" signal, and pruning on
     * it would make the catalog flicker as the user changes that filter.
     */
    fun pruneIneligibleSources(eligibleSourceIds: Set<Long>, generation: Long) {
        everEligibleSourceIds.addAll(eligibleSourceIds)
        val targets = (bySource.keys + everEligibleSourceIds) - eligibleSourceIds
        for (sourceId in targets) {
            bySource.compute(sourceId) { _, existing ->
                if (existing != null && generation < existing.generation) return@compute existing
                Entry(emptySet(), generation)
            }
        }
    }

    /**
     * The bounded, deterministically-ordered union of every source's currently active labels.
     * Ordering: sources by ascending id, then each source's own labels alphabetically -- an
     * arbitrary but fixed and reproducible rule, chosen only so the SAME accumulated cache state
     * always truncates to the SAME bounded result, not because source id or alphabetical order is
     * meaningful to the focus picker itself (which re-sorts for display independently).
     */
    fun snapshot(): Set<String> {
        return bySource.entries
            .sortedBy { it.key }
            .asSequence()
            .flatMap { it.value.labels.sorted().asSequence() }
            .distinct()
            .take(MAX_TOTAL_LABELS)
            .toSet()
    }

    /** Test-only: resets accumulated state so tests never leak into each other via this singleton. */
    internal fun clearForTesting() {
        bySource.clear()
        everEligibleSourceIds.clear()
    }
}
// KMK <--
