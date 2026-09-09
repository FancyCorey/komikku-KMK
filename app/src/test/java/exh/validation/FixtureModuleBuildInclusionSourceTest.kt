package exh.validation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class FixtureModuleBuildInclusionSourceTest {
    @Test
    fun `standalone fixture module is included only when its directory exists`() {
        val source = File("../settings.gradle.kts").readText()
        val guardedInclude = Regex(
            """if \(file\("fixture-sources-to-try-extension"\)\.isDirectory\) \{\s*include\(":fixture-sources-to-try-extension"\)\s*}""",
        )

        assertTrue(guardedInclude.containsMatchIn(source))
        assertEquals(
            1,
            source.lineSequence().count { line ->
                line.trim() == "include(\":fixture-sources-to-try-extension\")"
            },
        )
    }
}
