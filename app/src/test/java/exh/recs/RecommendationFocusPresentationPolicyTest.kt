package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

// KMK v0.8.21-fix5: R4/AUG-14 completion -- direct product correction (2026-08-25). Covers the
// presentation-layer glue between RecommendationFocusPolicy and the UI: the filtered view every
// section shows by default, the untouched "broader" view Show Broader Results reveals, and that
// Clear Focus (an inactive FocusCriteria) restores the exact original result with no residual
// filtering.
class RecommendationFocusPresentationPolicyTest {

    private fun recommendation(id: Long, genres: List<String>): PersonalRecommendation =
        PersonalRecommendation(
            manga = Manga.create().copy(id = id, source = 1L, url = "/m/$id", ogGenre = genres),
            score = 0.0,
            matchedGroups = emptyList(),
        )

    private fun criteria(
        include: Set<String> = emptySet(),
        exclude: Set<String> = emptySet(),
        matchAll: Boolean = true,
    ) = RecommendationFocusPolicy.FocusCriteria(include, exclude, matchAll)

    @Test
    fun `a null result stays null in both filtered and broader, and is not marked filtered`() {
        val presentation = RecommendationFocusPresentationPolicy.apply(null, criteria(include = setOf("action")))

        assertEquals(null, presentation.filtered)
        assertEquals(null, presentation.broader)
        assertFalse(presentation.isFiltered)
    }

    @Test
    fun `an inactive criteria (Clear Focus) is a pure pass-through -- exact original result, not filtered`() {
        val original = PersonalRecommendationResult.Success(listOf(recommendation(1, listOf("Action")), recommendation(2, listOf("Romance"))))

        val presentation = RecommendationFocusPresentationPolicy.apply(original, criteria())

        assertEquals(original, presentation.filtered)
        assertEquals(original, presentation.broader)
        assertFalse(presentation.isFiltered)
    }

    @Test
    fun `Loading and Error results are passed through unchanged even with an active criteria`() {
        val loadingPresentation = RecommendationFocusPresentationPolicy.apply(
            PersonalRecommendationResult.Loading,
            criteria(include = setOf("action")),
        )
        assertEquals(PersonalRecommendationResult.Loading, loadingPresentation.filtered)
        assertFalse(loadingPresentation.isFiltered)

        val error = PersonalRecommendationResult.Error(RuntimeException("boom"))
        val errorPresentation = RecommendationFocusPresentationPolicy.apply(error, criteria(include = setOf("action")))
        assertEquals(error, errorPresentation.filtered)
        assertFalse(errorPresentation.isFiltered)
    }

    @Test
    fun `an active criteria genuinely filters -- filtered omits non-matching candidates, broader keeps everyone`() {
        val action = recommendation(1, listOf("Action"))
        val romance = recommendation(2, listOf("Romance"))
        val original = PersonalRecommendationResult.Success(listOf(action, romance))

        val presentation = RecommendationFocusPresentationPolicy.apply(original, criteria(include = setOf("action")))

        assertTrue(presentation.isFiltered)
        assertEquals(listOf(action), (presentation.filtered as PersonalRecommendationResult.Success).result)
        assertEquals(listOf(action, romance), (presentation.broader as PersonalRecommendationResult.Success).result)
    }

    @Test
    fun `zero matches anywhere -- filtered is an honest empty Success, broader still carries the full original set`() {
        val original = PersonalRecommendationResult.Success(listOf(recommendation(1, listOf("Romance"))))

        val presentation = RecommendationFocusPresentationPolicy.apply(original, criteria(include = setOf("action")))

        val filtered = presentation.filtered as PersonalRecommendationResult.Success
        assertTrue(filtered.isEmpty, "an empty Success (not null, not Loading) is the honest empty state -- never a spinner")
        assertTrue(presentation.isFiltered)
        val broader = presentation.broader as PersonalRecommendationResult.Success
        assertFalse(broader.isEmpty)
        assertEquals(1, broader.result.size)
    }

    @Test
    fun `broader never mutates the criteria object itself -- computing it twice yields the same filtered result`() {
        val original = PersonalRecommendationResult.Success(listOf(recommendation(1, listOf("Romance"))))
        val activeCriteria = criteria(include = setOf("action"))

        val first = RecommendationFocusPresentationPolicy.apply(original, activeCriteria)
        // Reading .broader (simulating "Show Broader Results" being toggled) must not have any
        // side effect on activeCriteria -- re-applying it must produce an identical filtered view.
        @Suppress("UNUSED_EXPRESSION")
        first.broader
        val second = RecommendationFocusPresentationPolicy.apply(original, activeCriteria)

        assertEquals(first.filtered, second.filtered)
        assertEquals(activeCriteria, criteria(include = setOf("action")), "criteria value itself is unchanged")
    }
}
