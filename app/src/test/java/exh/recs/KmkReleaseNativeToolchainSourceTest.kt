package exh.recs

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KmkReleaseNativeToolchainSourceTest {

    @Test
    fun `release workflow installs the native configuration tool`() {
        val workflow = File("../.github/workflows/build_release.yml").readText()

        assertTrue(workflow.contains("name: Set up native build tools"))
        assertTrue(workflow.contains("meson==1.12.0"))
        assertTrue(workflow.contains("meson/bin\" >> \"${'$'}GITHUB_PATH"))
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
}
