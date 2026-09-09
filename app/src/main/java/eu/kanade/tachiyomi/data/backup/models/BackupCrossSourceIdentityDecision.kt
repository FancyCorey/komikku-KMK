package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupCrossSourceIdentityDecision(
    @ProtoNumber(1) val leftSource: Long = 0,
    @ProtoNumber(2) val leftUrl: String = "",
    @ProtoNumber(3) val rightSource: Long = 0,
    @ProtoNumber(4) val rightUrl: String = "",
    @ProtoNumber(5) val decision: String = "",
    @ProtoNumber(6) val decisionVersion: Int = 0,
    @ProtoNumber(7) val evidenceVersion: Int = 0,
    @ProtoNumber(8) val reasonCodes: List<String> = emptyList(),
    @ProtoNumber(9) val reviewState: String = "",
    @ProtoNumber(10) val createdAt: Long = 0,
    @ProtoNumber(11) val updatedAt: Long = 0,
    @ProtoNumber(12) val deletedAt: Long = 0,
)
