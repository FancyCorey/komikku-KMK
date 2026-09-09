package exh.recs.matching

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionValue
import tachiyomi.domain.taste.model.CrossSourceRecordKey

class CrossSourceMatchSelectionPolicyTest {
    private val origin = MangaIdentityKey(1, "/origin")
    private val confirmed = MangaIdentityKey(2, "/confirmed")
    private val rejected = MangaIdentityKey(3, "/rejected")
    private val unknown = MangaIdentityKey(4, "/unknown")

    @Test
    fun `enabled preference selects only explicit current confirmations`() {
        assertEquals(
            setOf(confirmed),
            CrossSourceMatchSelectionPolicy.automaticSelections(
                candidates = setOf(origin, confirmed, rejected, unknown),
                decisions = mapOf(
                    confirmed to decision(confirmed, CrossSourceIdentityDecisionValue.USER_CONFIRMED),
                    rejected to decision(rejected, CrossSourceIdentityDecisionValue.USER_REJECTED),
                ),
                enabled = true,
                origin = origin,
                manuallyDeselected = emptySet(),
            ),
        )
    }

    @Test
    fun `disabled preference and manual deselection never preselect`() {
        val decisions = mapOf(confirmed to decision(confirmed, CrossSourceIdentityDecisionValue.USER_CONFIRMED))
        assertEquals(
            emptySet<MangaIdentityKey>(),
            CrossSourceMatchSelectionPolicy.automaticSelections(setOf(confirmed), decisions, false, origin, emptySet()),
        )
        assertEquals(
            emptySet<MangaIdentityKey>(),
            CrossSourceMatchSelectionPolicy.automaticSelections(setOf(confirmed), decisions, true, origin, setOf(confirmed)),
        )
    }

    @Test
    fun `exact name mode selects only exact titles and preserves explicit confirmations`() {
        val originManga = manga(1L, "/origin", "The Story")
        val exact = manga(2L, "/exact", " the-story ")
        val different = manga(3L, "/different", "The Story Extra Edition")
        val confirmedDifferent = manga(4L, "/confirmed", "A Different Title")

        assertEquals(
            setOf(MangaIdentityKey(2L, "/exact"), MangaIdentityKey(4L, "/confirmed")),
            CrossSourceMatchSelectionPolicy.automaticSelections(
                candidates = listOf(originManga, exact, different, confirmedDifferent),
                origin = originManga,
                preselectionMode = SameMangaPreselectionMode.EXACT_NAME,
                decisions = mapOf(
                    MangaIdentityKey(4L, "/confirmed") to decision(
                        MangaIdentityKey(4L, "/confirmed"),
                        CrossSourceIdentityDecisionValue.USER_CONFIRMED,
                    ),
                ),
                manuallyDeselected = emptySet(),
            ),
        )
    }

    @Test
    fun `manual deselection wins over exact title mode and origin is excluded`() {
        val originManga = manga(1L, "/origin", "The Story")
        val exact = manga(2L, "/exact", "THE STORY")

        assertEquals(
            emptySet<MangaIdentityKey>(),
            CrossSourceMatchSelectionPolicy.automaticSelections(
                candidates = listOf(originManga, exact),
                origin = originManga,
                preselectionMode = SameMangaPreselectionMode.EXACT_NAME,
                decisions = emptyMap(),
                manuallyDeselected = setOf(MangaIdentityKey(2L, "/exact")),
            ),
        )
    }

    private fun manga(source: Long, url: String, title: String): Manga = Manga.create().copy(
        source = source,
        url = url,
        ogTitle = title,
    )

    private fun decision(key: MangaIdentityKey, value: CrossSourceIdentityDecisionValue) =
        CrossSourceIdentityDecisionPolicy.userDecision(
            pair = CrossSourceIdentityDecisionPolicy.canonicalPair(
                CrossSourceRecordKey(origin.source, origin.url),
                CrossSourceRecordKey(key.source, key.url),
            ),
            value = value,
            previous = null,
            timestamp = 1_000,
        )
}
