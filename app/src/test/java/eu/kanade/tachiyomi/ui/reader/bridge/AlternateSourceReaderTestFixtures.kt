package eu.kanade.tachiyomi.ui.reader.bridge

import tachiyomi.domain.taste.model.AlternateSourceBridgeKey
import tachiyomi.domain.taste.model.CrossSourceRecordKey

internal const val READER_SESSION_ID = "123e4567-e89b-42d3-a456-426614174000"
internal const val READER_TARGET_ID = "223e4567-e89b-42d3-a456-426614174000"

internal fun readerBridgeKey() = AlternateSourceBridgeKey(
    primary = CrossSourceRecordKey(1L, "/manga/primary"),
    alternate = CrossSourceRecordKey(2L, "/manga/alternate"),
)

internal fun readerRoute(
    role: AlternateSourceReaderRouteRole,
    chapterUrl: String = if (role == AlternateSourceReaderRouteRole.PRIMARY) "/chapter/primary" else "/chapter/alternate",
    chapterId: Long = if (role == AlternateSourceReaderRouteRole.PRIMARY) 11L else 22L,
    pageIndex: Int = 0,
) = AlternateSourceReaderRoute(
    role = role,
    record = when (role) {
        AlternateSourceReaderRouteRole.PRIMARY -> readerBridgeKey().primary
        AlternateSourceReaderRouteRole.ALTERNATE -> readerBridgeKey().alternate
    },
    mangaId = if (role == AlternateSourceReaderRouteRole.PRIMARY) 1_001L else 2_002L,
    chapterUrl = chapterUrl,
    chapterId = chapterId,
    pageIndex = pageIndex,
)

internal fun readerSession(
    currentRoute: AlternateSourceReaderRoute = readerRoute(AlternateSourceReaderRouteRole.ALTERNATE),
    previousRouteFingerprint: String? = AlternateSourceReaderRouteFingerprint.of(
        readerRoute(AlternateSourceReaderRouteRole.PRIMARY),
    ),
    pendingRouteFingerprint: String? = null,
    transitionCount: Int = 1,
    failedResolutionCount: Int = 0,
) = AlternateSourceReaderSession(
    sessionId = READER_SESSION_ID,
    bridgeKey = readerBridgeKey(),
    primaryResumeRoute = readerRoute(AlternateSourceReaderRouteRole.PRIMARY),
    currentRoute = currentRoute,
    activeTargetId = READER_TARGET_ID,
    lastSafeRouteFingerprint = AlternateSourceReaderRouteFingerprint.of(currentRoute),
    previousRouteFingerprint = previousRouteFingerprint,
    pendingRouteFingerprint = pendingRouteFingerprint,
    transitionCount = transitionCount,
    failedResolutionCount = failedResolutionCount,
    lastTransitionReason = AlternateSourceReaderTransitionReason.GAP_ENTRY,
    bridgeUpdatedAt = 2_000L,
    mappingUpdatedAt = 2_100L,
)
