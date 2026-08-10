package eu.kanade.presentation.more.settings.screen.about

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [KmkRecsWhatsNewPolicy] (v0.8.1-fix2).
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.KmkRecsWhatsNewPolicyTest"
 */
class KmkRecsWhatsNewPolicyTest {

    // --- hasUnseenChangelog ---

    @Test
    fun `current version greater than last-seen has unseen changelog`() {
        assertTrue(KmkRecsWhatsNewPolicy.hasUnseenChangelog(currentVersionCode = 751, lastSeenVersionCode = 750))
    }

    @Test
    fun `current version equal to last-seen has no unseen changelog`() {
        assertFalse(KmkRecsWhatsNewPolicy.hasUnseenChangelog(currentVersionCode = 751, lastSeenVersionCode = 751))
    }

    @Test
    fun `current version lower than last-seen has no unseen changelog`() {
        // Defensive case (e.g. a downgrade) — must not show either.
        assertFalse(KmkRecsWhatsNewPolicy.hasUnseenChangelog(currentVersionCode = 750, lastSeenVersionCode = 751))
    }

    @Test
    fun `first launch with default last-seen of zero has unseen changelog`() {
        assertTrue(KmkRecsWhatsNewPolicy.hasUnseenChangelog(currentVersionCode = 751, lastSeenVersionCode = 0))
    }

    // --- shouldShowKmkDialog: sequencing with the normal Komikku changelog ---

    @Test
    fun `KMK dialog stays pending while the normal Komikku changelog is showing`() {
        val result = KmkRecsWhatsNewPolicy.shouldShowKmkDialog(
            komikkuChangelogShowing = true,
            hasUnseenKmkChangelog = true,
        )
        assertFalse(result)
    }

    @Test
    fun `KMK dialog shows once the normal Komikku changelog is no longer showing`() {
        val result = KmkRecsWhatsNewPolicy.shouldShowKmkDialog(
            komikkuChangelogShowing = false,
            hasUnseenKmkChangelog = true,
        )
        assertTrue(result)
    }

    @Test
    fun `KMK dialog does not show when there is no unseen content, regardless of Komikku dialog state`() {
        assertFalse(KmkRecsWhatsNewPolicy.shouldShowKmkDialog(komikkuChangelogShowing = false, hasUnseenKmkChangelog = false))
        assertFalse(KmkRecsWhatsNewPolicy.shouldShowKmkDialog(komikkuChangelogShowing = true, hasUnseenKmkChangelog = false))
    }

    // --- seenVersionCodeOnAcknowledge ---

    @Test
    fun `acknowledging marks exactly the current version code as seen`() {
        assertEquals(751, KmkRecsWhatsNewPolicy.seenVersionCodeOnAcknowledge(751))
    }
}
