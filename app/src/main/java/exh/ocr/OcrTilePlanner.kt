package exh.ocr

// KMK --> OCR v0.1.1

data class OcrTileSpec(val y: Int, val height: Int)

object OcrTilePlanner {

    const val DEFAULT_TARGET_WIDTH = 1800
    const val DEFAULT_TILE_HEIGHT = 2500
    const val DEFAULT_TILE_OVERLAP = 120

    /**
     * Returns inSampleSize for width-based downsampling only.
     * Long pages are NOT shrunk based on height — tiling handles that separately.
     */
    fun widthSampleSize(
        originalWidth: Int,
        targetWidth: Int = DEFAULT_TARGET_WIDTH,
    ): Int {
        if (originalWidth <= targetWidth) return 1
        var size = 1
        while (originalWidth / (size * 2) >= targetWidth) {
            size *= 2
        }
        return size
    }

    /**
     * Plans vertical tiles for a decoded bitmap.
     * Short images produce a single tile covering the full height.
     * Long images are split into overlapping strips.
     */
    fun planTiles(
        bitmapHeight: Int,
        tileHeight: Int = DEFAULT_TILE_HEIGHT,
        overlap: Int = DEFAULT_TILE_OVERLAP,
    ): List<OcrTileSpec> {
        require(tileHeight > 0) { "tileHeight must be > 0" }
        require(overlap >= 0) { "overlap must be >= 0" }
        require(overlap < tileHeight) { "overlap must be < tileHeight" }

        if (bitmapHeight <= tileHeight) {
            return listOf(OcrTileSpec(y = 0, height = bitmapHeight))
        }

        val tiles = mutableListOf<OcrTileSpec>()
        var y = 0
        while (y < bitmapHeight) {
            val h = minOf(tileHeight, bitmapHeight - y)
            tiles.add(OcrTileSpec(y = y, height = h))
            if (y + h >= bitmapHeight) break
            y += tileHeight - overlap
        }
        return tiles
    }
}

// KMK <--
