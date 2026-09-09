package eu.kanade.tachiyomi.ui.reader.schedule

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK v0.8.7-fix1 -->
class ReaderScheduleEntitlementTest {

    private fun next(current: ReaderScheduleEntitlement, result: ReaderScheduleResult, graceConsumed: Boolean = false) =
        ReaderScheduleEntitlement.next(current, result, graceConsumed)

    // --- NotStarted (the confirmed defect's exact scenario) ---

    @Test
    fun `a reader opened while ALLOWED starts OpenedWhileAllowed`() {
        assertEquals(ReaderScheduleEntitlement.OpenedWhileAllowed, next(ReaderScheduleEntitlement.NotStarted, ReaderScheduleResult.ALLOWED))
    }

    @Test
    fun `a reader opened while DISABLED starts OpenedWhileAllowed`() {
        assertEquals(ReaderScheduleEntitlement.OpenedWhileAllowed, next(ReaderScheduleEntitlement.NotStarted, ReaderScheduleResult.DISABLED))
    }

    @Test
    fun `a reader opened while RESTRICTED starts OpenedWhileRestricted - no grace, blocked immediately`() {
        val result = next(ReaderScheduleEntitlement.NotStarted, ReaderScheduleResult.RESTRICTED)
        assertEquals(ReaderScheduleEntitlement.OpenedWhileRestricted, result)
        assertEquals(true, result.blocksReading)
    }

    // --- The core fix: OpenedWhileAllowed -> RESTRICTED transition grants grace; OpenedWhileRestricted never does ---

    @Test
    fun `an already-allowed session transitioning to RESTRICTED enters CurrentChapterGrace`() {
        assertEquals(
            ReaderScheduleEntitlement.CurrentChapterGrace,
            next(ReaderScheduleEntitlement.OpenedWhileAllowed, ReaderScheduleResult.RESTRICTED),
        )
    }

    @Test
    fun `OpenedWhileRestricted never transitions to CurrentChapterGrace even if re-evaluated as RESTRICTED again`() {
        // This is exactly the bug scenario: leaving a reader during restricted hours and opening a
        // NEW reader (a fresh instance, but here simulated by feeding OpenedWhileRestricted back in)
        // must never look like a fresh allowance.
        assertEquals(
            ReaderScheduleEntitlement.OpenedWhileRestricted,
            next(ReaderScheduleEntitlement.OpenedWhileRestricted, ReaderScheduleResult.RESTRICTED),
        )
    }

    @Test
    fun `OpenedWhileRestricted has no path back to OpenedWhileAllowed even if the schedule becomes ALLOWED`() {
        // Per the behavior contract's explicit two-path diagram: OpenedWhileRestricted -> Closed only.
        assertEquals(
            ReaderScheduleEntitlement.OpenedWhileRestricted,
            next(ReaderScheduleEntitlement.OpenedWhileRestricted, ReaderScheduleResult.ALLOWED),
        )
    }

    @Test
    fun `OpenedWhileAllowed remains OpenedWhileAllowed across repeated ALLOWED re-evaluations`() {
        assertEquals(
            ReaderScheduleEntitlement.OpenedWhileAllowed,
            next(ReaderScheduleEntitlement.OpenedWhileAllowed, ReaderScheduleResult.ALLOWED),
        )
    }

    // --- CurrentChapterGrace lifecycle ---

    @Test
    fun `CurrentChapterGrace stays in grace until the chapter-grace timer actually expires`() {
        assertEquals(
            ReaderScheduleEntitlement.CurrentChapterGrace,
            next(ReaderScheduleEntitlement.CurrentChapterGrace, ReaderScheduleResult.RESTRICTED, graceConsumed = false),
        )
    }

    @Test
    fun `CurrentChapterGrace does not revert to OpenedWhileAllowed even if the schedule swings back to ALLOWED mid-grace`() {
        assertEquals(
            ReaderScheduleEntitlement.CurrentChapterGrace,
            next(ReaderScheduleEntitlement.CurrentChapterGrace, ReaderScheduleResult.ALLOWED, graceConsumed = false),
        )
    }

    @Test
    fun `CurrentChapterGrace becomes GraceConsumed once the grace timer expires`() {
        val result = next(ReaderScheduleEntitlement.CurrentChapterGrace, ReaderScheduleResult.RESTRICTED, graceConsumed = true)
        assertEquals(ReaderScheduleEntitlement.GraceConsumed, result)
        assertEquals(true, result.blocksReading)
    }

    // --- Terminal states ---

    @Test
    fun `GraceConsumed is terminal for this session regardless of later schedule results`() {
        assertEquals(ReaderScheduleEntitlement.GraceConsumed, next(ReaderScheduleEntitlement.GraceConsumed, ReaderScheduleResult.ALLOWED))
        assertEquals(ReaderScheduleEntitlement.GraceConsumed, next(ReaderScheduleEntitlement.GraceConsumed, ReaderScheduleResult.DISABLED))
    }

    @Test
    fun `Closed is terminal - a later evaluation never revives it`() {
        assertEquals(ReaderScheduleEntitlement.Closed, next(ReaderScheduleEntitlement.Closed, ReaderScheduleResult.ALLOWED))
        assertEquals(ReaderScheduleEntitlement.Closed, next(ReaderScheduleEntitlement.Closed, ReaderScheduleResult.RESTRICTED))
    }

    // --- blocksReading ---

    @Test
    fun `only OpenedWhileRestricted and GraceConsumed block reading`() {
        assertEquals(false, ReaderScheduleEntitlement.NotStarted.blocksReading)
        assertEquals(false, ReaderScheduleEntitlement.OpenedWhileAllowed.blocksReading)
        assertEquals(false, ReaderScheduleEntitlement.CurrentChapterGrace.blocksReading)
        assertEquals(true, ReaderScheduleEntitlement.GraceConsumed.blocksReading)
        assertEquals(true, ReaderScheduleEntitlement.OpenedWhileRestricted.blocksReading)
        assertEquals(false, ReaderScheduleEntitlement.Closed.blocksReading)
    }

    // KMK v0.8.7-fix1 gap-closing pass -->
    // `ReaderViewModel.isChapterNavigationBlockedBySchedule()` — the real enforcement gate checked
    // directly by `init()` (initial/deep-link chapter load), `loadAdjacent()` (manual chapter-dialog
    // selection AND toolbar next/prev, which both funnel through it), and `loadNewChapter()` (natural
    // forward viewer paging into a new chapter) — is exactly `entitlement.blocksReading`. It is not
    // re-implemented as a separate function, so there is nothing additional to unit test beyond
    // `blocksReading` itself (already exhaustively covered above). These tests exist to make that
    // connection explicit and named per real call site, since `ReaderViewModel` itself cannot be
    // constructed in a unit test (20+ Injekt dependencies plus a non-defaultable `SavedStateHandle`,
    // the same class of gap already documented for `RecommendsScreenModel` earlier in this project) —
    // the actual wiring at each call site is verified by code inspection only, not an integration
    // test. Process-death restoration remains outside this pure state-machine test.

    @Test
    fun `a session opened while restricted rejects init()'s initial chapter load, including deep links`() {
        // init() and any deep-link-triggered initial load share the exact same code path in
        // ReaderViewModel — both resolve to OpenedWhileRestricted here.
        assertEquals(true, ReaderScheduleEntitlement.OpenedWhileRestricted.blocksReading)
    }

    @Test
    fun `a session whose grace has been consumed rejects loadAdjacent() - manual dialog selection and toolbar next-prev`() {
        assertEquals(true, ReaderScheduleEntitlement.GraceConsumed.blocksReading)
    }

    @Test
    fun `a session whose grace has been consumed rejects loadNewChapter() - natural forward paging into a new chapter`() {
        // This is the "no extra chapter" case: CurrentChapterGrace itself does NOT block (the
        // currently-open chapter must still be readable to its end), but once it resolves to
        // GraceConsumed, natural forward progression into a NEW chapter is rejected exactly like a
        // manual selection would be.
        assertEquals(false, ReaderScheduleEntitlement.CurrentChapterGrace.blocksReading)
        assertEquals(true, ReaderScheduleEntitlement.GraceConsumed.blocksReading)
    }

    @Test
    fun `an active grace session does not reject navigation until grace is actually consumed`() {
        assertEquals(false, ReaderScheduleEntitlement.CurrentChapterGrace.blocksReading)
    }

    @Test
    fun `a normal allowed session never rejects navigation`() {
        assertEquals(false, ReaderScheduleEntitlement.OpenedWhileAllowed.blocksReading)
    }
    // KMK <--
}
// KMK <--
