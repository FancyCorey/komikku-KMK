package eu.kanade.tachiyomi.ui.reader.bridge

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga

class AlternateSourceReaderRouteResolverTest {

    private val getManga = mockk<GetManga>()
    private val getChapter = mockk<GetChapter>()
    private val resolver = AlternateSourceReaderRouteResolver(getManga, getChapter)

    @Test
    fun `resolver grants navigation authority only after exact manga and chapter lookup`() = runTest {
        val manga = Manga.create().copy(id = 2_002L, source = 2L, url = "/manga/alternate")
        val chapter = Chapter.create().copy(id = 22L, mangaId = manga.id, url = "/chapter/alternate")
        coEvery { getManga.await("/manga/alternate", 2L) } returns manga
        coEvery { getChapter.await("/chapter/alternate", manga.id) } returns chapter

        assertEquals(
            AlternateSourceReaderRouteResolver.Result.Resolved(
                readerRoute(AlternateSourceReaderRouteRole.ALTERNATE, pageIndex = 3),
            ),
            resolver.resolve(
                readerBridgeKey(),
                AlternateSourceReaderRouteRole.ALTERNATE,
                "/chapter/alternate",
                3,
            ),
        )
        coVerify(exactly = 1) { getManga.await("/manga/alternate", 2L) }
        coVerify(exactly = 1) { getChapter.await("/chapter/alternate", manga.id) }
    }

    @Test
    fun `invalid or mismatched records never become routes`() = runTest {
        assertEquals(
            AlternateSourceReaderRouteResolver.Result.InvalidRequest,
            resolver.resolve(readerBridgeKey(), AlternateSourceReaderRouteRole.ALTERNATE, "", 0),
        )
        coVerify(exactly = 0) { getManga.await(any(), any()) }

        val mismatched = Manga.create().copy(id = 2_002L, source = 99L, url = "/manga/alternate")
        coEvery { getManga.await("/manga/alternate", 2L) } returns mismatched
        assertEquals(
            AlternateSourceReaderRouteResolver.Result.IdentityMismatch,
            resolver.resolve(
                readerBridgeKey(),
                AlternateSourceReaderRouteRole.ALTERNATE,
                "/chapter/alternate",
                0,
            ),
        )
        coVerify(exactly = 0) { getChapter.await(any(), any()) }

        val manga = Manga.create().copy(id = 2_002L, source = 2L, url = "/manga/alternate")
        val mismatchedChapter = Chapter.create().copy(
            id = 22L,
            mangaId = 99L,
            url = "/chapter/alternate",
        )
        coEvery { getManga.await("/manga/alternate", 2L) } returns manga
        coEvery { getChapter.await("/chapter/alternate", manga.id) } returns mismatchedChapter
        assertEquals(
            AlternateSourceReaderRouteResolver.Result.IdentityMismatch,
            resolver.resolve(
                readerBridgeKey(),
                AlternateSourceReaderRouteRole.ALTERNATE,
                "/chapter/alternate",
                0,
            ),
        )
    }

    @Test
    fun `missing records return typed unavailable results without inferred fallback`() = runTest {
        coEvery { getManga.await("/manga/alternate", 2L) } returns null
        assertEquals(
            AlternateSourceReaderRouteResolver.Result.MangaUnavailable,
            resolver.resolve(
                readerBridgeKey(),
                AlternateSourceReaderRouteRole.ALTERNATE,
                "/chapter/alternate",
                0,
            ),
        )

        val manga = Manga.create().copy(id = 2_002L, source = 2L, url = "/manga/alternate")
        coEvery { getManga.await("/manga/alternate", 2L) } returns manga
        coEvery { getChapter.await("/chapter/alternate", manga.id) } returns null
        assertEquals(
            AlternateSourceReaderRouteResolver.Result.ChapterUnavailable,
            resolver.resolve(
                readerBridgeKey(),
                AlternateSourceReaderRouteRole.ALTERNATE,
                "/chapter/alternate",
                0,
            ),
        )
    }

    @Test
    fun `ordinary failure is typed while cancellation and fatal errors propagate`() {
        coEvery { getManga.await(any(), any()) } throws IllegalStateException("private detail")
        assertEquals(
            AlternateSourceReaderRouteResolver.Result.Failed,
            kotlinx.coroutines.runBlocking {
                resolver.resolve(
                    readerBridgeKey(),
                    AlternateSourceReaderRouteRole.ALTERNATE,
                    "/chapter/alternate",
                    0,
                )
            },
        )

        coEvery { getManga.await(any(), any()) } throws CancellationException("stop")
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                resolver.resolve(
                    readerBridgeKey(),
                    AlternateSourceReaderRouteRole.ALTERNATE,
                    "/chapter/alternate",
                    0,
                )
            }
        }

        coEvery { getManga.await(any(), any()) } throws AssertionError("fatal")
        assertThrows(AssertionError::class.java) {
            kotlinx.coroutines.runBlocking {
                resolver.resolve(
                    readerBridgeKey(),
                    AlternateSourceReaderRouteRole.ALTERNATE,
                    "/chapter/alternate",
                    0,
                )
            }
        }
    }
}
