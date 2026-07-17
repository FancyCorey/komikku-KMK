package exh.recs.share

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import exh.recs.PersonalRecommendation
import exh.recs.loved.LovedDisplayItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

class RecommendationBundleExporter(
    private val sourceManager: SourceManager = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
) {

    private val json = Json {
        prettyPrint = false
        encodeDefaults = false
    }

    fun buildTopPicksBundle(recs: List<PersonalRecommendation>, kmkVersion: String): RecommendationBundle {
        val items = recs.mapNotNull { rec ->
            buildItem(rec.manga, score = rec.score, matchedGroups = rec.matchedGroups)
        }
        return makeBundle(RecommendationBundleType.TOP_PICKS, "Top Picks", items, kmkVersion)
    }

    fun buildSourceRowBundle(
        sourceName: String,
        sourceLang: String,
        recs: List<PersonalRecommendation>,
        kmkVersion: String,
    ): RecommendationBundle {
        val items = recs.mapNotNull { rec ->
            buildItem(rec.manga, score = rec.score, matchedGroups = rec.matchedGroups)
        }
        return makeBundle(
            RecommendationBundleType.SOURCE_ROW,
            "$sourceName (${sourceLang.uppercase()}) Recommendations",
            items,
            kmkVersion,
        )
    }

    fun buildLovedMangaBundle(
        displayItems: List<LovedDisplayItem>,
        linkGroupByKey: Map<String, String>,
        kmkVersion: String,
    ): RecommendationBundle {
        val items = displayItems.mapNotNull { entry ->
            val manga = entry.manga ?: return@mapNotNull null
            val key = "${entry.taste.source}|${entry.taste.url}"
            buildItem(manga, crossSourceGroupId = linkGroupByKey[key])
        }
        return makeBundle(RecommendationBundleType.LOVED_MANGA, "Loved Manga", items, kmkVersion)
    }

    /** Export Top Picks when only resolved Manga objects are available (no scores/groups). */
    fun buildTopPicksFromMangaBundle(mangas: List<Manga>, kmkVersion: String): RecommendationBundle {
        val items = mangas.mapNotNull { manga -> buildItem(manga) }
        return makeBundle(RecommendationBundleType.TOP_PICKS, "Top Picks", items, kmkVersion)
    }

    private fun makeBundle(
        type: RecommendationBundleType,
        title: String,
        items: List<RecommendationBundleItem>,
        kmkVersion: String,
    ) = RecommendationBundle(
        kmkRecsVersion = kmkVersion,
        createdAt = System.currentTimeMillis(),
        title = title,
        bundleType = type,
        requiredSources = collectSources(items),
        items = items,
    )

    private fun buildItem(
        manga: Manga,
        score: Double? = null,
        matchedGroups: List<String> = emptyList(),
        crossSourceGroupId: String? = null,
    ): RecommendationBundleItem? {
        if (manga.url.isBlank()) return null
        val ext = findInstalledExtension(manga.source)
        val source = sourceManager.get(manga.source)
        return RecommendationBundleItem(
            title = manga.title,
            url = manga.url,
            sourceId = manga.source,
            sourceName = source?.name,
            sourceLang = source?.lang,
            extensionPkgName = ext?.pkgName,
            extensionName = ext?.name,
            extensionSignatureHash = ext?.signatureHash,
            repoName = ext?.storeName,
            thumbnailUrl = manga.thumbnailUrl?.takeIf { it.isNotBlank() },
            author = manga.author?.takeIf { it.isNotBlank() },
            artist = manga.artist?.takeIf { it.isNotBlank() },
            description = manga.description?.takeIf { it.isNotBlank() },
            genres = manga.genre.orEmpty().filter { it.isNotBlank() },
            status = manga.status.takeIf { it != 0L }?.toInt(),
            score = score,
            matchedGroups = matchedGroups,
            crossSourceGroupId = crossSourceGroupId,
        )
    }

    private fun findInstalledExtension(sourceId: Long): Extension.Installed? =
        extensionManager.installedExtensionsFlow.value.find { ext ->
            ext.sources.any { it.id == sourceId }
        }

    private fun collectSources(items: List<RecommendationBundleItem>): List<RecommendationBundleSource> =
        items.distinctBy { it.sourceId }.mapNotNull { item ->
            if (item.sourceId == 0L) return@mapNotNull null
            RecommendationBundleSource(
                sourceId = item.sourceId,
                sourceName = item.sourceName,
                sourceLang = item.sourceLang,
                extensionPkgName = item.extensionPkgName,
                extensionName = item.extensionName,
                extensionSignatureHash = item.extensionSignatureHash,
                repoName = item.repoName,
            )
        }

    fun serializeBundle(bundle: RecommendationBundle): String = json.encodeToString(bundle)

    suspend fun writeToUri(context: Context, uri: Uri, bundle: RecommendationBundle): Result<Unit> = runCatching {
        val content = serializeBundle(bundle)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        } ?: error("Could not open output stream")
    }
}

// KMK <--
