package eu.kanade.tachiyomi.ui.reader.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlternateSourceReaderRoutePolicyTest {

    @Test
    fun `prepare rejects same route immediate automatic reversal and transition exhaustion`() {
        val session = readerSession()
        assertEquals(
            AlternateSourceReaderRoutePolicy.PrepareResult.SameRoute,
            AlternateSourceReaderRoutePolicy.prepare(session, session.currentRoute, explicitManualReturn = false),
        )
        assertEquals(
            AlternateSourceReaderRoutePolicy.PrepareResult.ImmediateAutomaticReversal,
            AlternateSourceReaderRoutePolicy.prepare(
                session,
                session.primaryResumeRoute,
                explicitManualReturn = false,
            ),
        )
        assertEquals(
            AlternateSourceReaderRoutePolicy.PrepareResult.TransitionLimitReached,
            AlternateSourceReaderRoutePolicy.prepare(
                session.copy(transitionCount = AlternateSourceReaderSession.MAX_TRANSITIONS),
                session.primaryResumeRoute,
                explicitManualReturn = true,
            ),
        )
    }

    @Test
    fun `manual return may reverse and commits only its exact prepared route`() {
        val session = readerSession()
        val prepared = AlternateSourceReaderRoutePolicy.prepare(
            session,
            session.primaryResumeRoute,
            explicitManualReturn = true,
        ) as AlternateSourceReaderRoutePolicy.PrepareResult.Allowed
        assertEquals(
            AlternateSourceReaderRouteFingerprint.of(session.primaryResumeRoute),
            prepared.session.pendingRouteFingerprint,
        )
        assertEquals(session.currentRoute, prepared.session.currentRoute)

        assertEquals(
            AlternateSourceReaderRoutePolicy.CommitResult.PendingRouteMismatch,
            AlternateSourceReaderRoutePolicy.commit(
                prepared.session,
                readerRoute(AlternateSourceReaderRouteRole.ALTERNATE, "/chapter/wrong", 23L),
                AlternateSourceReaderTransitionReason.MANUAL_RETURN,
            ),
        )

        val committed = AlternateSourceReaderRoutePolicy.commit(
            prepared.session,
            session.primaryResumeRoute,
            AlternateSourceReaderTransitionReason.MANUAL_RETURN,
        ) as AlternateSourceReaderRoutePolicy.CommitResult.Applied
        assertEquals(session.primaryResumeRoute, committed.session.currentRoute)
        assertEquals(session.lastSafeRouteFingerprint, committed.session.previousRouteFingerprint)
        assertEquals(null, committed.session.pendingRouteFingerprint)
        assertEquals(session.transitionCount + 1, committed.session.transitionCount)
    }

    @Test
    fun `failed resolution preserves readable route and ends automation at the bound`() {
        val session = readerSession(
            pendingRouteFingerprint = AlternateSourceReaderRouteFingerprint.of(
                readerRoute(AlternateSourceReaderRouteRole.PRIMARY),
            ),
        )
        val first = AlternateSourceReaderRoutePolicy.fail(session)
            as AlternateSourceReaderRoutePolicy.FailureResult.RetryAvailable
        assertEquals(session.currentRoute, first.session.currentRoute)
        assertEquals(null, first.session.pendingRouteFingerprint)
        assertEquals(1, first.session.failedResolutionCount)

        val second = AlternateSourceReaderRoutePolicy.fail(first.session)
            as AlternateSourceReaderRoutePolicy.FailureResult.AutomationEnded
        assertEquals(AlternateSourceReaderSession.MAX_FAILED_RESOLUTIONS, second.session.failedResolutionCount)
        assertTrue(AlternateSourceReaderSession.isValid(second.session))

        val repeated = AlternateSourceReaderRoutePolicy.fail(second.session)
            as AlternateSourceReaderRoutePolicy.FailureResult.AutomationEnded
        assertEquals(AlternateSourceReaderSession.MAX_FAILED_RESOLUTIONS, repeated.session.failedResolutionCount)
    }

    @Test
    fun `continued alternate reading updates exact route without consuming source transition budget`() {
        val session = readerSession(transitionCount = 7)
        val next = readerRoute(
            AlternateSourceReaderRouteRole.ALTERNATE,
            chapterUrl = "/chapter/alternate-next",
            chapterId = 23L,
            pageIndex = 4,
        )

        val result = AlternateSourceReaderRoutePolicy.continueAlternate(session, next)
            as AlternateSourceReaderRoutePolicy.ContinueResult.Applied

        assertEquals(next, result.session.currentRoute)
        assertEquals(7, result.session.transitionCount)
        assertEquals(session.previousRouteFingerprint, result.session.previousRouteFingerprint)
        assertEquals(AlternateSourceReaderTransitionReason.CONTINUED_READING, result.session.lastTransitionReason)
    }

    @Test
    fun `continued alternate reading rejects primary and different manga routes`() {
        val session = readerSession()
        assertEquals(
            AlternateSourceReaderRoutePolicy.ContinueResult.WrongRole,
            AlternateSourceReaderRoutePolicy.continueAlternate(
                session,
                readerRoute(AlternateSourceReaderRouteRole.PRIMARY),
            ),
        )
        assertEquals(
            AlternateSourceReaderRoutePolicy.ContinueResult.IdentityMismatch,
            AlternateSourceReaderRoutePolicy.continueAlternate(
                session,
                readerRoute(AlternateSourceReaderRouteRole.ALTERNATE).copy(mangaId = 9_999L),
            ),
        )
    }
}
