package exh.recs.bridge.fixture

import android.util.Log
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderBridgePolicy
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderMutationPolicy
import exh.recs.bestversion.fixture.BestVersionPairedFixtureCleanupResult
import exh.recs.bestversion.fixture.BestVersionPairedFixtureManifestLoad
import exh.recs.bestversion.fixture.BestVersionPairedFixtureMode
import exh.recs.bestversion.fixture.BestVersionPairedFixtureRoutePreparation
import exh.recs.bestversion.fixture.BestVersionPairedFixtureRuntime
import exh.recs.bestversion.fixture.PreferenceBestVersionPairedFixtureRecoveryStore
import exh.util.AlternateSourceBridgeUndoJournal
import exh.util.CrossSourceIdentityUndoJournal
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.taste.interactor.GetAlternateSourceBridge
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.ReplaceAlternateSourceBridge
import tachiyomi.domain.taste.interactor.ReplaceCrossSourceIdentityDecision
import tachiyomi.domain.taste.model.AlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeMapping
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingReplacement
import tachiyomi.domain.taste.model.AlternateSourceBridgePolicy
import tachiyomi.domain.taste.model.AlternateSourceBridgeState
import tachiyomi.domain.taste.model.AlternateSourceBridgeStateReplacement
import tachiyomi.domain.taste.model.CrossSourceIdentityDecision
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionValue
import tachiyomi.domain.taste.model.CrossSourceRecordKey
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest

interface AlternateSourceReaderPairedFixtureGateway {
    suspend fun prepare(): AlternateSourceReaderPairedGraphPreparation
    suspend fun cleanup(operationId: String): Boolean
    suspend fun cleanupUnrecorded(): Boolean
    suspend fun isAbsent(operationId: String): Boolean
}

class RuntimeAlternateSourceReaderPairedFixtureGateway(
    preferenceStore: PreferenceStore = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val runtime: BestVersionPairedFixtureRuntime = BestVersionPairedFixtureRuntime(
        modeOverride = BestVersionPairedFixtureMode.PAIRED_RECORDS,
        // The bridge and nested coordinator must observe the same preference instance. Separate
        // stores can make cleanup report success while leaving the paired recovery checkpoint.
        preferenceStore = preferenceStore,
    ),
) : AlternateSourceReaderPairedFixtureGateway {
    private val recoveryStore = PreferenceBestVersionPairedFixtureRecoveryStore(preferenceStore)

    override suspend fun prepare(): AlternateSourceReaderPairedGraphPreparation =
        when (val prepared = runtime.routeController.prepare()) {
            is BestVersionPairedFixtureRoutePreparation.Ready -> {
                Log.w(TAG, "paired route ready operation=${prepared.descriptor.operationId} origin=${prepared.descriptor.originMangaId}")
                val origin = ownedMangaById(
                    prepared.descriptor.originMangaId,
                    PRIMARY_SOURCE_ID,
                    PRIMARY_MANGA_URL,
                    prepared.descriptor.operationId,
                )
                    ?: return AlternateSourceReaderPairedGraphPreparation.Failed.also {
                        Log.w(TAG, "paired origin missing or ownership mismatch")
                    }
                val alternate = ownedManga(ALTERNATE_MANGA_URL, ALTERNATE_SOURCE_ID, prepared.descriptor.operationId)
                    ?: return AlternateSourceReaderPairedGraphPreparation.Failed.also {
                        Log.w(TAG, "paired alternate missing or ownership mismatch")
                    }
                if (origin.id != prepared.descriptor.originMangaId) {
                    Log.w(TAG, "paired origin id mismatch actual=${origin.id} expected=${prepared.descriptor.originMangaId}")
                    AlternateSourceReaderPairedGraphPreparation.Failed
                } else {
                    Log.w(TAG, "paired graph ready origin=${origin.id} alternate=${alternate.id}")
                    AlternateSourceReaderPairedGraphPreparation.Ready(
                        AlternateSourceReaderPairedGraph(prepared.descriptor.operationId, origin.id, alternate.id),
                    )
                }
            }
            BestVersionPairedFixtureRoutePreparation.NotAuthorized ->
                AlternateSourceReaderPairedGraphPreparation.NotAuthorized
            BestVersionPairedFixtureRoutePreparation.Collision -> AlternateSourceReaderPairedGraphPreparation.Collision
            BestVersionPairedFixtureRoutePreparation.RecoveryRequired ->
                AlternateSourceReaderPairedGraphPreparation.RecoveryRequired
            BestVersionPairedFixtureRoutePreparation.Failed -> {
                Log.w(TAG, "paired route failed")
                AlternateSourceReaderPairedGraphPreparation.Failed
            }
        }

    override suspend fun cleanup(operationId: String): Boolean {
        when (val loaded = recoveryStore.load()) {
            BestVersionPairedFixtureManifestLoad.Missing -> return true
            BestVersionPairedFixtureManifestLoad.Corrupt -> return false
            is BestVersionPairedFixtureManifestLoad.Present -> {
                if (loaded.manifest.operationId != operationId) return false
            }
        }
        return when (runtime.coordinator.cleanup()) {
            BestVersionPairedFixtureCleanupResult.NothingToClean,
            BestVersionPairedFixtureCleanupResult.Cleaned,
            -> true
            BestVersionPairedFixtureCleanupResult.RecoveryRecordCorrupt,
            BestVersionPairedFixtureCleanupResult.ConflictOrFailure,
            -> false
        }
    }

    override suspend fun cleanupUnrecorded(): Boolean = when (recoveryStore.load()) {
        BestVersionPairedFixtureManifestLoad.Missing -> true
        BestVersionPairedFixtureManifestLoad.Corrupt -> false
        is BestVersionPairedFixtureManifestLoad.Present -> when (runtime.coordinator.cleanup()) {
            BestVersionPairedFixtureCleanupResult.NothingToClean,
            BestVersionPairedFixtureCleanupResult.Cleaned,
            -> true
            BestVersionPairedFixtureCleanupResult.RecoveryRecordCorrupt,
            BestVersionPairedFixtureCleanupResult.ConflictOrFailure,
            -> false
        }
    }

    override suspend fun isAbsent(operationId: String): Boolean {
        val recoveryAbsent = when (val loaded = recoveryStore.load()) {
            BestVersionPairedFixtureManifestLoad.Missing -> true
            BestVersionPairedFixtureManifestLoad.Corrupt -> false
            is BestVersionPairedFixtureManifestLoad.Present -> false
        }
        return recoveryAbsent &&
            mangaRepository.getMangaByUrlAndSourceId(PRIMARY_MANGA_URL, PRIMARY_SOURCE_ID) == null &&
            mangaRepository.getMangaByUrlAndSourceId(ALTERNATE_MANGA_URL, ALTERNATE_SOURCE_ID) == null
    }

    private suspend fun ownedMangaById(id: Long, sourceId: Long, url: String, operationId: String): Manga? {
        val manga = mangaRepository.getMangaById(id)
        Log.w(
            TAG,
            "owned id lookup id=$id found=${manga != null} source=${manga?.source} url=${manga?.url} " +
                "notes=${manga?.notes} expected=${ownershipMarker(operationId)}",
        )
        return manga?.takeIf {
            it.source == sourceId && it.url == url && it.notes == ownershipMarker(operationId)
        }
    }

    private suspend fun ownedManga(url: String, sourceId: Long, operationId: String): Manga? {
        val manga = mangaRepository.getMangaByUrlAndSourceId(url, sourceId)
        Log.w(
            TAG,
            "owned lookup source=$sourceId url=$url found=${manga != null} " +
                "id=${manga?.id} notes=${manga?.notes} expected=${ownershipMarker(operationId)}",
        )
        return manga?.takeIf { it.notes == ownershipMarker(operationId) }
    }

    private companion object {
        const val TAG = "KMKFixture"
        const val PRIMARY_SOURCE_ID = 910000000000000001L
        const val ALTERNATE_SOURCE_ID = 910000000000000002L
        const val PRIMARY_MANGA_URL = "/kmk-fixture/f2/origin"
        const val ALTERNATE_MANGA_URL = "/kmk-fixture/f2/target"
        fun ownershipMarker(operationId: String) = "kmk-f2:$operationId"
    }
}

/** Repository composition for the bridge overlay. It never records fixture writes in Action History. */
class RepositoryAlternateSourceReaderFixtureDataStore(
    private val pairedFixture: AlternateSourceReaderPairedFixtureGateway =
        RuntimeAlternateSourceReaderPairedFixtureGateway(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val chapterRepository: ChapterRepository = Injekt.get(),
    private val getIdentity: GetCrossSourceIdentityDecisions = Injekt.get(),
    private val replaceIdentity: ReplaceCrossSourceIdentityDecision = Injekt.get(),
    private val getBridge: GetAlternateSourceBridge = Injekt.get(),
    private val replaceBridge: ReplaceAlternateSourceBridge = Injekt.get(),
    private val bridgeJournalIds: () -> Set<String> = {
        AlternateSourceBridgeUndoJournal.snapshot().mapTo(mutableSetOf()) { it.id }
    },
    private val identityJournalIds: () -> Set<String> = {
        CrossSourceIdentityUndoJournal.snapshot().mapTo(mutableSetOf()) { it.id }
    },
    private val now: () -> Long = System::currentTimeMillis,
) : AlternateSourceReaderFixtureDataStore {

    override suspend fun preparePairedGraph(): AlternateSourceReaderPairedGraphPreparation = pairedFixture.prepare()

    override suspend fun captureBaseline(
        graph: AlternateSourceReaderPairedGraph,
    ): AlternateSourceReaderFixtureBaseline {
        val context = requireOwnedContext(graph)
        check(getBridge.await(context.bridgeKey) == null) { "fixture-bridge-collision" }
        check(getIdentity.await(context.identityPair) == null) { "fixture-identity-collision" }
        val gapChapter = requireNotNull(
            chapterRepository.getChapterByUrlAndMangaId(PRIMARY_GAP_CHAPTER_URL, graph.primaryMangaId),
        ) { "fixture-primary-gap-chapter-missing" }
        check(gapChapter.toSnapshot().isStructurallyValid()) { "fixture-primary-gap-chapter-invalid" }
        return AlternateSourceReaderFixtureBaseline(
            primaryGapChapter = gapChapter.toSnapshot(),
            bridgeHash = hashBridge(null),
            identityHash = hashIdentity(null),
            actionHistoryIds = actionHistoryIds(),
        )
    }

    override suspend fun inspect(
        manifest: AlternateSourceReaderFixtureManifest,
    ): AlternateSourceReaderFixtureObservedState {
        val context = ownedContextOrNull(manifest)
        val identity = getIdentity.await(identityPair(manifest))
        val bridge = getBridge.await(bridgeKey(manifest))
        val rows = buildList {
            identity?.let { add(canonicalIdentity(it)) }
            bridge?.let { state ->
                add(canonicalBridge(state.bridge))
                state.mappings.map(::canonicalMapping).sorted().forEach(::add)
            }
        }
        val contextReady = context != null
        val gapAbsent = primaryGapIsAbsent(manifest)
        val scenarioReady = context?.let { matchesScenario(manifest, identity, bridge, it) } ?: false
        val routeReady = contextReady && gapAbsent && scenarioReady
        diagnostic(
            "inspect op=${manifest.operationId} scenario=${manifest.scenario} " +
                "context=$contextReady gapAbsent=$gapAbsent scenarioReady=$scenarioReady " +
                "identity=${identity != null} bridge=${bridge != null} altRows=${rows.size}",
        )
        return AlternateSourceReaderFixtureObservedState(
            overlayRows = rows,
            bridgeHash = hashBridge(bridge),
            identityHash = hashIdentity(identity),
            actionHistoryIds = actionHistoryIds(),
            routeReady = routeReady,
        )
    }

    override suspend fun applyStep(
        manifest: AlternateSourceReaderFixtureManifest,
        step: AlternateSourceReaderFixtureStep,
    ): AlternateSourceReaderFixtureManifest {
        val context = requireOwnedContext(manifest)
        when (step) {
            AlternateSourceReaderFixtureStep.PRIMARY_GAP -> removePrimaryGap(manifest)
            AlternateSourceReaderFixtureStep.IDENTITY -> seedIdentity(manifest)
            AlternateSourceReaderFixtureStep.BRIDGE -> seedBridge(manifest, context)
            AlternateSourceReaderFixtureStep.SESSION -> {
                check(primaryGapIsAbsent(manifest)) { "fixture-primary-gap-not-applied" }
                check(matchesScenario(manifest, getIdentity.await(context.identityPair), getBridge.await(context.bridgeKey), context)) {
                    "fixture-session-inputs-not-ready"
                }
            }
            AlternateSourceReaderFixtureStep.ROUTE -> {
                check(inspect(manifest).routeReady) { "fixture-route-inputs-not-ready" }
            }
            AlternateSourceReaderFixtureStep.PAIRED_GRAPH,
            AlternateSourceReaderFixtureStep.BASELINES,
            -> error("fixture-step-owned-by-coordinator")
        }
        return manifest
    }

    override suspend fun cleanupOverlay(manifest: AlternateSourceReaderFixtureManifest): Boolean {
        val expectedIdentity = expectedIdentity(manifest)
        val expectedBridge = expectedBridgeState(manifest)
        val pair = identityPair(manifest)
        val key = bridgeKey(manifest)
        val currentBridge = getBridge.await(key)
        if (currentBridge != null && !sameBridgeState(currentBridge, expectedBridge)) return false
        val currentIdentity = getIdentity.await(pair)
        if (currentIdentity != null && currentIdentity != expectedIdentity) return false

        if (currentBridge != null) {
            val removed = replaceBridge.await(
                AlternateSourceBridgeStateReplacement(
                    expectedBridge = currentBridge.bridge,
                    replacementBridge = null,
                    mappingReplacements = currentBridge.mappings.map {
                        AlternateSourceBridgeMappingReplacement(expected = it, replacement = null)
                    },
                ),
            )
            if (!removed) return false
        }
        if (currentIdentity != null && !replaceIdentity.await(currentIdentity, null)) return false

        return getBridge.await(key) == null &&
            getIdentity.await(pair) == null &&
            actionHistoryIds() == manifest.actionHistoryBaselineIds
    }

    override suspend fun cleanupPairedGraph(operationId: String): Boolean = pairedFixture.cleanup(operationId)

    override suspend fun cleanupUnrecordedPairedGraph(): Boolean = pairedFixture.cleanupUnrecorded()

    override suspend fun isPairedGraphAbsent(operationId: String): Boolean = pairedFixture.isAbsent(operationId)

    private suspend fun removePrimaryGap(manifest: AlternateSourceReaderFixtureManifest) {
        val snapshot = requireNotNull(manifest.primaryGapChapter)
        val current = chapterRepository.getChapterById(snapshot.id)
        check(current?.toSnapshot() == snapshot) { "fixture-primary-gap-chapter-collision" }
        chapterRepository.removeChaptersWithIds(listOf(snapshot.id))
        check(chapterRepository.getChapterById(snapshot.id) == null) { "fixture-primary-gap-delete-not-observed" }
        check(chapterRepository.getChapterByUrlAndMangaId(snapshot.url, snapshot.mangaId) == null) {
            "fixture-primary-gap-url-still-present"
        }
    }

    private suspend fun seedIdentity(manifest: AlternateSourceReaderFixtureManifest) {
        val expected = expectedIdentity(manifest)
        val current = getIdentity.await(expected.pair)
        if (current == expected) return
        check(current == null) { "fixture-identity-collision" }
        check(replaceIdentity.await(null, expected)) { "fixture-identity-replace-conflict" }
    }

    private suspend fun seedBridge(manifest: AlternateSourceReaderFixtureManifest, context: OwnedContext) {
        val expected = expectedBridgeState(manifest) ?: return
        val current = getBridge.await(context.bridgeKey)
        if (sameBridgeState(current, expected)) return
        check(current == null) { "fixture-bridge-collision" }
        val replacement = AlternateSourceBridgeStateReplacement(
            expectedBridge = null,
            replacementBridge = expected.bridge,
            mappingReplacements = expected.mappings.map {
                AlternateSourceBridgeMappingReplacement(expected = null, replacement = it)
            },
        )
        check(replaceBridge.await(replacement)) { "fixture-bridge-replace-conflict" }
    }

    private suspend fun matchesScenario(
        manifest: AlternateSourceReaderFixtureManifest,
        identity: CrossSourceIdentityDecision?,
        bridge: AlternateSourceBridgeState?,
        context: OwnedContext,
    ): Boolean {
        if (identity != expectedIdentity(manifest)) return false
        if (!sameBridgeState(bridge, expectedBridgeState(manifest))) return false
        val decision = AlternateSourceReaderBridgePolicy.resolveEntry(
            bridge,
            PRECEDING_PRIMARY_CHAPTER_URL,
            FOLLOWING_PRIMARY_CHAPTER_URL,
            now(),
        )
        val expectedDecision = when (manifest.scenario) {
            AlternateSourceReaderFixtureScenario.EXACT_GAP,
            AlternateSourceReaderFixtureScenario.STALE,
            AlternateSourceReaderFixtureScenario.PROCESS_RECREATED,
            -> decision is AlternateSourceReaderBridgePolicy.EntryDecision.Confirmed &&
                decision.alternateChapterUrl == TARGET_CHAPTER_2_URL
            AlternateSourceReaderFixtureScenario.OFFSET_PROVISIONAL ->
                decision is AlternateSourceReaderBridgePolicy.EntryDecision.RequiresConfirmation &&
                    decision.alternateChapterUrl == TARGET_CHAPTER_3_URL
            AlternateSourceReaderFixtureScenario.MISSING ->
                decision == AlternateSourceReaderBridgePolicy.EntryDecision.Unavailable
            AlternateSourceReaderFixtureScenario.DUPLICATE_CONFLICT ->
                decision == AlternateSourceReaderBridgePolicy.EntryDecision.Conflict
            AlternateSourceReaderFixtureScenario.OFF -> false
        }
        return expectedDecision && requiredAlternateChaptersExist(manifest, context)
    }

    private suspend fun requiredAlternateChaptersExist(
        manifest: AlternateSourceReaderFixtureManifest,
        context: OwnedContext,
    ): Boolean {
        val required = when (manifest.scenario) {
            AlternateSourceReaderFixtureScenario.EXACT_GAP,
            AlternateSourceReaderFixtureScenario.STALE,
            AlternateSourceReaderFixtureScenario.PROCESS_RECREATED,
            -> listOf(TARGET_CHAPTER_2_URL)
            AlternateSourceReaderFixtureScenario.OFFSET_PROVISIONAL -> listOf(TARGET_CHAPTER_3_URL)
            AlternateSourceReaderFixtureScenario.DUPLICATE_CONFLICT ->
                listOf(TARGET_CHAPTER_2_URL, TARGET_CHAPTER_3_URL)
            AlternateSourceReaderFixtureScenario.MISSING -> emptyList()
            AlternateSourceReaderFixtureScenario.OFF -> return false
        }
        return required.all { chapterRepository.getChapterByUrlAndMangaId(it, context.alternate.id) != null }
    }

    private fun expectedIdentity(manifest: AlternateSourceReaderFixtureManifest): CrossSourceIdentityDecision =
        CrossSourceIdentityDecisionPolicy.userDecision(
            pair = identityPair(manifest),
            value = CrossSourceIdentityDecisionValue.USER_CONFIRMED,
            previous = null,
            timestamp = manifest.createdAt,
        )

    private fun expectedBridgeState(manifest: AlternateSourceReaderFixtureManifest): AlternateSourceBridgeState? {
        if (manifest.scenario == AlternateSourceReaderFixtureScenario.MISSING) return null
        val alternateChapter = when (manifest.scenario) {
            AlternateSourceReaderFixtureScenario.OFFSET_PROVISIONAL -> TARGET_CHAPTER_3_URL
            AlternateSourceReaderFixtureScenario.EXACT_GAP,
            AlternateSourceReaderFixtureScenario.DUPLICATE_CONFLICT,
            AlternateSourceReaderFixtureScenario.STALE,
            AlternateSourceReaderFixtureScenario.PROCESS_RECREATED,
            -> TARGET_CHAPTER_2_URL
            AlternateSourceReaderFixtureScenario.MISSING,
            AlternateSourceReaderFixtureScenario.OFF,
            -> return null
        }
        val selected = requireReplacement(
            AlternateSourceReaderMutationPolicy.select(
                current = null,
                key = bridgeKey(manifest),
                precedingPrimaryChapterUrl = PRECEDING_PRIMARY_CHAPTER_URL,
                followingPrimaryChapterUrl = FOLLOWING_PRIMARY_CHAPTER_URL,
                alternateChapterUrl = alternateChapter,
                targetId = TARGET_ID_1,
                now = manifest.createdAt,
            ),
        )
        var state = selected.toState()
        if (manifest.scenario == AlternateSourceReaderFixtureScenario.DUPLICATE_CONFLICT) {
            val second = requireReplacement(
                AlternateSourceReaderMutationPolicy.select(
                    current = null,
                    key = bridgeKey(manifest),
                    precedingPrimaryChapterUrl = PRECEDING_PRIMARY_CHAPTER_URL,
                    followingPrimaryChapterUrl = FOLLOWING_PRIMARY_CHAPTER_URL,
                    alternateChapterUrl = TARGET_CHAPTER_3_URL,
                    targetId = TARGET_ID_2,
                    now = manifest.createdAt,
                ),
            )
            state = state.copy(mappings = state.mappings + requireNotNull(second.value.mappingReplacements.single().replacement))
        }
        if (
            manifest.scenario == AlternateSourceReaderFixtureScenario.EXACT_GAP ||
            manifest.scenario == AlternateSourceReaderFixtureScenario.STALE ||
            manifest.scenario == AlternateSourceReaderFixtureScenario.PROCESS_RECREATED
        ) {
            val corrected = requireReplacement(
                AlternateSourceReaderMutationPolicy.correct(
                    current = state,
                    targetId = TARGET_ID_1,
                    alternateChapterUrl = TARGET_CHAPTER_2_URL,
                    now = manifest.createdAt,
                ),
            )
            state = corrected.toState()
        }
        return state
    }

    private suspend fun requireOwnedContext(graph: AlternateSourceReaderPairedGraph): OwnedContext =
        requireNotNull(ownedContextOrNull(graph.operationId, graph.primaryMangaId, graph.alternateMangaId)) {
            "fixture-paired-graph-ownership-mismatch"
        }

    private suspend fun requireOwnedContext(manifest: AlternateSourceReaderFixtureManifest): OwnedContext =
        requireNotNull(ownedContextOrNull(manifest)) { "fixture-paired-graph-ownership-mismatch" }

    private suspend fun ownedContextOrNull(manifest: AlternateSourceReaderFixtureManifest): OwnedContext? =
        ownedContextOrNull(manifest.pairedFixtureOperationId, manifest.primaryMangaId, manifest.alternateMangaId)

    private suspend fun ownedContextOrNull(operationId: String, primaryId: Long, alternateId: Long): OwnedContext? {
        val marker = "kmk-f2:$operationId"
        val primary = mangaRepository.getMangaByUrlAndSourceId(PRIMARY_MANGA_URL, PRIMARY_SOURCE_ID)
            ?.takeIf { it.id == primaryId && it.notes == marker } ?: return null
        val alternate = mangaRepository.getMangaByUrlAndSourceId(ALTERNATE_MANGA_URL, ALTERNATE_SOURCE_ID)
            ?.takeIf { it.id == alternateId && it.notes == marker } ?: return null
        val context = OwnedContext(primary, alternate, bridgeKey(primary.source, primary.url, alternate.source, alternate.url))
        return context.takeIf {
            chapterRepository.getChapterByUrlAndMangaId(PRECEDING_PRIMARY_CHAPTER_URL, primary.id) != null &&
                chapterRepository.getChapterByUrlAndMangaId(FOLLOWING_PRIMARY_CHAPTER_URL, primary.id) != null
        }
    }

    private suspend fun primaryGapIsAbsent(manifest: AlternateSourceReaderFixtureManifest): Boolean =
        chapterRepository.getChapterById(requireNotNull(manifest.primaryGapChapter).id) == null &&
            chapterRepository.getChapterByUrlAndMangaId(PRIMARY_GAP_CHAPTER_URL, manifest.primaryMangaId) == null

    private fun identityPair(manifest: AlternateSourceReaderFixtureManifest) =
        CrossSourceIdentityDecisionPolicy.canonicalPair(
            CrossSourceRecordKey(manifest.primarySourceId, manifest.primaryMangaUrl),
            CrossSourceRecordKey(manifest.alternateSourceId, manifest.alternateMangaUrl),
        )

    private fun bridgeKey(manifest: AlternateSourceReaderFixtureManifest) = bridgeKey(
        manifest.primarySourceId,
        manifest.primaryMangaUrl,
        manifest.alternateSourceId,
        manifest.alternateMangaUrl,
    )

    private fun bridgeKey(primarySource: Long, primaryUrl: String, alternateSource: Long, alternateUrl: String) =
        AlternateSourceBridgeKey(
            CrossSourceRecordKey(primarySource, primaryUrl),
            CrossSourceRecordKey(alternateSource, alternateUrl),
        )

    private fun diagnostic(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    private fun actionHistoryIds(): Set<String> = bridgeJournalIds() + identityJournalIds()

    private fun hashIdentity(decision: CrossSourceIdentityDecision?): String = sha256(decision?.let(::canonicalIdentity) ?: "none")

    private fun hashBridge(state: AlternateSourceBridgeState?): String = sha256(
        state?.let {
            buildList {
                add(canonicalBridge(it.bridge))
                addAll(it.mappings.map(::canonicalMapping).sorted())
            }.joinToString("\n")
        } ?: "none",
    )

    private fun sameBridgeState(first: AlternateSourceBridgeState?, second: AlternateSourceBridgeState?): Boolean =
        first?.bridge == second?.bridge &&
            first?.mappings.orEmpty().sortedWith(AlternateSourceBridgePolicy.mappingComparator) ==
            second?.mappings.orEmpty().sortedWith(AlternateSourceBridgePolicy.mappingComparator)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02X".format(it) }

    private fun canonicalIdentity(value: CrossSourceIdentityDecision): String = listOf(
        "identity",
        value.pair.left.source,
        value.pair.left.url,
        value.pair.right.source,
        value.pair.right.url,
        value.decision.name,
        value.decisionVersion,
        value.evidenceVersion,
        value.reasonCodes.sortedBy { it.ordinal }.joinToString(",") { it.name },
        value.reviewState.name,
        value.createdAt,
        value.updatedAt,
        value.deletedAt,
    ).joinToString("|")

    private fun canonicalBridge(value: AlternateSourceBridge): String = listOf(
        "bridge",
        value.key.primary.source,
        value.key.primary.url,
        value.key.alternate.source,
        value.key.alternate.url,
        value.version,
        value.offsetMilli,
        value.offsetState?.name,
        value.continuationPrimaryChapterUrl,
        value.returnAfterAlternateChapterUrl,
        value.automaticReturn,
        value.reviewState.name,
        value.createdAt,
        value.updatedAt,
        value.deletedAt,
    ).joinToString("|")

    private fun canonicalMapping(value: AlternateSourceBridgeMapping): String = listOf(
        "mapping",
        value.key.targetId,
        value.primaryChapterUrl,
        value.alternateChapterUrl,
        value.precedingPrimaryChapterUrl,
        value.followingPrimaryChapterUrl,
        value.relation.name,
        value.state.name,
        value.offsetMilli,
        value.version,
        value.createdAt,
        value.updatedAt,
        value.deletedAt,
    ).joinToString("|")

    private fun Chapter.toSnapshot() = AlternateSourceReaderFixtureChapterSnapshot(
        id = id,
        mangaId = mangaId,
        url = url,
        name = name,
        chapterNumber = chapterNumber.toFloat(),
        scanlator = scanlator,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        dateFetch = dateFetch,
        dateUpload = dateUpload,
        sourceOrder = sourceOrder,
    )

    private fun requireReplacement(result: AlternateSourceReaderMutationPolicy.Result) =
        result as? AlternateSourceReaderMutationPolicy.Result.Replacement
            ?: error("fixture-production-policy-refused-scenario")

    private fun AlternateSourceReaderMutationPolicy.Result.Replacement.toState(): AlternateSourceBridgeState =
        AlternateSourceBridgeState(
            bridge = requireNotNull(value.replacementBridge),
            mappings = value.mappingReplacements.map { requireNotNull(it.replacement) },
        )

    private data class OwnedContext(
        val primary: Manga,
        val alternate: Manga,
        val bridgeKey: AlternateSourceBridgeKey,
    ) {
        val identityPair = CrossSourceIdentityDecisionPolicy.canonicalPair(bridgeKey.primary, bridgeKey.alternate)
    }

    private companion object {
        const val TAG = "KMKFixture"
        const val PRIMARY_SOURCE_ID = 910000000000000001L
        const val ALTERNATE_SOURCE_ID = 910000000000000002L
        const val PRIMARY_MANGA_URL = "/kmk-fixture/f2/origin"
        const val ALTERNATE_MANGA_URL = "/kmk-fixture/f2/target"
        const val PRECEDING_PRIMARY_CHAPTER_URL = "/kmk-fixture/f2/origin/chapter-1"
        const val PRIMARY_GAP_CHAPTER_URL = "/kmk-fixture/f2/origin/chapter-2"
        const val FOLLOWING_PRIMARY_CHAPTER_URL = "/kmk-fixture/f2/origin/chapter-3"
        const val TARGET_CHAPTER_2_URL = "/kmk-fixture/f2/target/chapter-2"
        const val TARGET_CHAPTER_3_URL = "/kmk-fixture/f2/target/chapter-3"
        const val TARGET_ID_1 = "11111111-1111-4111-8111-111111111111"
        const val TARGET_ID_2 = "22222222-2222-4222-8222-222222222222"
    }
}
