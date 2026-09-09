package app.komikku.fixture.sources

import android.util.Base64
import eu.kanade.tachiyomi.source.PagePreviewSource
import eu.kanade.tachiyomi.source.PagePreviewPage
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.PagePreviewInfo
import okhttp3.CacheControl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class FixtureSource : HttpSource(), PagePreviewSource {
    override val id = BuildConfig.SOURCE_ID
    override val name = "Fixture Source"
    override val lang = "en"
    override val supportsLatest = true
    override val baseUrl = LOOPBACK_BASE

    override suspend fun getPopularManga(page: Int) = MangasPage(emptyList(), false)

    override suspend fun getLatestUpdates(page: Int) = MangasPage(emptyList(), false)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) =
        MangasPage(emptyList(), false)

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val fixture = FixtureContent.forSource(id) ?: return SMangaUpdate(manga, chapters)
        if (manga.url != fixture.mangaUrl) return SMangaUpdate(manga, chapters)
        val details = if (fetchDetails) {
            SManga(
                url = fixture.mangaUrl,
                title = fixture.title,
                description = "Synthetic offline reader fixture",
                genre = "Fixture",
                status = SManga.COMPLETED,
                initialized = true,
            )
        } else {
            manga
        }
        return SMangaUpdate(
            manga = details,
            chapters = if (fetchChapters) fixture.chapters else chapters,
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val fixture = FixtureContent.forSource(id) ?: return emptyList()
        val chapterNumber = fixture.chapterNumber(chapter.url) ?: return emptyList()
        return (1..PAGE_COUNT).map { pageNumber ->
            Page(
                index = pageNumber - 1,
                imageUrl = "$LOOPBACK_BASE/pages/${fixture.serverPath}/chapter-$chapterNumber/page-$pageNumber.png",
            )
        }
    }

    override suspend fun fetchPreviewImage(page: PagePreviewInfo, cacheControl: CacheControl?): Response =
        fixtureImageResponse(page.imageUrl)

    override suspend fun getPagePreviewList(
        manga: SManga,
        chapters: List<SChapter>,
        page: Int,
    ): PagePreviewPage {
        val chapter = chapters.getOrNull(page) ?: return PagePreviewPage(page, emptyList(), false, 0)
        val fixture = FixtureContent.forSource(id)
        val chapterNumber = fixture?.chapterNumber(chapter.url)
        val previews = if (chapterNumber == null) {
            emptyList()
        } else {
            (1..PAGE_COUNT).map { pageNumber ->
                PagePreviewInfo(
                    index = pageNumber - 1,
                    imageUrl = "$LOOPBACK_BASE/pages/${fixture.serverPath}/chapter-$chapterNumber/page-$pageNumber.png",
                )
            }
        }
        return PagePreviewPage(page, previews, page + 1 < chapters.size, chapters.size)
    }

    override suspend fun getImage(page: Page): Response = fixtureImageResponse(requireNotNull(page.imageUrl))

    private fun fixtureImageResponse(url: String): Response =
        Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(FIXTURE_PNG.toResponseBody(IMAGE_MEDIA_TYPE))
            .build()

    private data class FixtureContent(
        val mangaUrl: String,
        val title: String,
        val serverPath: String,
        val chapterNumbers: IntRange,
    ) {
        val chapters: List<SChapter>
            get() = chapterNumbers.map { number ->
                SChapter(
                    name = "Chapter $number",
                    url = "$mangaUrl/chapter-$number",
                    date_upload = FIXED_UPLOAD_TIME + number,
                    chapter_number = number.toFloat(),
                )
            }

        fun chapterNumber(url: String): Int? = chapterNumbers.singleOrNull { url == "$mangaUrl/chapter-$it" }

        companion object {
            fun forSource(sourceId: Long): FixtureContent? = when (sourceId) {
                ALPHA_SOURCE_ID -> FixtureContent(
                    mangaUrl = "/kmk-fixture/f2/origin",
                    title = "Fixture Pair A",
                    serverPath = "alpha/origin",
                    chapterNumbers = 1..3,
                )
                BETA_SOURCE_ID -> FixtureContent(
                    mangaUrl = "/kmk-fixture/f2/target",
                    title = "Fixture Pair B",
                    serverPath = "beta/target",
                    chapterNumbers = 1..4,
                )
                else -> null
            }
        }
    }

    private companion object {
        const val ALPHA_SOURCE_ID = 910000000000000001L
        const val BETA_SOURCE_ID = 910000000000000002L
        const val LOOPBACK_BASE = "http://127.0.0.1:38291"
        const val PAGE_COUNT = 3
        const val FIXED_UPLOAD_TIME = 1_700_000_000_000L
        val IMAGE_MEDIA_TYPE = "image/png".toMediaType()
        val FIXTURE_PNG = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
            Base64.DEFAULT,
        )
    }
}
