package exh.recs.sources

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// KMK C3 (HR-2026-08-26-FOCUS-CRITERION-CATALOG-COMPLETENESS, E4.3)
class SourceGenreCatalogCacheTest {

    private class GenreTriState(name: String) : Filter.TriState(name)
    private class GenreGroup(name: String, state: List<Filter<*>>) : Filter.Group<Filter<*>>(name, state)

    @BeforeEach
    fun setUp() {
        SourceGenreCatalogCache.clearForTesting()
    }

    @AfterEach
    fun tearDown() {
        SourceGenreCatalogCache.clearForTesting()
    }

    private fun genreFilterList(vararg names: String) =
        FilterList(GenreGroup("Genres", names.map { GenreTriState(it) }))

    /** Shorthand for a successful [SourceGenreCatalogCache.record] call at [generation]. */
    private fun success(sourceId: Long, generation: Long, vararg names: String) {
        SourceGenreCatalogCache.record(sourceId, generation, Result.success(genreFilterList(*names)))
    }

    /** Shorthand for a successful-but-empty [SourceGenreCatalogCache.record] call at [generation]. */
    private fun successEmpty(sourceId: Long, generation: Long) {
        SourceGenreCatalogCache.record(sourceId, generation, Result.success(FilterList()))
    }

    /** Shorthand for a failed [SourceGenreCatalogCache.record] call at [generation]. */
    private fun failure(sourceId: Long, generation: Long) {
        SourceGenreCatalogCache.record(sourceId, generation, Result.failure(RuntimeException("simulated fetch failure")))
    }

    /** Shorthand for [SourceGenreCatalogCache.pruneIneligibleSources] at [generation]. */
    private fun prune(eligibleSourceIds: Set<Long>, generation: Long) {
        SourceGenreCatalogCache.pruneIneligibleSources(eligibleSourceIds = eligibleSourceIds, generation = generation)
    }

    @Test
    fun `snapshot is empty before any source is recorded`() {
        assertTrue(SourceGenreCatalogCache.snapshot().isEmpty())
    }

    @Test
    fun `snapshot unions labels recorded from multiple different sources`() {
        success(1L, generation = 1, "Action")
        success(2L, generation = 1, "Comedy")

        assertEquals(setOf("action", "comedy"), SourceGenreCatalogCache.snapshot())
    }

    // KMK F2-03 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM) correction: this test used
    // to assert the OLD `computeIfAbsent` behavior (first record for a source id wins forever). That
    // was the exact reopened "sticky empty/error results" defect -- a transient getFilterList()
    // failure permanently cached as empty, with no way to recover except a process restart. The
    // corrected contract is the opposite: a later, genuinely non-empty extraction always wins.
    @Test
    fun `a later non-empty record for the same source id overwrites an earlier one, not stuck forever`() {
        success(1L, generation = 1, "Action")
        success(1L, generation = 2, "Totally Different Genre")

        assertEquals(
            setOf("totally different genre"),
            SourceGenreCatalogCache.snapshot(),
            "a later successful extraction for the same source id must overwrite the earlier one",
        )
    }

    @Test
    fun `a successful but empty extraction is never cached -- a transient failure can recover on the very next call`() {
        // Simulates the real caller's own getFilterList() genuinely succeeding but returning no
        // usable genre filters this one time -- not a fetch failure.
        successEmpty(1L, generation = 1)
        assertTrue(SourceGenreCatalogCache.snapshot().isEmpty(), "a successful empty extraction must not be cached at all")

        // A later call for the SAME source id (e.g. the very next refresh) succeeds -- this must not
        // be blocked by the earlier empty attempt.
        success(1L, generation = 2, "Action")
        assertEquals(setOf("action"), SourceGenreCatalogCache.snapshot(), "recovery must succeed on the next call, no restart needed")
    }

    @Test
    fun `a source that legitimately has zero genre filters contributes nothing every time, harmlessly`() {
        repeat(3) { generation -> successEmpty(1L, generation = generation.toLong()) }

        assertTrue(SourceGenreCatalogCache.snapshot().isEmpty())
    }

    @Test
    fun `the snapshot survives independent of what the current recommendation load happens to include`() {
        // Simulates a source seen in a PAST load contributing to a catalog snapshot requested during
        // a load where that source produced no results at all this time -- the whole point of E4.3.
        success(99L, generation = 1, "Historical")

        val snapshotDuringAnUnrelatedLoad = SourceGenreCatalogCache.snapshot()

        assertTrue("historical" in snapshotDuringAnUnrelatedLoad)
    }

    @Test
    fun `clearForTesting resets accumulated state`() {
        success(1L, generation = 1, "Action")
        SourceGenreCatalogCache.clearForTesting()

        assertTrue(SourceGenreCatalogCache.snapshot().isEmpty())
    }

    // KMK F2-03 corrective slice B: required coverage item 1 -- a source that previously advertised
    // genres and later successfully returns none must have its stale labels PRUNED, not retained.
    @Test
    fun `a successful empty extraction after a previous non-empty success prunes the stale labels`() {
        success(1L, generation = 1, "Action")
        assertEquals(setOf("action"), SourceGenreCatalogCache.snapshot())

        successEmpty(1L, generation = 2)

        assertTrue(
            SourceGenreCatalogCache.snapshot().isEmpty(),
            "a genuinely successful empty extraction must prune the source's earlier stale labels",
        )
    }

    // KMK F2-03 corrective slice B: required coverage item 2 -- a fetch FAILURE must retain the last
    // successful entry, distinct from a genuinely successful empty extraction (which prunes).
    @Test
    fun `a fetch failure after a previous success retains the last known-good labels`() {
        success(1L, generation = 1, "Action")
        assertEquals(setOf("action"), SourceGenreCatalogCache.snapshot())

        failure(1L, generation = 2)

        assertEquals(
            setOf("action"),
            SourceGenreCatalogCache.snapshot(),
            "a fetch failure must retain the source's last successful labels, not erase them",
        )
    }

    @Test
    fun `a fetch failure with no prior success contributes nothing, but does not throw`() {
        failure(1L, generation = 1)

        assertTrue(SourceGenreCatalogCache.snapshot().isEmpty())
    }

    // KMK F2-03 corrective slice B: required coverage item 3 -- explicit source removal/pruning.
    @Test
    fun `pruneIneligibleSources removes entries for sources no longer eligible`() {
        success(1L, generation = 1, "Action")
        success(2L, generation = 1, "Comedy")
        assertEquals(setOf("action", "comedy"), SourceGenreCatalogCache.snapshot())

        // Source 2 was disabled/uninstalled -- only source 1 remains eligible.
        prune(eligibleSourceIds = setOf(1L), generation = 1)

        assertEquals(
            setOf("action"),
            SourceGenreCatalogCache.snapshot(),
            "a source no longer in the eligible set must be pruned from the catalog",
        )
    }

    @Test
    fun `pruneIneligibleSources with an empty eligible set clears the whole catalog`() {
        success(1L, generation = 1, "Action")
        success(2L, generation = 1, "Comedy")

        prune(eligibleSourceIds = emptySet(), generation = 1)

        assertTrue(SourceGenreCatalogCache.snapshot().isEmpty())
    }

    @Test
    fun `pruneIneligibleSources leaves an untouched eligible source's entry exactly as it was`() {
        success(1L, generation = 1, "Action")

        prune(eligibleSourceIds = setOf(1L, 2L, 3L), generation = 1)

        assertEquals(setOf("action"), SourceGenreCatalogCache.snapshot())
    }

    // KMK F2-03 production-caller-boundary correction (2026-08-27): a THIRD independent review found
    // that pruning based only on eligibleSourceIds and the cache's own existing keys leaves a real
    // caller-level gap: a source that was eligible in an OLDER refresh, never recorded anything (no
    // cache entry yet), and was uninstalled before a NEWER refresh runs is invisible to BOTH the
    // newer refresh's own eligibleSourceIds AND the cache's existing keys -- so no barrier was ever
    // raised, and the older refresh's delayed first record() call could still repopulate it.
    // everEligibleSourceIds closes this: the two pruneIneligibleSources calls below simulate exactly
    // the real caller sequence (an older refresh's own eligible computation, then a newer refresh's
    // own eligible computation) -- deliberately WITHOUT ever passing source 1L as a direct
    // "ineligible" hint to either call, proving the cache remembers it from having been eligible
    // once, not because a caller told it to watch for that specific id.
    @Test
    fun `pruneIneligibleSources barriers a source that was eligible in an earlier call but is absent from a later one, even though it was never recorded`() {
        // Simulates an OLDER refresh's own eligible-set computation, which included source 1L (it
        // was installed and enabled then). Being merely eligible must not create any cache entry.
        SourceGenreCatalogCache.pruneIneligibleSources(eligibleSourceIds = setOf(1L), generation = 1)
        assertTrue(SourceGenreCatalogCache.snapshot().isEmpty(), "precondition: merely being eligible once must not create an entry")

        // Simulates a NEWER refresh's own eligible-set computation, which no longer includes 1L (it
        // was uninstalled) -- this call's own eligibleSourceIds never mentions 1L directly.
        SourceGenreCatalogCache.pruneIneligibleSources(eligibleSourceIds = emptySet(), generation = 9)

        // A delayed record() for source 1L, from the OLDER refresh (generation 2 < the second
        // prune's 9), must be rejected even though it is genuinely the first ever record() call for
        // that source id.
        success(1L, generation = 2, "Stale First-Ever Record")

        assertTrue(
            SourceGenreCatalogCache.snapshot().isEmpty(),
            "a source eligible in an earlier prune call but absent from a later one must be barriered " +
                "even though the later call's own eligibleSourceIds never named it directly and " +
                "record() was never previously called for it",
        )
    }

    @Test
    fun `a source barriered this way still accepts a later record at a strictly newer generation, such as after reinstall`() {
        SourceGenreCatalogCache.pruneIneligibleSources(eligibleSourceIds = setOf(1L), generation = 1)
        SourceGenreCatalogCache.pruneIneligibleSources(eligibleSourceIds = emptySet(), generation = 2)

        // A NEWER refresh re-enables (e.g. reinstalls) the source and records it -- generation 9 is
        // strictly newer than the second prune's barrier generation 2.
        success(1L, generation = 9, "Newer Record After Re-Enable")

        assertEquals(
            setOf("newer record after re enable"),
            SourceGenreCatalogCache.snapshot(),
            "a later record at a strictly newer generation than the proactive prune must still be accepted",
        )
    }

    @Test
    fun `everEligibleSourceIds does not grow when the same eligible set is passed repeatedly`() {
        // Bounded-memory sanity check: repeatedly passing the SAME eligible set must never make
        // pruning misbehave (e.g. by accidentally barriering an eligible source) -- a plain Set's
        // own semantics already guarantee no growth from re-adding the same elements, but this
        // proves the observable BEHAVIOR stays correct across many repeated calls, not merely that
        // the internal set's size is bounded (which is not itself directly observable from outside).
        repeat(50) { generation ->
            success(1L, generation = generation.toLong(), "Stable")
            SourceGenreCatalogCache.pruneIneligibleSources(eligibleSourceIds = setOf(1L), generation = generation.toLong())
        }

        assertEquals(
            setOf("stable"),
            SourceGenreCatalogCache.snapshot(),
            "a source repeatedly reaffirmed as eligible must never be accidentally pruned by its own history",
        )
    }

    // KMK F2-03 corrective slice B: staleness -- a late-arriving result from an OLDER, superseded
    // refresh generation must never overwrite what a NEWER refresh already recorded for the same
    // source, whichever order the two calls actually land in.
    @Test
    fun `a stale older-generation record never overwrites a newer generation's result`() {
        success(1L, generation = 5, "Fresh")
        // Generation 5's refresh already recorded its result; generation 3's late-arriving call
        // (from an earlier, already-superseded refresh) must be discarded outright.
        success(1L, generation = 3, "Stale")

        assertEquals(
            setOf("fresh"),
            SourceGenreCatalogCache.snapshot(),
            "an older generation's late-arriving result must never overwrite a newer generation's",
        )
    }

    @Test
    fun `a stale older-generation failure never prunes or otherwise disturbs a newer generation's result`() {
        success(1L, generation = 5, "Fresh")

        // A stale generation's late-arriving failure must not touch the newer entry at all.
        failure(1L, generation = 3)

        assertEquals(setOf("fresh"), SourceGenreCatalogCache.snapshot())
    }

    @Test
    fun `a stale older-generation successful-empty result never prunes a newer generation's result`() {
        success(1L, generation = 5, "Fresh")

        // A stale generation's late-arriving "successful empty" must not prune the newer entry.
        successEmpty(1L, generation = 3)

        assertEquals(setOf("fresh"), SourceGenreCatalogCache.snapshot())
    }

    @Test
    fun `an equal generation is accepted, not treated as stale`() {
        success(1L, generation = 5, "First")
        // Same generation -- e.g. the same refresh's own call, or a retry within that refresh --
        // must still be able to update the entry; only a strictly OLDER generation is discarded.
        success(1L, generation = 5, "Second")

        assertEquals(setOf("second"), SourceGenreCatalogCache.snapshot())
    }

    // --- Tombstone-generation follow-up correction (2026-08-28) ---
    //
    // A second independent review found that the slice-B fix above still stored labels and the
    // generation barrier together in one removable Entry: both a successful-empty result and
    // pruneIneligibleSources deleted the WHOLE entry, including the generation barrier that was
    // supposed to protect against a late arrival. The two cases below are the review's own named
    // adversarial scenarios, reproduced exactly.

    // Named scenario 1: "generation 5 successful-empty followed by generation 3 nonempty remains
    // empty". Before the fix, generation 5's successful-empty removed the Entry entirely (labels
    // AND generation), so generation 3's later nonempty result saw no entry, was treated as the
    // FIRST record for that source, and incorrectly repopulated stale labels.
    @Test
    fun `generation 5 successful-empty followed by generation 3 nonempty remains empty`() {
        success(1L, generation = 5, "Fresh")
        successEmpty(1L, generation = 5)
        assertTrue(SourceGenreCatalogCache.snapshot().isEmpty(), "precondition: the successful-empty result pruned the earlier labels")

        // The delayed, older, nonempty result must NOT repopulate the source now that a newer
        // generation has already established (via a genuinely successful empty extraction) that
        // this source currently has no genre filters.
        success(1L, generation = 3, "Stale Delayed Result")

        assertTrue(
            SourceGenreCatalogCache.snapshot().isEmpty(),
            "an older delayed nonempty result must not repopulate labels a newer generation's " +
                "successful-empty result already pruned -- the generation barrier must survive the prune",
        )
    }

    // Named scenario 2: "prune followed by an older in-flight result remains pruned". Before the
    // fix, pruneIneligibleSources removed the key from the (then single) Entry map entirely, so an
    // older in-flight result arriving after the prune saw no entry and could repopulate it.
    @Test
    fun `prune followed by an older in-flight result remains pruned`() {
        success(1L, generation = 1, "Action")
        assertEquals(setOf("action"), SourceGenreCatalogCache.snapshot())

        // A newer refresh (generation 4) determines source 1 is no longer eligible (disabled or
        // uninstalled) and prunes it.
        prune(eligibleSourceIds = emptySet(), generation = 4)
        assertTrue(SourceGenreCatalogCache.snapshot().isEmpty(), "precondition: source 1 was pruned")

        // An OLDER refresh's in-flight fetch for source 1 (generation 2, launched before the prune's
        // own refresh but completing after it) finally arrives.
        success(1L, generation = 2, "Stale In-Flight Result")

        assertTrue(
            SourceGenreCatalogCache.snapshot().isEmpty(),
            "an older in-flight result must not repopulate a source a newer refresh has already pruned",
        )
    }

    // Required coverage: "older failure arriving after newer invalidation" -- a delayed FAILURE
    // (not just a delayed success) must not resurrect a pruned source either, and must not disturb
    // the pruned (empty-labels) state that is already there.
    @Test
    fun `prune followed by an older in-flight failure remains pruned and does not resurrect the entry`() {
        success(1L, generation = 1, "Action")
        prune(eligibleSourceIds = emptySet(), generation = 4)
        assertTrue(SourceGenreCatalogCache.snapshot().isEmpty(), "precondition: source 1 was pruned")

        // An OLDER refresh's in-flight fetch for source 1 finally FAILS after the prune already ran.
        failure(1L, generation = 2)

        assertTrue(
            SourceGenreCatalogCache.snapshot().isEmpty(),
            "an older in-flight failure must not resurrect a source a newer refresh has already pruned",
        )
    }

    // A generation EQUAL to the pruning refresh's own generation must still be able to act (e.g. the
    // source's own searchSource() call for THIS refresh, before it becomes ineligible partway through
    // -- not a stale arrival). Only a STRICTLY older generation is rejected, consistent with
    // `an equal generation is accepted, not treated as stale` above.
    @Test
    fun `a record at the same generation as a prune is still accepted, not treated as stale`() {
        prune(eligibleSourceIds = emptySet(), generation = 5)

        success(1L, generation = 5, "Same Generation")

        assertEquals(
            setOf("same generation"),
            SourceGenreCatalogCache.snapshot(),
            "a record at the same generation as a prior prune is not itself stale and must be accepted",
        )
    }

    // A failure must ALSO raise the staleness barrier (not only successes), so an even-older result
    // cannot sneak in behind a failure the way it could behind an unprotected successful-empty.
    @Test
    fun `a failure raises the staleness barrier -- an older result arriving after a failure is still rejected`() {
        success(1L, generation = 1, "Original")
        failure(1L, generation = 5)
        assertEquals(setOf("original"), SourceGenreCatalogCache.snapshot(), "precondition: failure retained the last successful labels")

        // A delayed, older-than-the-failure result must not overwrite what the failure's generation
        // already established as current.
        success(1L, generation = 3, "Stale Delayed Result")

        assertEquals(
            setOf("original"),
            SourceGenreCatalogCache.snapshot(),
            "a failure must raise the staleness barrier just like a success does",
        )
    }

    // Overlapping-refresh coverage: three results for the same source, from three different
    // refresh generations, arriving completely out of the order their refreshes were started in
    // (2 launched, then 5, then 5's failure lands, then 2's success lands, then 3's -- all after
    // 5 already completed) -- the final state must always reflect only the highest generation seen,
    // regardless of arrival order.
    @Test
    fun `overlapping refreshes for the same source converge to the highest generation regardless of arrival order`() {
        success(1L, generation = 2, "From Refresh Two")
        success(1L, generation = 5, "From Refresh Five")
        failure(1L, generation = 3) // a third, older-than-5 refresh's failure, arriving even later
        successEmpty(1L, generation = 1) // a fourth, oldest refresh's empty result, arriving last of all

        assertEquals(
            setOf("from refresh five"),
            SourceGenreCatalogCache.snapshot(),
            "regardless of arrival order, only the highest-generation refresh's result may be reflected",
        )
    }

    // KMK F2-03 corrective slice B: required coverage item 3 (deterministic bound) -- when the
    // catalog exceeds MAX_TOTAL_LABELS, the retained subset must be the same every time, not
    // dependent on ConcurrentHashMap's unspecified iteration order.
    @Test
    fun `snapshot truncates deterministically by source id then alphabetically when exceeding the bound`() {
        // MAX_TOTAL_LABELS is 2000 and private; use a source count comfortably small enough to stay
        // well under any real bound while still proving the ordering rule itself directly, rather
        // than trying to exceed the literal cap from a unit test.
        success(3L, generation = 1, "Zebra", "Apple")
        success(1L, generation = 1, "Mango", "Banana")
        success(2L, generation = 1, "Kiwi")

        // Not a set comparison -- reconstructs the exact expected ORDER (source id ascending, then
        // alphabetical within a source) to prove snapshot() is actually deterministic, not merely
        // that it returns the right elements.
        val expectedOrder = listOf("banana", "mango", "kiwi", "apple", "zebra")
        val actualOrder = SourceGenreCatalogCache.snapshot().toList()

        assertEquals(expectedOrder, actualOrder, "snapshot must order by ascending source id, then alphabetically within a source")

        // Determinism across repeated calls with the same accumulated state.
        assertEquals(actualOrder, SourceGenreCatalogCache.snapshot().toList())
    }

    @Test
    fun `snapshot deduplicates a label shared across two different sources`() {
        success(1L, generation = 1, "Action")
        success(2L, generation = 1, "Action")

        assertEquals(setOf("action"), SourceGenreCatalogCache.snapshot())
    }
}
