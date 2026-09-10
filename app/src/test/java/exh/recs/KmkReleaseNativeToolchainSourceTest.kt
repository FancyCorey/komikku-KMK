package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest

class KmkReleaseNativeToolchainSourceTest {

    @Test
    fun `release workflow installs the native configuration tool`() {
        val workflow = File("../.github/workflows/build_release.yml").readText()

        assertTrue(workflow.contains("name: Set up native build tools"))
        assertTrue(workflow.contains("sudo apt-get install --yes nasm"))
        assertTrue(workflow.contains("meson==1.12.0"))
        assertTrue(workflow.contains("meson/bin\" >> \"${'$'}GITHUB_PATH"))
        assertTrue(workflow.contains("nasm --version"))
    }

    @Test
    fun `dav1d uses the active Android CMake toolchain on every host`() {
        val source = File("../core/image-decoder/src/main/cpp/libheif/dav1d.cmake").readText()

        assertTrue(source.contains("${'$'}{CMAKE_C_COMPILER}"))
        assertTrue(source.contains("${'$'}{CMAKE_CXX_COMPILER}"))
        assertTrue(source.contains("${'$'}{CMAKE_SYSROOT}"))
        assertFalse(source.contains("prebuilt/windows-x86_64"))
        assertFalse(source.contains("/bin/clang.exe"))
    }

    @Test
    fun `release build carries its unavailable JitPack dependency`() {
        val appBuild = File("../app/build.gradle.kts").readText()
        val catalog = File("../gradle/libs.versions.toml").readText()
        val artifact = File("../app/libs/flexible-adapter-c8013533.aar")

        assertTrue(appBuild.contains("implementation(files(\"libs/flexible-adapter-c8013533.aar\"))"))
        assertFalse(catalog.contains("com.github.arkon.FlexibleAdapter"))
        assertTrue(artifact.isFile)
        assertEquals(
            "41929c785c249e0395faf89fd6bb253aafd65d44d88dbeaa46ecd9658d706cc4",
            MessageDigest.getInstance("SHA-256")
                .digest(artifact.readBytes())
                .joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun `formatting excludes generated native build files`() {
        val lintConvention = File("../buildSrc/src/main/kotlin/mihon.code.lint.gradle.kts").readText()

        assertTrue(lintConvention.contains("add(\"**/.cxx/**/*.xml\")"))
    }

    @Test
    fun `release properties do not enable services in the development variant`() {
        val appBuild = File("../app/build.gradle.kts").readText()
        val debugBlock = appBuild.substringAfter("val debug by getting {").substringBefore("val release by getting {")

        assertTrue(debugBlock.contains("buildConfigField(\"boolean\", \"UPDATER_ENABLED\", \"false\")"))
        assertTrue(debugBlock.contains("buildConfigField(\"boolean\", \"GOOGLE_DRIVE_SYNC_ENABLED\", \"false\")"))
    }

    @Test
    fun `locale configuration is generated during task execution`() {
        val generator = File("../buildSrc/src/main/kotlin/mihon/buildlogic/tasks/LocalesConfigTask.kt").readText()

        assertTrue(generator.contains("inputs.files(localeResources)"))
        assertTrue(generator.contains("outputs.file(outputFile)"))
        assertTrue(generator.contains("doLast {"))
    }
}
