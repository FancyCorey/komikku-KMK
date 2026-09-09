package eu.kanade.domain

import exh.recs.matching.ConfirmedGroupLocalTrackingPropagator
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ConfirmedGroupLocalTrackingPropagatorRegistrationTest {
    @Test
    fun `KMK domain module registers the propagator requested by loved manga screens`() {
        val module = File("src/main/java/eu/kanade/domain/KMKDomainModule.kt").readText()

        assertTrue(
            module.contains("addFactory { ConfirmedGroupLocalTrackingPropagator(get()) }") &&
                module.contains("import exh.recs.matching.ConfirmedGroupLocalTrackingPropagator"),
            "KMKDomainModule must register ConfirmedGroupLocalTrackingPropagator for Injekt resolution",
        )
    }
}
