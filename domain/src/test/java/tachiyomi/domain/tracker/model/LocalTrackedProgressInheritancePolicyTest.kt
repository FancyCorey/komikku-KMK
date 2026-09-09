package tachiyomi.domain.tracker.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalTrackedProgressInheritancePolicyTest {

    @Test
    fun `older recognized chapter cannot advance aggregate progress`() {
        val progressed = target.copy(lastChapterNumber = 12.0, lastProgressAt = 20L)

        assertFalse(LocalTrackedProgressInheritancePolicy.acceptsAggregateProgress(progressed, 11.0, 30L))
    }

    @Test
    fun `newer chapter can advance aggregate progress`() {
        val progressed = target.copy(lastChapterNumber = 12.0, lastProgressAt = 20L)

        assertTrue(LocalTrackedProgressInheritancePolicy.acceptsAggregateProgress(progressed, 13.0, 30L))
    }

    private val target = LocalTrackedWork(
        id = "target",
        title = "Example",
        normalizedTitle = "example",
        status = LocalTrackedWorkStatus.READING,
        lastChapterSource = null,
        lastChapterNumber = null,
        lastChapterUrl = null,
        lastChapterLabel = null,
        lastProgressAt = null,
        createdAt = 1_000,
        updatedAt = 1_000,
    )

    private val update = MappedLocalTrackedProgress(
        targetSource = 2,
        targetUrl = "/chapter-4",
        targetChapterUrl = "/chapter-4/page-1",
        targetLabel = "Chapter 4",
        chapterNumber = 4.0,
        progressAt = 2_000,
    )

    @Test
    fun `only enabled confirmed non opted out mapped updates apply`() {
        assertEquals(
            LocalTrackedProgressInheritanceDecision.APPLY,
            LocalTrackedProgressInheritancePolicy.decide(true, true, false, target, update),
        )
        assertEquals(2L, LocalTrackedProgressInheritancePolicy.apply(target, update)!!.lastChapterSource)
        assertEquals(4.0, LocalTrackedProgressInheritancePolicy.apply(target, update)!!.lastChapterNumber)
    }

    @Test
    fun `disabled unauthorized opted out and unmapped updates are rejected`() {
        assertEquals(LocalTrackedProgressInheritanceDecision.DISABLED, decision(enabled = false))
        assertEquals(LocalTrackedProgressInheritanceDecision.UNAUTHORIZED_GROUP, decision(confirmed = false))
        assertEquals(LocalTrackedProgressInheritanceDecision.OPTED_OUT, decision(optedOut = true))
        assertEquals(LocalTrackedProgressInheritanceDecision.UNMAPPED, decision(update = null))
    }

    @Test
    fun `older and equal chapter updates never regress or rewrite target`() {
        val progressed = LocalTrackedProgressInheritancePolicy.apply(target, update)!!
        val older = update.copy(chapterNumber = 3.0, progressAt = 3_000)
        val equal = update.copy(progressAt = 3_000)

        assertEquals(
            LocalTrackedProgressInheritanceDecision.OLDER_THAN_TARGET,
            LocalTrackedProgressInheritancePolicy.decide(true, true, false, progressed, older),
        )
        assertEquals(
            LocalTrackedProgressInheritanceDecision.ALREADY_RECORDED,
            LocalTrackedProgressInheritancePolicy.decide(true, true, false, progressed, equal),
        )
        assertNull(LocalTrackedProgressInheritancePolicy.apply(progressed, older))
        assertNull(LocalTrackedProgressInheritancePolicy.apply(progressed, equal))
    }

    @Test
    fun `source progress can advance when aggregate row is already current`() {
        val existing = LocalTrackedWorkSourceProgress(
            workId = "target",
            source = 2,
            url = "/source",
            chapterNumber = 3.0,
            chapterUrl = "/chapter-3",
            chapterLabel = "Chapter 3",
            progressAt = 2_000,
            updatedAt = 2_000,
        )
        val newer = update.copy(chapterNumber = 4.0, progressAt = 3_000)

        assertTrue(LocalTrackedProgressInheritancePolicy.acceptsSourceProgress(existing, newer))
        assertFalse(LocalTrackedProgressInheritancePolicy.acceptsSourceProgress(existing, update.copy(chapterNumber = 2.0)))
    }

    private fun decision(
        enabled: Boolean = true,
        confirmed: Boolean = true,
        optedOut: Boolean = false,
        update: MappedLocalTrackedProgress? = this.update,
    ) = LocalTrackedProgressInheritancePolicy.decide(enabled, confirmed, optedOut, target, update)
}
