package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupAlternateSourceBridge(
    @ProtoNumber(1) val primarySource: Long = 0,
    @ProtoNumber(2) val primaryUrl: String = "",
    @ProtoNumber(3) val alternateSource: Long = 0,
    @ProtoNumber(4) val alternateUrl: String = "",
    @ProtoNumber(5) val version: Int = 0,
    @ProtoNumber(6) val offsetMilli: Long = Long.MIN_VALUE,
    @ProtoNumber(7) val offsetState: String = "",
    @ProtoNumber(8) val continuationPrimaryChapterUrl: String = "",
    @ProtoNumber(9) val automaticReturn: Boolean = false,
    @ProtoNumber(10) val reviewState: String = "",
    @ProtoNumber(11) val createdAt: Long = 0,
    @ProtoNumber(12) val updatedAt: Long = 0,
    @ProtoNumber(13) val deletedAt: Long = 0,
    @ProtoNumber(14) val returnAfterAlternateChapterUrl: String = "",
)

@Serializable
data class BackupAlternateSourceBridgeMapping(
    @ProtoNumber(1) val primarySource: Long = 0,
    @ProtoNumber(2) val primaryUrl: String = "",
    @ProtoNumber(3) val alternateSource: Long = 0,
    @ProtoNumber(4) val alternateUrl: String = "",
    @ProtoNumber(5) val targetId: String = "",
    @ProtoNumber(6) val primaryChapterUrl: String = "",
    @ProtoNumber(7) val alternateChapterUrl: String = "",
    @ProtoNumber(8) val relation: String = "",
    @ProtoNumber(9) val state: String = "",
    @ProtoNumber(10) val offsetMilli: Long = Long.MIN_VALUE,
    @ProtoNumber(11) val version: Int = 0,
    @ProtoNumber(12) val createdAt: Long = 0,
    @ProtoNumber(13) val updatedAt: Long = 0,
    @ProtoNumber(14) val deletedAt: Long = 0,
    @ProtoNumber(15) val precedingPrimaryChapterUrl: String = "",
    @ProtoNumber(16) val followingPrimaryChapterUrl: String = "",
)
