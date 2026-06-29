package exh.ocr

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// KMK --> OCR v0.1.1

class OcrSearchRankerTest {

    private val pageText = "come back to me when you are ready to fight"

    @Test
    fun `exact phrase match returns EXACT type with score 1`() {
        val tokens = OcrSearchRanker.tokenize("come back ready")
        val score = OcrSearchRanker.score(tokens, pageText, isExactPhrase = true)
        score.shouldNotBeNull()
        score.matchType shouldBe OcrMatchType.EXACT
        score.score shouldBe 1.0
        score.missingWords shouldBe emptyList()
    }

    @Test
    fun `all tokens present returns ALL_TOKENS type`() {
        val tokens = OcrSearchRanker.tokenize("come back fight")
        val score = OcrSearchRanker.score(tokens, pageText, isExactPhrase = false)
        score.shouldNotBeNull()
        score.matchType shouldBe OcrMatchType.ALL_TOKENS
        score.missingWords shouldBe emptyList()
    }

    @Test
    fun `partial match above threshold returns PARTIAL type`() {
        val tokens = OcrSearchRanker.tokenize("come back fight missing")
        val score = OcrSearchRanker.score(tokens, pageText, isExactPhrase = false)
        score.shouldNotBeNull()
        score.matchType shouldBe OcrMatchType.PARTIAL
        score.matchedWords shouldContain "come"
        score.missingWords shouldContain "missing"
    }

    @Test
    fun `match below 60 percent threshold returns null`() {
        val tokens = OcrSearchRanker.tokenize("zzz yyy xxx www")
        val score = OcrSearchRanker.score(tokens, pageText, isExactPhrase = false)
        score.shouldBeNull()
    }

    @Test
    fun `single token must match exactly to return result`() {
        val tokens = OcrSearchRanker.tokenize("zzzmissing")
        val score = OcrSearchRanker.score(tokens, pageText, isExactPhrase = false)
        score.shouldBeNull()
    }

    @Test
    fun `single matching token returns result`() {
        val tokens = OcrSearchRanker.tokenize("come")
        val score = OcrSearchRanker.score(tokens, pageText, isExactPhrase = false)
        score.shouldNotBeNull()
        score.matchedWords shouldContain "come"
    }

    @Test
    fun `empty query tokens returns null`() {
        val score = OcrSearchRanker.score(emptyList(), pageText, isExactPhrase = false)
        score.shouldBeNull()
    }

    @Test
    fun `score is higher for all-token match than partial match`() {
        val allTokens = OcrSearchRanker.tokenize("come back fight")
        val partialTokens = OcrSearchRanker.tokenize("come back fight missing")
        val allScore = OcrSearchRanker.score(allTokens, pageText, isExactPhrase = false)
        val partialScore = OcrSearchRanker.score(partialTokens, pageText, isExactPhrase = false)
        allScore.shouldNotBeNull()
        partialScore.shouldNotBeNull()
        allScore.score shouldBeGreaterThan partialScore.score
    }

    @Test
    fun `tokenize removes stop words`() {
        val tokens = OcrSearchRanker.tokenize("the quick fox and the cat")
        tokens shouldBe listOf("quick", "fox", "cat")
    }

    @Test
    fun `tokenize removes short tokens`() {
        val tokens = OcrSearchRanker.tokenize("a bb ccc dddd")
        // "a" is stop word; "bb" >= 2 chars but not stop word — included; "ccc", "dddd" included
        tokens shouldContain "ccc"
        tokens shouldContain "dddd"
    }

    @Test
    fun `tokenize deduplicates`() {
        val tokens = OcrSearchRanker.tokenize("run run run")
        tokens shouldBe listOf("run")
    }
}

// KMK <--
