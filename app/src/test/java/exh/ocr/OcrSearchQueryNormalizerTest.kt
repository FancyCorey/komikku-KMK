package exh.ocr

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

// KMK --> OCR v0.1.1 (updated from v0.1.0)

class OcrSearchQueryNormalizerTest {

    @Test
    fun `normalize lowercases text`() {
        OcrSearchQueryNormalizer.normalize("Hello World") shouldBe "hello world"
    }

    @Test
    fun `normalize collapses multiple spaces`() {
        OcrSearchQueryNormalizer.normalize("hello   world") shouldBe "hello world"
    }

    @Test
    fun `normalize strips punctuation`() {
        OcrSearchQueryNormalizer.normalize("Hello, World!") shouldBe "hello world"
    }

    @Test
    fun `normalize trims leading and trailing whitespace`() {
        OcrSearchQueryNormalizer.normalize("  hello  ") shouldBe "hello"
    }

    @Test
    fun `normalize empty string`() {
        OcrSearchQueryNormalizer.normalize("") shouldBe ""
    }

    @Test
    fun `buildSnippet returns text around match`() {
        val text = "the quick brown fox jumps over the lazy dog"
        val snippet = OcrSearchQueryNormalizer.buildSnippet(text, "fox", contextChars = 10)
        snippet shouldContain "fox"
    }

    @Test
    fun `buildSnippet returns start of text when no match`() {
        val text = "hello world"
        val snippet = OcrSearchQueryNormalizer.buildSnippet(text, "zzz", contextChars = 5)
        snippet shouldContain "hello"
    }

    @Test
    fun `buildSnippet prepends ellipsis when match is not at start`() {
        val longText = "a".repeat(200) + "fox" + "b".repeat(200)
        val snippet = OcrSearchQueryNormalizer.buildSnippet(longText, "fox")
        snippet.startsWith("…") shouldBe true
    }

    @Test
    fun `tokenize returns meaningful tokens from query`() {
        val tokens = OcrSearchQueryNormalizer.tokenize("The quick brown fox")
        // "the" is stop word, rest are useful
        tokens shouldContain "quick"
        tokens shouldContain "brown"
        tokens shouldContain "fox"
    }

    @Test
    fun `tokenize excludes stop words`() {
        val tokens = OcrSearchQueryNormalizer.tokenize("the and or")
        tokens shouldBe emptyList()
    }

    @Test
    fun `buildSnippet with rawText prefers raw for display`() {
        val raw = "Hello, World! This is a test."
        val normalized = OcrSearchQueryNormalizer.normalize(raw)
        val snippet = OcrSearchQueryNormalizer.buildSnippet(raw, normalized, "world")
        snippet shouldContain "World"
    }
}

// KMK <--
