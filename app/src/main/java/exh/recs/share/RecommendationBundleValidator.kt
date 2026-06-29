package exh.recs.share

import kotlinx.serialization.json.Json

// KMK -->

object RecommendationBundleValidator {

    const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024 // 2 MB
    const val MAX_ITEMS = 500
    const val MAX_SOURCES = 200

    sealed interface ValidationResult {
        data class Valid(val bundle: RecommendationBundle) : ValidationResult
        data class WrongSchema(val found: String) : ValidationResult
        data class UnsupportedVersion(val found: Int) : ValidationResult
        data object TooManyItems : ValidationResult
        data object TooManySources : ValidationResult
        data object FileTooLarge : ValidationResult
        data class MalformedJson(val message: String) : ValidationResult
    }

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun validate(jsonString: String, fileSizeBytes: Int = jsonString.encodeToByteArray().size): ValidationResult {
        if (fileSizeBytes > MAX_FILE_SIZE_BYTES) return ValidationResult.FileTooLarge

        val bundle = try {
            lenientJson.decodeFromString<RecommendationBundle>(jsonString)
        } catch (e: Exception) {
            return ValidationResult.MalformedJson(e.message ?: "Unknown JSON error")
        }

        if (bundle.schema != RecommendationBundle.SCHEMA_ID) {
            return ValidationResult.WrongSchema(bundle.schema)
        }
        if (bundle.schemaVersion != RecommendationBundle.SCHEMA_VERSION) {
            return ValidationResult.UnsupportedVersion(bundle.schemaVersion)
        }
        if (bundle.items.size > MAX_ITEMS) return ValidationResult.TooManyItems
        if (bundle.requiredSources.size > MAX_SOURCES) return ValidationResult.TooManySources

        return ValidationResult.Valid(bundle)
    }
}

// KMK <--
