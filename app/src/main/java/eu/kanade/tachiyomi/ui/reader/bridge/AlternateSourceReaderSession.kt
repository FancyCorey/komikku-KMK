package eu.kanade.tachiyomi.ui.reader.bridge

import tachiyomi.domain.taste.model.AlternateSourceBridgeKey
import tachiyomi.domain.taste.model.AlternateSourceBridgePolicy
import tachiyomi.domain.taste.model.CrossSourceRecordKey
import java.security.MessageDigest
import java.util.UUID

enum class AlternateSourceReaderRouteRole { PRIMARY, ALTERNATE }

data class AlternateSourceReaderRoute(
    val role: AlternateSourceReaderRouteRole,
    val record: CrossSourceRecordKey,
    val mangaId: Long,
    val chapterUrl: String,
    val chapterId: Long,
    val pageIndex: Int,
)

enum class AlternateSourceReaderTransitionReason {
    GAP_ENTRY,
    CONTINUED_READING,
    USER_CORRECTION,
    MANUAL_RETURN,
    AUTOMATIC_RETURN,
    PROCESS_RECOVERY,
}

data class AlternateSourceReaderSession(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val sessionId: String,
    val bridgeKey: AlternateSourceBridgeKey,
    val primaryResumeRoute: AlternateSourceReaderRoute,
    val currentRoute: AlternateSourceReaderRoute,
    val activeTargetId: String? = null,
    val lastSafeRouteFingerprint: String,
    val previousRouteFingerprint: String? = null,
    val pendingRouteFingerprint: String? = null,
    val transitionCount: Int = 0,
    val failedResolutionCount: Int = 0,
    val lastTransitionReason: AlternateSourceReaderTransitionReason,
    val bridgeUpdatedAt: Long,
    val mappingUpdatedAt: Long,
    val uncertainAlignmentNoticeEmitted: Boolean = false,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_PAGE_INDEX = 1_000_000
        const val MAX_TRANSITIONS = 32
        const val MAX_FAILED_RESOLUTIONS = 2
        private const val SHA_256_HEX_LENGTH = 64
        private val uuidPattern = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
        )
        private val fingerprintPattern = Regex("^[0-9a-f]{$SHA_256_HEX_LENGTH}$")

        fun create(
            bridgeKey: AlternateSourceBridgeKey,
            primaryResumeRoute: AlternateSourceReaderRoute,
            alternateRoute: AlternateSourceReaderRoute,
            activeTargetId: String?,
            bridgeUpdatedAt: Long,
            mappingUpdatedAt: Long,
            sessionId: String = UUID.randomUUID().toString(),
        ): AlternateSourceReaderSession {
            val session = AlternateSourceReaderSession(
                sessionId = sessionId,
                bridgeKey = bridgeKey,
                primaryResumeRoute = primaryResumeRoute,
                currentRoute = alternateRoute,
                activeTargetId = activeTargetId,
                lastSafeRouteFingerprint = AlternateSourceReaderRouteFingerprint.of(alternateRoute),
                previousRouteFingerprint = AlternateSourceReaderRouteFingerprint.of(primaryResumeRoute),
                transitionCount = 1,
                lastTransitionReason = AlternateSourceReaderTransitionReason.GAP_ENTRY,
                bridgeUpdatedAt = bridgeUpdatedAt,
                mappingUpdatedAt = mappingUpdatedAt,
            )
            require(isValid(session))
            return session
        }

        fun createPendingEntry(
            bridgeKey: AlternateSourceBridgeKey,
            primaryResumeRoute: AlternateSourceReaderRoute,
            alternateRoute: AlternateSourceReaderRoute,
            activeTargetId: String,
            bridgeUpdatedAt: Long,
            mappingUpdatedAt: Long,
            sessionId: String = UUID.randomUUID().toString(),
        ): AlternateSourceReaderSession {
            require(primaryResumeRoute.role == AlternateSourceReaderRouteRole.PRIMARY)
            require(alternateRoute.role == AlternateSourceReaderRouteRole.ALTERNATE)
            val session = AlternateSourceReaderSession(
                sessionId = sessionId,
                bridgeKey = bridgeKey,
                primaryResumeRoute = primaryResumeRoute,
                currentRoute = primaryResumeRoute,
                activeTargetId = activeTargetId,
                lastSafeRouteFingerprint = AlternateSourceReaderRouteFingerprint.of(primaryResumeRoute),
                pendingRouteFingerprint = AlternateSourceReaderRouteFingerprint.of(alternateRoute),
                lastTransitionReason = AlternateSourceReaderTransitionReason.GAP_ENTRY,
                bridgeUpdatedAt = bridgeUpdatedAt,
                mappingUpdatedAt = mappingUpdatedAt,
            )
            require(isValid(session))
            return session
        }

        fun isValid(session: AlternateSourceReaderSession): Boolean {
            if (session.schemaVersion != CURRENT_SCHEMA_VERSION || !uuidPattern.matches(session.sessionId)) return false
            if (!AlternateSourceBridgePolicy.isValidKey(session.bridgeKey)) return false
            if (!isValidRoute(session.primaryResumeRoute, session.bridgeKey)) return false
            if (!isValidRoute(session.currentRoute, session.bridgeKey)) return false
            if (session.primaryResumeRoute.role != AlternateSourceReaderRouteRole.PRIMARY) return false
            if (session.activeTargetId != null && !uuidPattern.matches(session.activeTargetId)) return false
            if (session.bridgeUpdatedAt <= 0L || session.mappingUpdatedAt <= 0L) return false
            if (session.transitionCount !in 0..MAX_TRANSITIONS) return false
            if (session.failedResolutionCount !in 0..MAX_FAILED_RESOLUTIONS) return false
            if (!fingerprintPattern.matches(session.lastSafeRouteFingerprint)) return false
            if (session.lastSafeRouteFingerprint != AlternateSourceReaderRouteFingerprint.of(session.currentRoute)) return false
            if (session.previousRouteFingerprint != null && !fingerprintPattern.matches(session.previousRouteFingerprint)) return false
            if (session.pendingRouteFingerprint != null && !fingerprintPattern.matches(session.pendingRouteFingerprint)) return false
            return true
        }

        fun isValidRoute(route: AlternateSourceReaderRoute, bridgeKey: AlternateSourceBridgeKey): Boolean {
            if (route.mangaId <= 0L || route.chapterId <= 0L) return false
            if (route.pageIndex !in 0..MAX_PAGE_INDEX) return false
            if (route.chapterUrl.isBlank() || route.chapterUrl.length > AlternateSourceBridgePolicy.MAX_URL_LENGTH) return false
            val expectedRecord = when (route.role) {
                AlternateSourceReaderRouteRole.PRIMARY -> bridgeKey.primary
                AlternateSourceReaderRouteRole.ALTERNATE -> bridgeKey.alternate
            }
            return route.record == expectedRecord
        }
    }
}

object AlternateSourceReaderRouteFingerprint {
    fun of(route: AlternateSourceReaderRoute): String {
        val payload = listOf(
            route.role.name,
            route.record.source,
            route.record.url,
            route.mangaId,
            route.chapterUrl,
            route.chapterId,
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
