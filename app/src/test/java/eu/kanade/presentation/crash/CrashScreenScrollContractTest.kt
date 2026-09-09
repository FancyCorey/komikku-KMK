package eu.kanade.presentation.crash

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class CrashScreenScrollContractTest {

    @Test
    fun `crash details use the InfoScreen scroll owner`() {
        val source = File("src/main/java/eu/kanade/presentation/crash/CrashScreen.kt").readText()

        assertTrue(source.contains(".fillMaxWidth()"))
        assertFalse(source.contains(".verticalScroll(rememberScrollState())"))
        assertFalse(source.contains(".fillMaxSize()"))
    }
}
