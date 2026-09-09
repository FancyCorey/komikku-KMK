package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupLocalTrackedWork(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val title: String = "",
    @ProtoNumber(3) val normalizedTitle: String = "",
    @ProtoNumber(4) val status: String = "",
    @ProtoNumber(5) val lastChapterSource: Long = 0,
    @ProtoNumber(6) val lastChapterNumber: Double = 0.0,
    @ProtoNumber(7) val hasLastChapterNumber: Boolean = false,
    @ProtoNumber(8) val lastChapterUrl: String = "",
    @ProtoNumber(9) val lastChapterLabel: String = "",
    @ProtoNumber(10) val lastProgressAt: Long = 0,
    @ProtoNumber(11) val createdAt: Long = 0,
    @ProtoNumber(12) val updatedAt: Long = 0,
    @ProtoNumber(13) val sources: List<BackupLocalTrackedWorkSource> = emptyList(),
    @ProtoNumber(14) val lists: List<BackupLocalTrackedWorkList> = emptyList(),
    @ProtoNumber(15) val score: Double = 0.0,
    @ProtoNumber(16) val hasScore: Boolean = false,
    @ProtoNumber(17) val startDate: Long = 0,
    @ProtoNumber(18) val finishDate: Long = 0,
    @ProtoNumber(19) val hasStartDate: Boolean = false,
    @ProtoNumber(20) val hasFinishDate: Boolean = false,
    /** 0 is the legacy 0-10 local scale; 1 is the canonical 1-100 scale. */
    @ProtoNumber(21) val scoreScaleVersion: Int = 0,
    @ProtoNumber(22) val sourceProgress: List<BackupLocalTrackedWorkSourceProgress> = emptyList(),
    /** Presence markers distinguish current authoritative empty collections from legacy omission. */
    @ProtoNumber(23) val hasSources: Boolean = false,
    @ProtoNumber(24) val hasLists: Boolean = false,
    @ProtoNumber(25) val hasSourceProgress: Boolean = false,
)

@Serializable
data class BackupLocalTrackedWorkSource(
    @ProtoNumber(1) val source: Long = 0,
    @ProtoNumber(2) val url: String = "",
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val confidence: Int = 0,
    @ProtoNumber(5) val confirmation: String = "",
    @ProtoNumber(6) val createdAt: Long = 0,
    @ProtoNumber(7) val updatedAt: Long = 0,
    @ProtoNumber(8) val inheritanceOptedOut: Boolean = false,
)

@Serializable
data class BackupLocalTrackedWorkSourceProgress(
    @ProtoNumber(1) val source: Long = 0,
    @ProtoNumber(2) val url: String = "",
    @ProtoNumber(3) val chapterNumber: Double = 0.0,
    @ProtoNumber(4) val hasChapterNumber: Boolean = false,
    @ProtoNumber(5) val chapterUrl: String = "",
    @ProtoNumber(6) val chapterLabel: String = "",
    @ProtoNumber(7) val progressAt: Long = 0,
    @ProtoNumber(8) val inheritedFromSource: Long = 0,
    @ProtoNumber(9) val inheritedFromUrl: String = "",
    @ProtoNumber(10) val hasInheritedFrom: Boolean = false,
    @ProtoNumber(11) val updatedAt: Long = 0,
)

@Serializable
data class BackupLocalTrackedWorkList(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val createdAt: Long = 0,
)
