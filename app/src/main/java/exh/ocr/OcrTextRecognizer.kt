package exh.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// KMK --> OCR v0.1.1

data class OcrImageInput(val stream: InputStream, val pageIdentity: String)

data class OcrPageText(val rawText: String)

interface OcrTextRecognizer {
    suspend fun recognizeText(input: OcrImageInput): OcrPageText
    val engineKey: String
    val engineVersion: String
}

class MlKitLatinOcrTextRecognizer(
    @Suppress("UNUSED_PARAMETER") context: Context,
) : OcrTextRecognizer {

    override val engineKey: String get() = ENGINE_KEY
    override val engineVersion: String get() = ENGINE_VERSION

    companion object {
        const val ENGINE_KEY = "mlkit-latin"
        // Version includes preprocessing variant so old v0.1.0 rows (engine_version="16.0.1")
        // are not treated as current — all pages will be re-indexed with the improved tiling.
        const val ENGINE_VERSION = "16.0.1-tiled-v2"
    }

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognizeText(input: OcrImageInput): OcrPageText {
        val bytes = try {
            input.stream.use { it.readBytes() }
        } catch (e: Exception) {
            return OcrPageText(rawText = "")
        }
        if (bytes.isEmpty()) return OcrPageText(rawText = "")

        // Decode bounds only
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        val origWidth = boundsOpts.outWidth
        val origHeight = boundsOpts.outHeight
        if (origWidth <= 0 || origHeight <= 0) return OcrPageText(rawText = "")

        // Width-based sample size only — height does not determine downsampling.
        // This prevents long webtoon/manhwa pages (e.g. 900 x 12000) from being
        // shrunk to an unreadably narrow width.
        val sampleSize = OcrTilePlanner.widthSampleSize(origWidth)

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val fullBitmap = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (e: OutOfMemoryError) {
            return OcrPageText(rawText = "")
        } ?: return OcrPageText(rawText = "")

        return try {
            val tiles = OcrTilePlanner.planTiles(fullBitmap.height)
            if (tiles.size == 1) {
                // No tiling needed — OCR the full bitmap directly
                val text = runMlKit(fullBitmap)
                OcrPageText(rawText = text)
            } else {
                val textParts = mutableListOf<String>()
                for (tile in tiles) {
                    val tileBitmap = try {
                        Bitmap.createBitmap(fullBitmap, 0, tile.y, fullBitmap.width, tile.height)
                    } catch (e: OutOfMemoryError) {
                        break
                    }
                    try {
                        val tileText = runMlKit(tileBitmap)
                        if (tileText.isNotBlank()) textParts.add(tileText)
                    } finally {
                        tileBitmap.recycle()
                    }
                }
                OcrPageText(rawText = textParts.joinToString("\n"))
            }
        } finally {
            fullBitmap.recycle()
        }
    }

    private suspend fun runMlKit(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { visionText -> cont.resume(visionText.text) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
                .addOnCanceledListener { cont.cancel() }
        }
    }
}

// KMK <--
