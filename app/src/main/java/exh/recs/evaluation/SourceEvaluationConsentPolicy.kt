package exh.recs.evaluation

// KMK --> v0.7.11: consent gate for Source Evaluation pre-run warning
object SourceEvaluationConsentPolicy {
    fun isConsentRequired(consentGiven: Boolean): Boolean = !consentGiven
}
// KMK <--
