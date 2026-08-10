package exh.ocr

import kotlinx.coroutines.CancellationException
import java.io.FileNotFoundException
import java.io.IOException

// KMK --> v0.7.46: public-polish pass — stable, typed OCR error keys instead of raw exception text
/**
 * Stable, storage-safe classification for OCR failures.
 *
 * OCR is privacy-sensitive (it stores recognized manga page text locally), so neither the UI nor
 * `ocr_indexed_page.error_message` should hold an arbitrary caught exception's `.message` — that text
 * is not written for end users and, for some failure types, could incidentally include file paths or
 * other environment-specific detail. Every OCR failure is classified into one of these fixed keys;
 * the raw exception is still logged via logcat at the catch site (sanitized — see
 * [OcrIndexService]) for developer diagnostics only.
 */
enum class OcrErrorKey(val storageKey: String) {
    Storage("ocr_error_storage"),
    ImageDecode("ocr_error_image_decode"),
    NoDownloadedPages("ocr_error_no_downloaded_pages"),
    Cancelled("ocr_error_cancelled"),
    PermissionOrFileAccess("ocr_error_file_access"),
    Internal("ocr_error_internal"),
    ;

    companion object {
        /** Resolves a previously-stored [storageKey] back to its key, or null if unrecognized (legacy raw text). */
        fun fromStorageKey(key: String?): OcrErrorKey? = entries.find { it.storageKey == key }
    }
}

object OcrErrorClassifier {

    fun classify(e: Throwable): OcrErrorKey = when {
        e is CancellationException -> OcrErrorKey.Cancelled
        e is FileNotFoundException -> OcrErrorKey.PermissionOrFileAccess
        e is SecurityException -> OcrErrorKey.PermissionOrFileAccess
        e is OutOfMemoryError -> OcrErrorKey.ImageDecode
        isImageDecodeException(e) -> OcrErrorKey.ImageDecode
        e is IOException -> OcrErrorKey.Storage
        else -> OcrErrorKey.Internal
    }

    /** Stable storage key to persist in `ocr_indexed_page.error_message` instead of raw exception text. */
    fun classifyToStorageKey(e: Throwable): String = classify(e).storageKey

    private fun isImageDecodeException(e: Throwable): Boolean {
        val className = e.javaClass.name
        return className.contains("BitmapFactory") ||
            className.contains("ImageDecoder") ||
            (className.contains("Codec", ignoreCase = true) && className.contains("android", ignoreCase = true))
    }
}
// KMK <--
