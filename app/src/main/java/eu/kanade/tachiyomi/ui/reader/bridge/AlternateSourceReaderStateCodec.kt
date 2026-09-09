package eu.kanade.tachiyomi.ui.reader.bridge

import tachiyomi.domain.taste.model.AlternateSourceBridgeKey
import tachiyomi.domain.taste.model.CrossSourceRecordKey

object AlternateSourceReaderStateCodec {

    const val KEY_PREFIX = "alternate_source_reader_"
    const val KEY_SCHEMA_VERSION = "${KEY_PREFIX}schema_version"
    const val KEY_SESSION_ID = "${KEY_PREFIX}session_id"
    const val KEY_PRIMARY_SOURCE = "${KEY_PREFIX}primary_source"
    const val KEY_PRIMARY_URL = "${KEY_PREFIX}primary_url"
    const val KEY_ALTERNATE_SOURCE = "${KEY_PREFIX}alternate_source"
    const val KEY_ALTERNATE_URL = "${KEY_PREFIX}alternate_url"
    const val KEY_PRIMARY_MANGA_ID = "${KEY_PREFIX}primary_manga_id"
    const val KEY_PRIMARY_CHAPTER_URL = "${KEY_PREFIX}primary_chapter_url"
    const val KEY_PRIMARY_CHAPTER_ID = "${KEY_PREFIX}primary_chapter_id"
    const val KEY_PRIMARY_PAGE_INDEX = "${KEY_PREFIX}primary_page_index"
    const val KEY_CURRENT_ROLE = "${KEY_PREFIX}current_role"
    const val KEY_CURRENT_MANGA_ID = "${KEY_PREFIX}current_manga_id"
    const val KEY_CURRENT_CHAPTER_URL = "${KEY_PREFIX}current_chapter_url"
    const val KEY_CURRENT_CHAPTER_ID = "${KEY_PREFIX}current_chapter_id"
    const val KEY_CURRENT_PAGE_INDEX = "${KEY_PREFIX}current_page_index"
    const val KEY_ACTIVE_TARGET_ID = "${KEY_PREFIX}active_target_id"
    const val KEY_LAST_SAFE_FINGERPRINT = "${KEY_PREFIX}last_safe_fingerprint"
    const val KEY_PREVIOUS_FINGERPRINT = "${KEY_PREFIX}previous_fingerprint"
    const val KEY_PENDING_FINGERPRINT = "${KEY_PREFIX}pending_fingerprint"
    const val KEY_TRANSITION_COUNT = "${KEY_PREFIX}transition_count"
    const val KEY_FAILED_RESOLUTION_COUNT = "${KEY_PREFIX}failed_resolution_count"
    const val KEY_LAST_TRANSITION_REASON = "${KEY_PREFIX}last_transition_reason"
    const val KEY_BRIDGE_UPDATED_AT = "${KEY_PREFIX}bridge_updated_at"
    const val KEY_MAPPING_UPDATED_AT = "${KEY_PREFIX}mapping_updated_at"
    const val KEY_NOTICE_EMITTED = "${KEY_PREFIX}notice_emitted"

    val ALL_KEYS = listOf(
        KEY_SCHEMA_VERSION,
        KEY_SESSION_ID,
        KEY_PRIMARY_SOURCE,
        KEY_PRIMARY_URL,
        KEY_ALTERNATE_SOURCE,
        KEY_ALTERNATE_URL,
        KEY_PRIMARY_MANGA_ID,
        KEY_PRIMARY_CHAPTER_URL,
        KEY_PRIMARY_CHAPTER_ID,
        KEY_PRIMARY_PAGE_INDEX,
        KEY_CURRENT_ROLE,
        KEY_CURRENT_MANGA_ID,
        KEY_CURRENT_CHAPTER_URL,
        KEY_CURRENT_CHAPTER_ID,
        KEY_CURRENT_PAGE_INDEX,
        KEY_ACTIVE_TARGET_ID,
        KEY_LAST_SAFE_FINGERPRINT,
        KEY_PREVIOUS_FINGERPRINT,
        KEY_PENDING_FINGERPRINT,
        KEY_TRANSITION_COUNT,
        KEY_FAILED_RESOLUTION_COUNT,
        KEY_LAST_TRANSITION_REASON,
        KEY_BRIDGE_UPDATED_AT,
        KEY_MAPPING_UPDATED_AT,
        KEY_NOTICE_EMITTED,
    )

    data class Encoded(
        val schemaVersion: Int,
        val sessionId: String,
        val primarySource: Long,
        val primaryUrl: String,
        val alternateSource: Long,
        val alternateUrl: String,
        val primaryMangaId: Long,
        val primaryChapterUrl: String,
        val primaryChapterId: Long,
        val primaryPageIndex: Int,
        val currentRole: String,
        val currentMangaId: Long,
        val currentChapterUrl: String,
        val currentChapterId: Long,
        val currentPageIndex: Int,
        val activeTargetId: String?,
        val lastSafeRouteFingerprint: String,
        val previousRouteFingerprint: String?,
        val pendingRouteFingerprint: String?,
        val transitionCount: Int,
        val failedResolutionCount: Int,
        val lastTransitionReason: String,
        val bridgeUpdatedAt: Long,
        val mappingUpdatedAt: Long,
        val uncertainAlignmentNoticeEmitted: Boolean,
    )

    sealed interface DecodeResult {
        data class Valid(val session: AlternateSourceReaderSession) : DecodeResult
        data object Invalid : DecodeResult
    }

    fun encode(session: AlternateSourceReaderSession): Encoded {
        require(AlternateSourceReaderSession.isValid(session))
        return Encoded(
            schemaVersion = session.schemaVersion,
            sessionId = session.sessionId,
            primarySource = session.bridgeKey.primary.source,
            primaryUrl = session.bridgeKey.primary.url,
            alternateSource = session.bridgeKey.alternate.source,
            alternateUrl = session.bridgeKey.alternate.url,
            primaryMangaId = session.primaryResumeRoute.mangaId,
            primaryChapterUrl = session.primaryResumeRoute.chapterUrl,
            primaryChapterId = session.primaryResumeRoute.chapterId,
            primaryPageIndex = session.primaryResumeRoute.pageIndex,
            currentRole = session.currentRoute.role.name,
            currentMangaId = session.currentRoute.mangaId,
            currentChapterUrl = session.currentRoute.chapterUrl,
            currentChapterId = session.currentRoute.chapterId,
            currentPageIndex = session.currentRoute.pageIndex,
            activeTargetId = session.activeTargetId,
            lastSafeRouteFingerprint = session.lastSafeRouteFingerprint,
            previousRouteFingerprint = session.previousRouteFingerprint,
            pendingRouteFingerprint = session.pendingRouteFingerprint,
            transitionCount = session.transitionCount,
            failedResolutionCount = session.failedResolutionCount,
            lastTransitionReason = session.lastTransitionReason.name,
            bridgeUpdatedAt = session.bridgeUpdatedAt,
            mappingUpdatedAt = session.mappingUpdatedAt,
            uncertainAlignmentNoticeEmitted = session.uncertainAlignmentNoticeEmitted,
        )
    }

    fun write(session: AlternateSourceReaderSession, set: (String, Any?) -> Unit) {
        val value = encode(session)
        set(KEY_SCHEMA_VERSION, value.schemaVersion)
        set(KEY_SESSION_ID, value.sessionId)
        set(KEY_PRIMARY_SOURCE, value.primarySource)
        set(KEY_PRIMARY_URL, value.primaryUrl)
        set(KEY_ALTERNATE_SOURCE, value.alternateSource)
        set(KEY_ALTERNATE_URL, value.alternateUrl)
        set(KEY_PRIMARY_MANGA_ID, value.primaryMangaId)
        set(KEY_PRIMARY_CHAPTER_URL, value.primaryChapterUrl)
        set(KEY_PRIMARY_CHAPTER_ID, value.primaryChapterId)
        set(KEY_PRIMARY_PAGE_INDEX, value.primaryPageIndex)
        set(KEY_CURRENT_ROLE, value.currentRole)
        set(KEY_CURRENT_MANGA_ID, value.currentMangaId)
        set(KEY_CURRENT_CHAPTER_URL, value.currentChapterUrl)
        set(KEY_CURRENT_CHAPTER_ID, value.currentChapterId)
        set(KEY_CURRENT_PAGE_INDEX, value.currentPageIndex)
        set(KEY_ACTIVE_TARGET_ID, value.activeTargetId)
        set(KEY_LAST_SAFE_FINGERPRINT, value.lastSafeRouteFingerprint)
        set(KEY_PREVIOUS_FINGERPRINT, value.previousRouteFingerprint)
        set(KEY_PENDING_FINGERPRINT, value.pendingRouteFingerprint)
        set(KEY_TRANSITION_COUNT, value.transitionCount)
        set(KEY_FAILED_RESOLUTION_COUNT, value.failedResolutionCount)
        set(KEY_LAST_TRANSITION_REASON, value.lastTransitionReason)
        set(KEY_BRIDGE_UPDATED_AT, value.bridgeUpdatedAt)
        set(KEY_MAPPING_UPDATED_AT, value.mappingUpdatedAt)
        set(KEY_NOTICE_EMITTED, value.uncertainAlignmentNoticeEmitted)
    }

    fun read(get: (String) -> Any?): DecodeResult {
        val activeTargetId = get(KEY_ACTIVE_TARGET_ID)
        val previousFingerprint = get(KEY_PREVIOUS_FINGERPRINT)
        val pendingFingerprint = get(KEY_PENDING_FINGERPRINT)
        if (activeTargetId != null && activeTargetId !is String) return DecodeResult.Invalid
        if (previousFingerprint != null && previousFingerprint !is String) return DecodeResult.Invalid
        if (pendingFingerprint != null && pendingFingerprint !is String) return DecodeResult.Invalid
        return decode(
            Encoded(
                schemaVersion = get(KEY_SCHEMA_VERSION) as? Int ?: return DecodeResult.Invalid,
                sessionId = get(KEY_SESSION_ID) as? String ?: return DecodeResult.Invalid,
                primarySource = get(KEY_PRIMARY_SOURCE) as? Long ?: return DecodeResult.Invalid,
                primaryUrl = get(KEY_PRIMARY_URL) as? String ?: return DecodeResult.Invalid,
                alternateSource = get(KEY_ALTERNATE_SOURCE) as? Long ?: return DecodeResult.Invalid,
                alternateUrl = get(KEY_ALTERNATE_URL) as? String ?: return DecodeResult.Invalid,
                primaryMangaId = get(KEY_PRIMARY_MANGA_ID) as? Long ?: return DecodeResult.Invalid,
                primaryChapterUrl = get(KEY_PRIMARY_CHAPTER_URL) as? String ?: return DecodeResult.Invalid,
                primaryChapterId = get(KEY_PRIMARY_CHAPTER_ID) as? Long ?: return DecodeResult.Invalid,
                primaryPageIndex = get(KEY_PRIMARY_PAGE_INDEX) as? Int ?: return DecodeResult.Invalid,
                currentRole = get(KEY_CURRENT_ROLE) as? String ?: return DecodeResult.Invalid,
                currentMangaId = get(KEY_CURRENT_MANGA_ID) as? Long ?: return DecodeResult.Invalid,
                currentChapterUrl = get(KEY_CURRENT_CHAPTER_URL) as? String ?: return DecodeResult.Invalid,
                currentChapterId = get(KEY_CURRENT_CHAPTER_ID) as? Long ?: return DecodeResult.Invalid,
                currentPageIndex = get(KEY_CURRENT_PAGE_INDEX) as? Int ?: return DecodeResult.Invalid,
                activeTargetId = activeTargetId,
                lastSafeRouteFingerprint = get(KEY_LAST_SAFE_FINGERPRINT) as? String ?: return DecodeResult.Invalid,
                previousRouteFingerprint = previousFingerprint,
                pendingRouteFingerprint = pendingFingerprint,
                transitionCount = get(KEY_TRANSITION_COUNT) as? Int ?: return DecodeResult.Invalid,
                failedResolutionCount = get(KEY_FAILED_RESOLUTION_COUNT) as? Int ?: return DecodeResult.Invalid,
                lastTransitionReason = get(KEY_LAST_TRANSITION_REASON) as? String ?: return DecodeResult.Invalid,
                bridgeUpdatedAt = get(KEY_BRIDGE_UPDATED_AT) as? Long ?: return DecodeResult.Invalid,
                mappingUpdatedAt = get(KEY_MAPPING_UPDATED_AT) as? Long ?: return DecodeResult.Invalid,
                uncertainAlignmentNoticeEmitted = get(KEY_NOTICE_EMITTED) as? Boolean ?: return DecodeResult.Invalid,
            ),
        )
    }

    fun clear(remove: (String) -> Unit) = ALL_KEYS.forEach(remove)

    fun decode(encoded: Encoded?): DecodeResult {
        if (encoded == null) return DecodeResult.Invalid
        return try {
            val key = AlternateSourceBridgeKey(
                primary = CrossSourceRecordKey(encoded.primarySource, encoded.primaryUrl),
                alternate = CrossSourceRecordKey(encoded.alternateSource, encoded.alternateUrl),
            )
            val session = AlternateSourceReaderSession(
                schemaVersion = encoded.schemaVersion,
                sessionId = encoded.sessionId,
                bridgeKey = key,
                primaryResumeRoute = AlternateSourceReaderRoute(
                    role = AlternateSourceReaderRouteRole.PRIMARY,
                    record = key.primary,
                    mangaId = encoded.primaryMangaId,
                    chapterUrl = encoded.primaryChapterUrl,
                    chapterId = encoded.primaryChapterId,
                    pageIndex = encoded.primaryPageIndex,
                ),
                currentRoute = AlternateSourceReaderRoute(
                    role = AlternateSourceReaderRouteRole.valueOf(encoded.currentRole),
                    record = when (AlternateSourceReaderRouteRole.valueOf(encoded.currentRole)) {
                        AlternateSourceReaderRouteRole.PRIMARY -> key.primary
                        AlternateSourceReaderRouteRole.ALTERNATE -> key.alternate
                    },
                    mangaId = encoded.currentMangaId,
                    chapterUrl = encoded.currentChapterUrl,
                    chapterId = encoded.currentChapterId,
                    pageIndex = encoded.currentPageIndex,
                ),
                activeTargetId = encoded.activeTargetId,
                lastSafeRouteFingerprint = encoded.lastSafeRouteFingerprint,
                previousRouteFingerprint = encoded.previousRouteFingerprint,
                pendingRouteFingerprint = encoded.pendingRouteFingerprint,
                transitionCount = encoded.transitionCount,
                failedResolutionCount = encoded.failedResolutionCount,
                lastTransitionReason = AlternateSourceReaderTransitionReason.valueOf(encoded.lastTransitionReason),
                bridgeUpdatedAt = encoded.bridgeUpdatedAt,
                mappingUpdatedAt = encoded.mappingUpdatedAt,
                uncertainAlignmentNoticeEmitted = encoded.uncertainAlignmentNoticeEmitted,
            )
            if (AlternateSourceReaderSession.isValid(session)) DecodeResult.Valid(session) else DecodeResult.Invalid
        } catch (_: IllegalArgumentException) {
            DecodeResult.Invalid
        }
    }
}
