package exh

import android.content.Context
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.sy.SYMR

object GalleryAddPresentationPolicy {
    private const val MAX_TITLE_LENGTH = 80

    fun safeTitle(title: String, evaluationModeEnabled: Boolean): String? {
        if (evaluationModeEnabled) return null
        val normalized = title
            .map { if (it.category == CharCategory.CONTROL) ' ' else it }
            .filterNot { it.category == CharCategory.FORMAT }
            .joinToString(separator = "")
            .trim()
            .replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) return null
        return if (normalized.length <= MAX_TITLE_LENGTH) normalized else normalized.take(MAX_TITLE_LENGTH - 3) + "..."
    }
}

fun Context.galleryAddFailureMessage(reason: GalleryAddFailureKind): String = when (reason) {
    GalleryAddFailureKind.UNKNOWN_TYPE -> stringResource(SYMR.strings.gallery_adder_unknown_type_safe)
    GalleryAddFailureKind.UNKNOWN_SOURCE -> stringResource(SYMR.strings.gallery_adder_unknown_source_safe)
    GalleryAddFailureKind.NOT_FOUND -> stringResource(SYMR.strings.gallery_adder_not_found_safe)
    GalleryAddFailureKind.CHAPTER_NOT_FOUND -> stringResource(SYMR.strings.gallery_adder_chapter_not_found_safe)
    GalleryAddFailureKind.IMPORT_FAILED -> stringResource(SYMR.strings.gallery_adder_import_failed_safe)
}

fun Context.galleryAddEventMessage(event: GalleryAddEvent, evaluationModeEnabled: Boolean): String = when (event) {
    is GalleryAddEvent.Success -> GalleryAddPresentationPolicy.safeTitle(event.manga.title, evaluationModeEnabled)
        ?.let { stringResource(SYMR.strings.batch_add_success_log_message, it) }
        ?: stringResource(SYMR.strings.gallery_adder_success_safe)
    is GalleryAddEvent.Fail -> galleryAddFailureMessage(event.reason)
}
