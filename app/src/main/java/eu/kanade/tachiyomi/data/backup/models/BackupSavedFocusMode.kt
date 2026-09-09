package eu.kanade.tachiyomi.data.backup.models

// KMK v0.8.21-fix2: AUG-14 slice 3 -- saved For You focus modes
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// KMK v0.8.21-fix5: R4/AUG-14 completion -- direct product correction (2026-08-25). ProtoNumber 3
// ("groups") keeps its original wire number and now carries the include set; ProtoNumber 7/8 are
// new fields so an old backup (which never wrote them) decodes with their defaults (empty
// excludeGroups, matchAll = false to match SavedFocusModeStore's own v1 migration rule -- see its
// KDoc) rather than losing data or crashing.
@Serializable
data class BackupSavedFocusMode(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val groups: List<String> = emptyList(),
    @ProtoNumber(4) val createdAt: Long = 0,
    @ProtoNumber(5) val updatedAt: Long = 0,
    @ProtoNumber(6) val order: Int = 0,
    @ProtoNumber(7) val excludeGroups: List<String> = emptyList(),
    @ProtoNumber(8) val matchAll: Boolean = false,
)
// KMK <--
