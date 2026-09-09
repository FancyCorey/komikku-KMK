package exh.recs.bestversion.fixture

import kotlinx.serialization.Serializable
import mihon.domain.migration.models.MigrationFlag
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

enum class BestVersionPairedFixtureMode(val prefValue: String) {
    OFF("off"),
    PAIRED_RECORDS("paired_records"),
    ;

    companion object {
        fun fromPrefValue(value: String): BestVersionPairedFixtureMode =
            entries.firstOrNull { it.prefValue == value } ?: OFF
    }
}

data class BestVersionPairedFixtureSourceIdentity(
    val packageName: String,
    val sourceId: Long,
    val signerSha256: String,
    val extensionName: String,
    val sourceName: String,
    val versionName: String,
    val versionCode: Long,
)

data class BestVersionPairedFixtureActivation(
    val isDebugBuild: Boolean,
    val mode: BestVersionPairedFixtureMode,
    val evaluationModeEnabled: Boolean,
    val fixtureProfile: String,
    val expectedSignerSha256: String,
    val installedSources: Set<BestVersionPairedFixtureSourceIdentity>,
)

object BestVersionPairedFixtureGate {
    const val ISOLATED_PROFILE = "isolated-emulator-f2"
    const val ALPHA_PACKAGE = "app.komikku.fixture.sources.alpha"
    const val BETA_PACKAGE = "app.komikku.fixture.sources.beta"
    const val ALPHA_SOURCE_ID = 910000000000000001L
    const val BETA_SOURCE_ID = 910000000000000002L

    fun isAllowed(input: BestVersionPairedFixtureActivation): Boolean {
        if (!input.isDebugBuild || input.mode != BestVersionPairedFixtureMode.PAIRED_RECORDS) return false
        if (!input.evaluationModeEnabled || input.fixtureProfile != ISOLATED_PROFILE) return false

        val signer = input.expectedSignerSha256.lowercase(Locale.ROOT)
        if (!signer.matches(Regex("[0-9a-f]{64}"))) return false
        val expected = setOf(
            BestVersionPairedFixtureSourceIdentity(
                ALPHA_PACKAGE,
                ALPHA_SOURCE_ID,
                signer,
                "Fixture Source Alpha",
                "Fixture Source",
                "1.6.0",
                1L,
            ),
            BestVersionPairedFixtureSourceIdentity(
                BETA_PACKAGE,
                BETA_SOURCE_ID,
                signer,
                "Fixture Source Beta",
                "Fixture Source",
                "1.6.0",
                1L,
            ),
        )
        return input.installedSources
            .map { it.copy(signerSha256 = it.signerSha256.lowercase(Locale.ROOT)) }
            .toSet() == expected
    }
}

@Serializable
enum class BestVersionPairedFixtureStep {
    ORIGIN_MANGA,
    TARGET_MANGA,
    CHAPTERS_AND_HISTORY,
    CATEGORY,
    TASTES,
    GROUP_LINKS,
}

@Serializable
data class BestVersionPairedFixtureManifest(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val revision: String = REVISION,
    val operationId: String,
    val pendingStep: BestVersionPairedFixtureStep? = null,
    val completedSteps: Set<BestVersionPairedFixtureStep> = emptySet(),
    val originMangaId: Long? = null,
    val targetMangaId: Long? = null,
    val originChapterIds: List<Long> = emptyList(),
    val targetChapterIds: List<Long> = emptyList(),
    val categoryId: Long? = null,
    val sideEffectBaseline: BestVersionPairedFixtureSideEffectBaseline = BestVersionPairedFixtureSideEffectBaseline(),
    val seededStateHash: String? = null,
) {
    val ownershipMarker: String get() = "kmk-f2:$operationId"
    val categoryName: String get() = "KMK F2 ${operationId.take(8)}"
    val groupId: String get() = "kmk-f2-group-$operationId"

    fun isStructurallyValid(): Boolean =
        schemaVersion == CURRENT_SCHEMA_VERSION &&
            revision == REVISION &&
            runCatching { UUID.fromString(operationId) }.isSuccess &&
            completedSteps.all { it in BestVersionPairedFixtureStep.entries } &&
            sideEffectBaseline.isStructurallyValid() &&
            seededStateHash?.matches(Regex("[0-9A-F]{64}")) != false

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
        const val REVISION = "f2.3-r2"
    }
}

@Serializable
data class BestVersionPairedFixtureSideEffectBaseline(
    val qualitySignalIds: Set<Long> = emptySet(),
    val migrationEventIds: Set<String> = emptySet(),
    val migrationReceiptIds: Set<String> = emptySet(),
) {
    fun isStructurallyValid(): Boolean =
        qualitySignalIds.size <= MAX_QUALITY_SIGNAL_BASELINE && qualitySignalIds.all { it > 0L } &&
            migrationEventIds.size <= MAX_JOURNAL_BASELINE && migrationEventIds.all(::isUuid) &&
            migrationReceiptIds.size <= MAX_JOURNAL_BASELINE && migrationReceiptIds.all(::isUuid)

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess

    private companion object {
        const val MAX_QUALITY_SIGNAL_BASELINE = 4_096
        const val MAX_JOURNAL_BASELINE = 20
    }
}

data class BestVersionPairedFixtureSpec(
    val originSourceId: Long = BestVersionPairedFixtureGate.ALPHA_SOURCE_ID,
    val originUrl: String = "/kmk-fixture/f2/origin",
    val targetSourceId: Long = BestVersionPairedFixtureGate.BETA_SOURCE_ID,
    val targetUrl: String = "/kmk-fixture/f2/target",
    val allowedMigrationFlags: Set<MigrationFlag> = setOf(MigrationFlag.CHAPTER, MigrationFlag.CATEGORY),
) {
    init {
        require(originSourceId != targetSourceId)
        require(originUrl.startsWith("/kmk-fixture/f2/") && targetUrl.startsWith("/kmk-fixture/f2/"))
        require(allowedMigrationFlags == setOf(MigrationFlag.CHAPTER, MigrationFlag.CATEGORY))
    }
}

data class BestVersionPairedFixtureObservedState(
    val mangaRows: List<String> = emptyList(),
    val chapterRows: List<String> = emptyList(),
    val historyRows: List<String> = emptyList(),
    val categoryRows: List<String> = emptyList(),
    val tasteRows: List<String> = emptyList(),
    val groupRows: List<String> = emptyList(),
    val qualitySignalIds: Set<Long> = emptySet(),
    val migrationEventIds: Set<String> = emptySet(),
    val migrationReceiptIds: Set<String> = emptySet(),
) {
    val isAbsent: Boolean
        get() = mangaRows.isEmpty() && chapterRows.isEmpty() && historyRows.isEmpty() &&
            categoryRows.isEmpty() && tasteRows.isEmpty() && groupRows.isEmpty()

    fun sideEffectsMatch(baseline: BestVersionPairedFixtureSideEffectBaseline): Boolean =
        qualitySignalIds == baseline.qualitySignalIds &&
            migrationEventIds == baseline.migrationEventIds &&
            migrationReceiptIds == baseline.migrationReceiptIds

    fun sha256(): String {
        val canonical = listOf(
            mangaRows,
            chapterRows,
            historyRows,
            categoryRows,
            tasteRows,
            groupRows,
            qualitySignalIds.map(Long::toString),
            migrationEventIds.toList(),
            migrationReceiptIds.toList(),
        )
            .joinToString("\n") { rows -> rows.sorted().joinToString("|") }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }
    }
}
