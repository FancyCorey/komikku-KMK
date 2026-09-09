package exh.recs.bridge.fixture

import android.content.Context
import android.content.Intent
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderRoute
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderRouteRole
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderSession
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderStateCodec
import tachiyomi.core.common.Constants
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.taste.interactor.GetAlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingState
import tachiyomi.domain.taste.model.CrossSourceRecordKey
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object AlternateSourceReaderFixturePaths {
    const val PRIMARY_MANGA = "/kmk-fixture/f2/origin"
    const val ALTERNATE_MANGA = "/kmk-fixture/f2/target"
    const val PRIMARY_CHAPTER_1 = "$PRIMARY_MANGA/chapter-1"
    const val PRIMARY_CHAPTER_2 = "$PRIMARY_MANGA/chapter-2"
    const val PRIMARY_CHAPTER_3 = "$PRIMARY_MANGA/chapter-3"
    const val ALTERNATE_CHAPTER_1 = "$ALTERNATE_MANGA/chapter-1"
    const val ALTERNATE_CHAPTER_2 = "$ALTERNATE_MANGA/chapter-2"
    const val ALTERNATE_CHAPTER_3 = "$ALTERNATE_MANGA/chapter-3"
    const val ALTERNATE_CHAPTER_4 = "$ALTERNATE_MANGA/chapter-4"
}

enum class AlternateSourceReaderFixtureDestination { MANGA, READER }

data class AlternateSourceReaderFixtureLaunch(
    val destination: AlternateSourceReaderFixtureDestination,
    val mangaId: Long,
    val chapterId: Long? = null,
    val readerState: Map<String, Any?> = emptyMap(),
) {
    fun isStructurallyValid(): Boolean =
        mangaId > 0L &&
            when (destination) {
                AlternateSourceReaderFixtureDestination.MANGA -> chapterId == null && readerState.isEmpty()
                AlternateSourceReaderFixtureDestination.READER ->
                    chapterId != null && chapterId > 0L &&
                        readerState.keys.all { it in AlternateSourceReaderStateCodec.ALL_KEYS }
            }
}

sealed interface AlternateSourceReaderFixtureRouteResolution {
    data class Ready(
        val manga: AlternateSourceReaderFixtureLaunch,
        val reader: AlternateSourceReaderFixtureLaunch,
    ) : AlternateSourceReaderFixtureRouteResolution

    data object NotReady : AlternateSourceReaderFixtureRouteResolution
}

class AlternateSourceReaderFixtureRouteResolver(
    private val recoveryStore: AlternateSourceReaderFixtureRecoveryStore,
    private val dataStore: AlternateSourceReaderFixtureDataStore,
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val chapterRepository: ChapterRepository = Injekt.get(),
    private val getBridge: GetAlternateSourceBridge = Injekt.get(),
    private val diagnostic: (String) -> Unit = {},
) {
    suspend fun resolve(
        expectedScenario: AlternateSourceReaderFixtureScenario,
    ): AlternateSourceReaderFixtureRouteResolution {
        val manifest = when (val loaded = recoveryStore.load()) {
            is AlternateSourceReaderFixtureManifestLoad.Present -> loaded.manifest
            AlternateSourceReaderFixtureManifestLoad.Corrupt,
            AlternateSourceReaderFixtureManifestLoad.Missing,
            -> return notReady("recovery-record-missing-or-corrupt")
        }
        if (
            !manifest.isStructurallyValid() ||
            manifest.scenario != expectedScenario ||
            manifest.pendingStep != null ||
            manifest.completedSteps != AlternateSourceReaderFixtureStep.entries.toSet()
        ) {
            return notReady(
                "manifest-invalid-or-incomplete scenario=${manifest.scenario} expected=$expectedScenario " +
                    "pending=${manifest.pendingStep} completed=${manifest.completedSteps.size}/" +
                    AlternateSourceReaderFixtureStep.entries.size,
            )
        }
        val observed = dataStore.inspect(manifest)
        if (
            !observed.routeReady ||
            observed.sha256() != manifest.seededOverlayHash
        ) {
            return notReady(
                "overlay-not-ready routeReady=${observed.routeReady} " +
                    "hashMatches=${observed.sha256() == manifest.seededOverlayHash}",
            )
        }
        val primary = mangaRepository.getMangaByUrlAndSourceId(manifest.primaryMangaUrl, manifest.primarySourceId)
            ?.takeIf { it.id == manifest.primaryMangaId }
            ?: return notReady("primary-manga-missing-or-id-mismatch")
        val alternate = mangaRepository.getMangaByUrlAndSourceId(manifest.alternateMangaUrl, manifest.alternateSourceId)
            ?.takeIf { it.id == manifest.alternateMangaId }
            ?: return notReady("alternate-manga-missing-or-id-mismatch")
        val primaryChapter = chapterRepository.getChapterByUrlAndMangaId(
            AlternateSourceReaderFixturePaths.PRIMARY_CHAPTER_1,
            primary.id,
        ) ?: return notReady("primary-reader-chapter-missing")
        val readerLaunch = if (expectedScenario == AlternateSourceReaderFixtureScenario.PROCESS_RECREATED) {
            processRecreatedLaunch(manifest, primary.id, primaryChapter.id, alternate.id)
                ?: return notReady("recreated-reader-session-missing-or-invalid")
        } else {
            AlternateSourceReaderFixtureLaunch(
                destination = AlternateSourceReaderFixtureDestination.READER,
                mangaId = primary.id,
                chapterId = primaryChapter.id,
            )
        }
        val mangaLaunch = AlternateSourceReaderFixtureLaunch(
            destination = AlternateSourceReaderFixtureDestination.MANGA,
            mangaId = primary.id,
        )
        if (!mangaLaunch.isStructurallyValid() || !readerLaunch.isStructurallyValid()) {
            return notReady("launch-arguments-invalid")
        }
        return AlternateSourceReaderFixtureRouteResolution.Ready(mangaLaunch, readerLaunch)
    }

    private fun notReady(reason: String): AlternateSourceReaderFixtureRouteResolution.NotReady {
        diagnostic("route outcome=NotReady reason=$reason")
        return AlternateSourceReaderFixtureRouteResolution.NotReady
    }

    private suspend fun processRecreatedLaunch(
        manifest: AlternateSourceReaderFixtureManifest,
        primaryMangaId: Long,
        primaryChapterId: Long,
        alternateMangaId: Long,
    ): AlternateSourceReaderFixtureLaunch? {
        val alternateChapter = chapterRepository.getChapterByUrlAndMangaId(
            AlternateSourceReaderFixturePaths.ALTERNATE_CHAPTER_2,
            alternateMangaId,
        ) ?: return null
        val key = AlternateSourceBridgeKey(
            primary = CrossSourceRecordKey(manifest.primarySourceId, manifest.primaryMangaUrl),
            alternate = CrossSourceRecordKey(manifest.alternateSourceId, manifest.alternateMangaUrl),
        )
        val bridge = getBridge.await(key) ?: return null
        val mapping = bridge.mappings.singleOrNull {
            it.alternateChapterUrl == AlternateSourceReaderFixturePaths.ALTERNATE_CHAPTER_2 &&
                it.state == AlternateSourceBridgeMappingState.CONFIRMED &&
                it.deletedAt == null
        } ?: return null
        val primaryRoute = AlternateSourceReaderRoute(
            role = AlternateSourceReaderRouteRole.PRIMARY,
            record = key.primary,
            mangaId = primaryMangaId,
            chapterUrl = AlternateSourceReaderFixturePaths.PRIMARY_CHAPTER_1,
            chapterId = primaryChapterId,
            pageIndex = 0,
        )
        val alternateRoute = AlternateSourceReaderRoute(
            role = AlternateSourceReaderRouteRole.ALTERNATE,
            record = key.alternate,
            mangaId = alternateMangaId,
            chapterUrl = AlternateSourceReaderFixturePaths.ALTERNATE_CHAPTER_2,
            chapterId = alternateChapter.id,
            pageIndex = 0,
        )
        val session = AlternateSourceReaderSession.create(
            bridgeKey = key,
            primaryResumeRoute = primaryRoute,
            alternateRoute = alternateRoute,
            activeTargetId = mapping.key.targetId,
            bridgeUpdatedAt = bridge.bridge.updatedAt,
            mappingUpdatedAt = mapping.updatedAt,
        )
        val encoded = linkedMapOf<String, Any?>()
        AlternateSourceReaderStateCodec.write(session, encoded::set)
        return AlternateSourceReaderFixtureLaunch(
            destination = AlternateSourceReaderFixtureDestination.READER,
            mangaId = alternateMangaId,
            chapterId = alternateChapter.id,
            readerState = encoded,
        )
    }
}

fun interface AlternateSourceReaderFixtureRouteLauncher {
    fun launch(route: AlternateSourceReaderFixtureLaunch)
}

class AndroidAlternateSourceReaderFixtureRouteLauncher(
    context: Context,
) : AlternateSourceReaderFixtureRouteLauncher {
    private val appContext = context.applicationContext

    override fun launch(route: AlternateSourceReaderFixtureLaunch) {
        require(route.isStructurallyValid())
        val intent = when (route.destination) {
            AlternateSourceReaderFixtureDestination.MANGA -> Intent(appContext, MainActivity::class.java)
                .setAction(Constants.SHORTCUT_MANGA)
                .putExtra(Constants.MANGA_EXTRA, route.mangaId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            AlternateSourceReaderFixtureDestination.READER -> ReaderActivity.newIntent(
                appContext,
                route.mangaId,
                requireNotNull(route.chapterId),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).also { intent ->
                route.readerState.forEach { (key, value) -> intent.putPrimitiveExtra(key, value) }
            }
        }
        appContext.startActivity(intent)
    }
}

private fun Intent.putPrimitiveExtra(key: String, value: Any?) {
    when (value) {
        null -> Unit
        is String -> putExtra(key, value)
        is Int -> putExtra(key, value)
        is Long -> putExtra(key, value)
        is Boolean -> putExtra(key, value)
        else -> error("unsupported-reader-state-primitive")
    }
}
