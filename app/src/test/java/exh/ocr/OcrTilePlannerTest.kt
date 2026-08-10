package exh.ocr

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

// KMK --> OCR v0.1.1

class OcrTilePlannerTest {

    @Test
    fun `short image produces single tile`() {
        val tiles = OcrTilePlanner.planTiles(bitmapHeight = 1000)
        tiles.size shouldBe 1
        tiles[0].y shouldBe 0
        tiles[0].height shouldBe 1000
    }

    @Test
    fun `image exactly at tile height produces single tile`() {
        val tiles = OcrTilePlanner.planTiles(bitmapHeight = OcrTilePlanner.DEFAULT_TILE_HEIGHT)
        tiles.size shouldBe 1
    }

    @Test
    fun `long image produces multiple tiles`() {
        val tiles = OcrTilePlanner.planTiles(bitmapHeight = 8000)
        tiles.size shouldBeGreaterThan 1
    }

    @Test
    fun `tiles cover full bitmap height with overlap`() {
        val height = 7000
        val tiles = OcrTilePlanner.planTiles(bitmapHeight = height)
        val lastTile = tiles.last()
        (lastTile.y + lastTile.height) shouldBe height
    }

    @Test
    fun `no tile exceeds bitmap height`() {
        val height = 5500
        val tiles = OcrTilePlanner.planTiles(bitmapHeight = height)
        tiles.forEach { tile ->
            (tile.y + tile.height) shouldNotBe 0
            tile.y shouldBe tile.y.coerceAtLeast(0)
            (tile.y + tile.height) shouldBe (tile.y + tile.height).coerceAtMost(height)
        }
    }

    @Test
    fun `tiles have positive height`() {
        val tiles = OcrTilePlanner.planTiles(bitmapHeight = 9000)
        tiles.forEach { tile ->
            tile.height shouldBeGreaterThan 0
        }
    }

    @Test
    fun `first tile always starts at y=0`() {
        val tiles = OcrTilePlanner.planTiles(bitmapHeight = 6000)
        tiles.first().y shouldBe 0
    }

    @Test
    fun `widthSampleSize returns 1 when width fits target`() {
        OcrTilePlanner.widthSampleSize(originalWidth = 800) shouldBe 1
        OcrTilePlanner.widthSampleSize(originalWidth = 1800) shouldBe 1
    }

    @Test
    fun `widthSampleSize reduces wide images`() {
        val size = OcrTilePlanner.widthSampleSize(originalWidth = 4000)
        size shouldBeGreaterThan 1
    }

    @Test
    fun `widthSampleSize result keeps decoded width near target`() {
        val origWidth = 3600
        val sampleSize = OcrTilePlanner.widthSampleSize(origWidth)
        val decodedWidth = origWidth / sampleSize
        // Should stay within 2x of target width
        (decodedWidth >= OcrTilePlanner.DEFAULT_TARGET_WIDTH / 2) shouldBe true
    }
}

// KMK <--
