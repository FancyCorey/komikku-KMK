package exh.util

/**
 * Keeps the Developer Options opt-in explicit without coupling it to
 * Evaluation Mode or to a particular diagnostic screen.
 */
object DeveloperOptionsGatePolicy {
    enum class RequestResult {
        PERSIST,
        REQUIRE_CONFIRMATION,
    }

    fun request(currentEnabled: Boolean, requestedEnabled: Boolean): RequestResult =
        if (requestedEnabled && !currentEnabled) {
            RequestResult.REQUIRE_CONFIRMATION
        } else {
            RequestResult.PERSIST
        }

    fun canExposeDiagnostics(developerOptionsEnabled: Boolean, evaluationModeEnabled: Boolean): Boolean =
        developerOptionsEnabled && !evaluationModeEnabled
}
