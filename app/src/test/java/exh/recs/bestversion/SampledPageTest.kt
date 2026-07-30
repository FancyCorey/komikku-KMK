package exh.recs.bestversion

import eu.kanade.domain.manga.model.PagePreview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK v0.8.16-fix1 -->
// Regression coverage for the raw-URL -> source-aware preview model fix: SampledPage must carry a
// PagePreview (index, imageUrl, source) so Coil can route it through PagePreviewFetcher, not a bare
// URL string that bypasses the source-runtime boundary. See UI_AUDIT_NOTES.md for the original bug.
class SampledPageTest {

    @Test
    fun `sampled page display model carries the candidate source id`() {
        val page = SampledPage(index = 2, preview = PagePreview(index = 2, imageUrl = "https://example.com/p.jpg", source = 12345L))
        assertEquals(12345L, page.preview.source)
        assertEquals("https://example.com/p.jpg", page.preview.imageUrl)
        assertEquals(2, page.index)
    }
}
// KMK <--
