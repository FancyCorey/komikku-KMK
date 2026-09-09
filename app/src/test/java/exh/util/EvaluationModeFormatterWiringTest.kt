package exh.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class EvaluationModeFormatterWiringTest {

    @Test
    fun `application startup installs the Android label resolver before dependency setup`() {
        val source = File("src/main/java/eu/kanade/tachiyomi/App.kt").readText()
        val superCall = source.indexOf("super<Application>.onCreate()")
        val resolverInstall = source.indexOf("EvaluationModeFormatter.initialize(this)")
        val dependencySetup = source.indexOf("patchInjekt()")

        assertTrue(superCall >= 0)
        assertTrue(resolverInstall > superCall)
        assertTrue(dependencySetup > resolverInstall)
    }

    @Test
    fun `generic label templates have typed placeholders and only the formatter resolves them`() {
        val resources = File("../i18n-kmk/src/commonMain/moko-resources/base/strings.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resources)
        val strings = document.getElementsByTagName("string")
        val values = buildMap {
            repeat(strings.length) { index ->
                val node = strings.item(index)
                put(node.attributes.getNamedItem("name").nodeValue, node.textContent)
            }
        }

        assertEquals("Source %1\$s", values["evaluation_mode_source_label"])
        assertEquals("Repo %1\$d", values["evaluation_mode_repo_label"])
        assertEquals("Like Tag %1\$d", values["evaluation_mode_liked_tag_label"])
        assertEquals("Dislike Tag %1\$d", values["evaluation_mode_blocked_tag_label"])

        val directReferences = File("src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { index, line ->
                    if (GENERIC_LABEL_RESOURCE_NAMES.any(line::contains)) "${file.invariantSeparatorsPath}:${index + 1}" else null
                }
            }
            .toList()

        assertEquals(4, directReferences.size)
        assertTrue(directReferences.all { it.contains("exh/util/EvaluationModeFormatter.kt") })
    }

    private companion object {
        val GENERIC_LABEL_RESOURCE_NAMES = listOf(
            "evaluation_mode_source_label",
            "evaluation_mode_repo_label",
            "evaluation_mode_liked_tag_label",
            "evaluation_mode_blocked_tag_label",
        )
    }
}
