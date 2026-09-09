package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.normalizeTag

class RecommendationFocusPolicyTest {

    @Test
    fun `focus option canonicalization collapses source aliases to one group`() {
        val aliases = mapOf("action fantasy" to "action")

        assertEquals("action", canonicalFocusGroup("Action-Fantasy", aliases))
        assertEquals("action", canonicalFocusGroup("action", aliases))
    }

    @Test
    fun `focus catalog search is normalized and keeps selection-independent results`() {
        val groups = listOf("action", "slice of life", "science fiction")

        assertEquals(listOf("action"), filterFocusGroups(groups, "  ACT!  "))
        assertEquals(groups, filterFocusGroups(groups, "   "))
    }

    @Test
    fun `focus catalog search matches an existing alias without exposing it as a duplicate`() {
        val groups = listOf("science fiction", "action")
        val aliasMap = mapOf("sci fi" to "science fiction")

        assertEquals(listOf("science fiction"), filterFocusGroups(groups, "SCI-FI", aliasMap))
        assertEquals(listOf("science fiction"), filterFocusGroups(groups, "fiction", aliasMap))
    }

    private fun recommendation(id: Long, genres: List<String>, score: Double = 0.0): PersonalRecommendation =
        PersonalRecommendation(
            manga = Manga.create().copy(id = id, source = 1L, url = "/m/$id", ogGenre = genres),
            score = score,
            matchedGroups = emptyList(),
        )

    private fun criteria(
        include: Set<String> = emptySet(),
        exclude: Set<String> = emptySet(),
        matchAll: Boolean = true,
    ) = RecommendationFocusPolicy.FocusCriteria(include, exclude, matchAll)

    @Test
    fun `an inactive criteria (no include, no exclude) is a cleared focus`() {
        val result = RecommendationFocusPolicy.apply(listOf(recommendation(1, listOf("Action"))), criteria(), emptyMap())
        assertEquals(RecommendationFocusPolicy.Outcome.Cleared, result)
    }

    // KMK v0.8.21-fix5: R4/AUG-14 completion -- direct product correction. This is now a real
    // filter: a candidate failing the include/exclude/Match-All-or-Any contract is genuinely
    // absent from Outcome.Filtered.matched (though always still present in Outcome.Filtered.all,
    // for the transient "Show Broader Results" view).

    @Test
    fun `Match Any include keeps a candidate matching at least one included group`() {
        val action = recommendation(1, listOf("Action"))
        val romance = recommendation(2, listOf("Romance"))

        val result = RecommendationFocusPolicy.apply(
            listOf(action, romance),
            criteria(include = setOf("action", "fantasy"), matchAll = false),
            emptyMap(),
        )

        val filtered = (result as RecommendationFocusPolicy.Outcome.Filtered)
        assertEquals(listOf(action), filtered.matched.map { it.recommendation })
        assertEquals(listOf(action, romance), filtered.all)
    }

    @Test
    fun `Match All include requires every included group to be present`() {
        val both = recommendation(1, listOf("Action", "Fantasy"))
        val onlyAction = recommendation(2, listOf("Action"))

        val result = RecommendationFocusPolicy.apply(
            listOf(both, onlyAction),
            criteria(include = setOf("action", "fantasy"), matchAll = true),
            emptyMap(),
        )

        val filtered = (result as RecommendationFocusPolicy.Outcome.Filtered)
        assertEquals(listOf(both), filtered.matched.map { it.recommendation })
    }

    @Test
    fun `Match All is the default when matchAll is not specified`() {
        val default = RecommendationFocusPolicy.FocusCriteria(include = setOf("action", "fantasy"))
        assertTrue(default.matchAll)
    }

    @Test
    fun `exclude drops a candidate matching any excluded group regardless of Match All or Any`() {
        val actionHorror = recommendation(1, listOf("Action", "Horror"))
        val actionOnly = recommendation(2, listOf("Action"))

        val matchAllResult = RecommendationFocusPolicy.apply(
            listOf(actionHorror, actionOnly),
            criteria(include = setOf("action"), exclude = setOf("horror"), matchAll = true),
            emptyMap(),
        )
        val matchAnyResult = RecommendationFocusPolicy.apply(
            listOf(actionHorror, actionOnly),
            criteria(include = setOf("action"), exclude = setOf("horror"), matchAll = false),
            emptyMap(),
        )

        assertEquals(
            listOf(actionOnly),
            (matchAllResult as RecommendationFocusPolicy.Outcome.Filtered).matched.map { it.recommendation },
        )
        assertEquals(
            listOf(actionOnly),
            (matchAnyResult as RecommendationFocusPolicy.Outcome.Filtered).matched.map { it.recommendation },
        )
    }

    @Test
    fun `include-exclude conflict on the same group -- exclude wins, candidate is dropped`() {
        val action = recommendation(1, listOf("Action"))

        val result = RecommendationFocusPolicy.apply(
            listOf(action),
            criteria(include = setOf("action"), exclude = setOf("action")),
            emptyMap(),
        )

        val filtered = (result as RecommendationFocusPolicy.Outcome.Filtered)
        assertTrue(filtered.matched.isEmpty())
        assertEquals(listOf(action), filtered.all, "the original candidate is still present in .all for Show Broader Results")
    }

    @Test
    fun `exclude-only criteria (no include) keeps every candidate not matching an excluded group`() {
        val romance = recommendation(1, listOf("Romance"))
        val horror = recommendation(2, listOf("Horror"))

        val result = RecommendationFocusPolicy.apply(
            listOf(romance, horror),
            criteria(exclude = setOf("horror")),
            emptyMap(),
        )

        val filtered = (result as RecommendationFocusPolicy.Outcome.Filtered)
        assertEquals(listOf(romance), filtered.matched.map { it.recommendation })
    }

    @Test
    fun `matching is case-insensitive and punctuation-tolerant via the shared normalizer`() {
        val candidate = recommendation(1, listOf("Sci-Fi"))

        val result = RecommendationFocusPolicy.apply(
            listOf(candidate),
            criteria(include = setOf("SCI fi")),
            emptyMap(),
        )

        val filtered = (result as RecommendationFocusPolicy.Outcome.Filtered)
        assertEquals(listOf(candidate), filtered.matched.map { it.recommendation })
    }

    @Test
    fun `duplicate and differently cased genres do not inflate include coverage under Match All`() {
        val candidate = recommendation(1, listOf("Action", "action", " ACTION "))

        val result = RecommendationFocusPolicy.apply(
            listOf(candidate),
            criteria(include = setOf("Action", "action"), matchAll = true),
            emptyMap(),
        )

        val filtered = (result as RecommendationFocusPolicy.Outcome.Filtered)
        assertEquals(setOf("action"), filtered.matched.single().matchedGroups)
    }

    @Test
    fun `explicit aliases apply to both selection and candidate metadata`() {
        val candidate = recommendation(1, listOf("Yuri"))

        val result = RecommendationFocusPolicy.apply(
            listOf(candidate),
            criteria(include = setOf("girls_love")),
            mapOf("yuri" to "girls_love"),
        )

        val filtered = (result as RecommendationFocusPolicy.Outcome.Filtered)
        assertEquals(listOf(candidate), filtered.matched.map { it.recommendation })
    }

    @Test
    fun `zero matches across every candidate yields an empty matched list but a non-empty all list -- the honest empty state`() {
        val ordinary = listOf(recommendation(1, listOf("Romance")), recommendation(2, listOf("Comedy")))

        val result = RecommendationFocusPolicy.apply(ordinary, criteria(include = setOf("action")), emptyMap())

        val filtered = (result as RecommendationFocusPolicy.Outcome.Filtered)
        assertTrue(filtered.matched.isEmpty())
        assertEquals(ordinary, filtered.all)
    }

    @Test
    fun `Show Broader Results view (Outcome#all) is never itself filtered or reordered`() {
        val one = recommendation(1, listOf("Action"))
        val two = recommendation(2, listOf("Romance"))
        val three = recommendation(3, emptyList())

        val result = RecommendationFocusPolicy.apply(listOf(one, two, three), criteria(include = setOf("action")), emptyMap())

        val filtered = (result as RecommendationFocusPolicy.Outcome.Filtered)
        assertEquals(listOf(one, two, three), filtered.all)
    }

    @Test
    fun `filtering never mutates durable taste score or the taste-scoring matchedGroups field`() {
        val candidate = recommendation(1, listOf("Action"), score = 4.5)

        RecommendationFocusPolicy.apply(listOf(candidate), criteria(include = setOf("action")), emptyMap())

        assertEquals(4.5, candidate.score)
        assertEquals(emptyList<String>(), candidate.matchedGroups)
    }

    @Test
    fun `matched candidates preserve original relative order`() {
        val one = recommendation(1, listOf("Action"))
        val two = recommendation(2, listOf("Action", "Fantasy"))
        val three = recommendation(3, listOf("Action"))

        val result = RecommendationFocusPolicy.apply(listOf(one, two, three), criteria(include = setOf("action"), matchAll = false), emptyMap())

        val filtered = (result as RecommendationFocusPolicy.Outcome.Filtered)
        assertEquals(listOf(one, two, three), filtered.matched.map { it.recommendation })
    }

    // --- buildFocusAliasMap / buildFocusKnownGroups: independent_codex_recheck_2026-08-26 ---

    @Test
    fun `buildFocusAliasMap includes every built-in synonym variant, inverted to canonical`() {
        val aliasMap = buildFocusAliasMap(emptyMap())

        assertEquals("girls love", aliasMap["yuri"])
        assertEquals("boys love", aliasMap["yaoi"])
        assertEquals("sci fi", aliasMap["science fiction"])
    }

    @Test
    fun `built-in spelling variants converge on the canonical fantasy focus group`() {
        val aliasMap = buildFocusAliasMap(emptyMap())

        assertEquals("fantasy", aliasMap["fantacy"])
        val result = RecommendationFocusPolicy.apply(
            listOf(recommendation(1, listOf("Fantacy"))),
            criteria(include = setOf("Fantasy")),
            aliasMap,
        )
        assertTrue(result is RecommendationFocusPolicy.Outcome.Filtered && result.matched.size == 1)
    }

    @Test
    fun `buildFocusAliasMap merges the user's persisted alias table on top of built-in synonyms`() {
        val aliasMap = buildFocusAliasMap(mapOf("space opera" to "sci fi"))

        assertEquals("sci fi", aliasMap["space opera"], "the user's own persisted alias must resolve")
        assertEquals("girls love", aliasMap["yuri"], "built-in synonyms must still be present alongside user aliases")
    }

    @Test
    fun `buildFocusAliasMap lets a user alias override a built-in synonym on key conflict`() {
        // "yuri" is a built-in variant of "girls love" -- a user who explicitly aliased it to
        // something else has curated that on purpose and must win.
        val aliasMap = buildFocusAliasMap(mapOf("yuri" to "romance"))

        assertEquals("romance", aliasMap["yuri"])
    }

    @Test
    fun `buildFocusAliasMap drops blank normalized aliases without crashing`() {
        val aliasMap = buildFocusAliasMap(mapOf("   " to "action", "" to "action"))

        assertTrue(aliasMap.keys.none { it.isBlank() })
    }

    @Test
    fun `built-in metadata aliases converge on one canonical focus group`() {
        val aliases = buildFocusAliasMap(emptyMap())

        assertEquals("webtoon", canonicalFocusGroup("webcomic", aliases))
        assertEquals("shounen", canonicalFocusGroup("SHONEN", aliases))
        assertEquals("full color", canonicalFocusGroup("full colour", aliases))
    }

    @Test
    fun `known focus groups canonicalize metadata aliases instead of exposing duplicates`() {
        val aliases = buildFocusAliasMap(emptyMap())
        val groups = buildFocusKnownGroups(
            groupToAliases = emptyMap(),
            sourceFilterGroups = setOf("Webtoon", "Webcomic", "Shonen", "Shounen", "Full Colour"),
            aliasMap = aliases,
        )

        assertTrue("webtoon" in groups)
        assertTrue("webcomic" !in groups)
        assertTrue("shounen" in groups)
        assertTrue("shonen" !in groups)
        assertTrue("full color" in groups)
        assertTrue("full colour" !in groups)
    }

    @Test
    fun `buildFocusKnownGroups unions the alias table's groups with built-in synonym groups`() {
        val groups = buildFocusKnownGroups(mapOf("Mecha" to listOf("Robot", "Gundam")))

        assertTrue("mecha" in groups, "a user-aliased group must be offered even with no loaded results")
        assertTrue("sci fi" in groups, "a built-in synonym group must always be offered")
    }

    @Test
    fun `buildFocusKnownGroups normalizes and drops blank group keys`() {
        val groups = buildFocusKnownGroups(mapOf("  Mecha  " to emptyList(), "" to emptyList()))

        assertTrue("mecha" in groups)
        assertTrue(groups.none { it.isBlank() })
    }

    // KMK C3 (HR-2026-08-26-FOCUS-CRITERION-CATALOG-COMPLETENESS, E4.3)

    @Test
    fun `buildFocusKnownGroups unions in source filter groups alongside alias and built-in groups`() {
        val groups = buildFocusKnownGroups(
            groupToAliases = mapOf("Mecha" to listOf("Robot")),
            sourceFilterGroups = setOf("Cooking", "Post-Apocalyptic"),
        )

        assertTrue("mecha" in groups, "the alias-table group must still be present")
        assertTrue("sci fi" in groups, "the built-in synonym group must still be present")
        assertTrue("cooking" in groups, "a source-filter-derived group must now also be offered")
        assertTrue("post apocalyptic" in groups, "a source-filter-derived group is normalized like every other input")
    }

    @Test
    fun `buildFocusKnownGroups defaults sourceFilterGroups to empty, preserving the pre-C3 two-input contract`() {
        val groups = buildFocusKnownGroups(mapOf("Mecha" to emptyList()))

        assertTrue("mecha" in groups)
        assertTrue("sci fi" in groups)
    }

    // KMK --> EC-04 2026-09-01: typed focus dimensions (Genre/Demographic slice).

    @Test
    fun `classifyFocusDimension recognizes every curated demographic label`() {
        val demographics = listOf("Shounen", "Shonen", "Shoujo", "Shojo", "Seinen", "Josei", "Kids", "Children")

        demographics.forEach { label ->
            assertEquals(
                FocusDimension.DEMOGRAPHIC,
                classifyFocusDimension(label.normalizeTag()),
                "expected '$label' to classify as DEMOGRAPHIC",
            )
        }
    }

    @Test
    fun `classifyFocusDimension is case and whitespace insensitive`() {
        assertEquals(FocusDimension.DEMOGRAPHIC, classifyFocusDimension("  SHOUNEN  ".normalizeTag()))
        assertEquals(FocusDimension.DEMOGRAPHIC, classifyFocusDimension("ShOuJo".normalizeTag()))
    }

    @Test
    fun `classifyFocusDimension defaults every non-demographic group to GENRE`() {
        listOf("action", "slice of life", "science fiction", "mecha", "isekai").forEach { label ->
            assertEquals(
                FocusDimension.GENRE,
                classifyFocusDimension(label.normalizeTag()),
                "expected '$label' to classify as GENRE",
            )
        }
    }

    @Test
    fun `classifyFocusDimension does not misclassify a genre that merely contains a demographic substring`() {
        // "Shoujo Ai" / "Boys Love" style compound genres must not accidentally match the bare
        // demographic vocabulary -- classification is exact-key, not substring, matching.
        assertEquals(FocusDimension.GENRE, classifyFocusDimension("shoujo ai".normalizeTag()))
        assertEquals(FocusDimension.GENRE, classifyFocusDimension("boys love".normalizeTag()))
    }

    @Test
    fun `classifyFocusDimension separates format and presentation access labels`() {
        assertEquals(FocusDimension.FORMAT, classifyFocusDimension("Manhwa".normalizeTag()))
        assertEquals(FocusDimension.FORMAT, classifyFocusDimension("long-strip".normalizeTag()))
        assertEquals(
            FocusDimension.PRESENTATION_ACCESS,
            classifyFocusDimension("Full Colour".normalizeTag()),
        )
        assertEquals(FocusDimension.PRESENTATION_ACCESS, classifyFocusDimension("free".normalizeTag()))
    }

    @Test
    fun `classifyFocusDimension keeps unknown and compound labels in genre`() {
        assertEquals(FocusDimension.GENRE, classifyFocusDimension("fantasy".normalizeTag()))
        assertEquals(FocusDimension.GENRE, classifyFocusDimension("webtoon romance".normalizeTag()))
    }
    // KMK <--
}
