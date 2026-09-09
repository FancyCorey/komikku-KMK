package eu.kanade.tachiyomi.library

/** Stable values persisted for library-update failures. Unknown legacy values must never be rendered verbatim. */
enum class LibraryUpdateErrorMessageKey(val storageValue: String) {
    NoChapters("library_update_error_no_chapters"),
    SourceNotFound("library_update_error_source_not_found"),
    Unknown("library_update_error_unknown"),
    ;

    companion object {
        fun fromStorageValue(value: String?): LibraryUpdateErrorMessageKey =
            entries.firstOrNull { it.storageValue == value } ?: Unknown
    }
}
