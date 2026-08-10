package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class Backup(
    @ProtoNumber(1) val backupManga: List<BackupManga>,
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    // @ProtoNumber(100) var backupBrokenSources, legacy source model with non-compliant proto number,
    @ProtoNumber(101) var backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) var backupExtensionStores: List<BackupExtensionStore> = emptyList(),
    // SY specific values
    @ProtoNumber(600) var backupSavedSearches: List<BackupSavedSearch> = emptyList(),
    // KMK -->
    // Global Popular/Latest feeds
    @ProtoNumber(610) var backupFeeds: List<BackupFeed> = emptyList(),
    // Taste profile: manga ratings, tag preferences, tag aliases, disabled recs sources
    // Proto numbers 620-629 are reserved for this fork's taste system — check upstream before reusing
    @ProtoNumber(620) var backupMangaTastes: List<BackupMangaTaste> = emptyList(),
    @ProtoNumber(621) var backupTagTastes: List<BackupTagTaste> = emptyList(),
    @ProtoNumber(622) var backupTagAliases: List<BackupTagAlias> = emptyList(),
    @ProtoNumber(623) var backupDisabledRecommendationSources: List<BackupDisabledRecommendationSource> = emptyList(),
    // KMK --> v0.7.0: Phase 4 – cross-source link groups
    @ProtoNumber(624) var backupCrossSourceMangaLinks: List<BackupCrossSourceMangaLink> = emptyList(),
    // KMK <--
    // KMK --> v0.7.16: Best Version quality signals (proto 625)
    @ProtoNumber(625) var backupMangaSourceQualitySignals: List<BackupMangaSourceQualitySignal> = emptyList(),
    // KMK <--
    // KMK --> v0.7.28: seen manga keys for For You dismissals (proto 626)
    @ProtoNumber(626) var backupSeenMangaKeys: List<BackupSeenMangaKey> = emptyList(),
    // KMK <--
    // KMK --> v0.8.1-fix1: user-selected primary version per confirmed cross-source link group (proto 627)
    @ProtoNumber(627) var backupCrossSourceGroupPrimaries: List<BackupCrossSourceGroupPrimary> = emptyList(),
    // KMK <--
    // KMK <--
)
