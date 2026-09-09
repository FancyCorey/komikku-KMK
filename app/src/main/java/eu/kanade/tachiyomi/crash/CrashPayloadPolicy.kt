package eu.kanade.tachiyomi.crash

object CrashPayloadPolicy {
    const val MAX_DETAILS_LENGTH = 24_000
    const val MAX_ENCODED_LENGTH = MAX_DETAILS_LENGTH + 1_024

    fun detailsForTransport(throwable: Throwable): String =
        throwable.stackTraceToString().take(MAX_DETAILS_LENGTH)

    fun acceptEncodedPayload(payload: String?): String? = payload
        ?.takeIf { it.isNotBlank() && it.length <= MAX_ENCODED_LENGTH }

    fun acceptDecodedDetails(details: String): String? = details
        .takeIf { it.isNotBlank() && it.length <= MAX_DETAILS_LENGTH }
}
