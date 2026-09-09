package exh.perf

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

// KMK F2-04 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM) -->
/**
 * Source-guard proof that the C4 performance fixture generator/seeder never ships in a release
 * artifact. `PerformanceFixtureSpec`/`Dataset`/`Generator`/`Seeder` moved from `app/src/main`
 * (compiled into EVERY build type, including `release`/`releaseTest`/`foss`/`preview`/`benchmark`/
 * `kmkPublicTest` -- every one of those `initWith(release)` in `app/build.gradle.kts`, none of them
 * merge `src/debug`) to `app/src/debug` (compiled only into the `debug` build type, which is what
 * `:app:testDebugUnitTest`/`:app:connectedDebugAndroidTest` -- and this very test -- run against,
 * so host and device test coverage is retained without shipping the classes in any release variant).
 *
 * A live `:app:compileReleaseKotlin` run is the ultimate confirmation (record its result in the
 * dated receipt), but that is a one-off manual check; this test is the durable, always-run
 * regression guard against the fixture files silently drifting back into `src/main` later.
 */
class PerformanceFixtureProductionExclusionTest {

    private val fixtureFileNames = setOf(
        "PerformanceFixtureSpec.kt",
        "PerformanceFixtureDataset.kt",
        "PerformanceFixtureGenerator.kt",
        "PerformanceFixtureSeeder.kt",
    )

    @Test
    fun `no performance fixture file exists under app-src-main`() {
        val mainPerfDir = repoRelative("app/src/main/java/exh/perf")
        assertFalse(
            Files.isDirectory(mainPerfDir),
            "app/src/main/java/exh/perf must not exist -- fixture classes belong in app/src/debug, never a production source set",
        )
    }

    @Test
    fun `every performance fixture file exists under app-src-debug`() {
        val debugPerfDir = repoRelative("app/src/debug/java/exh/perf")
        assertTrue(Files.isDirectory(debugPerfDir), "app/src/debug/java/exh/perf must exist")

        fixtureFileNames.forEach { fileName ->
            assertTrue(
                Files.isRegularFile(debugPerfDir.resolve(fileName)),
                "$fileName must exist under app/src/debug/java/exh/perf",
            )
        }
    }

    @Test
    fun `no other src set (main, release-derived, or otherwise) carries a performance fixture file`() {
        // Defensive breadth check: even if someone later adds a NEW build-type source set (e.g.
        // app/src/foss/java/...), a fixture file placed there would also leak into that release
        // variant. Scans every top-level app/src/* directory except debug/test/androidTest/testFixtures.
        val appSrcDir = repoRelative("app/src")
        assertTrue(Files.isDirectory(appSrcDir), "app/src must exist")

        val allowedSourceSets = setOf("debug", "test", "androidTest", "testFixtures", "test-shared")
        Files.newDirectoryStream(appSrcDir).use { stream ->
            stream.filter { Files.isDirectory(it) }
                .filter { it.fileName.toString() !in allowedSourceSets }
                .forEach { sourceSetDir ->
                    val perfDir = sourceSetDir.resolve("java/exh/perf")
                    assertFalse(
                        Files.isDirectory(perfDir) && Files.list(perfDir).use { it.findAny().isPresent },
                        "${sourceSetDir.fileName}/java/exh/perf must not contain fixture files -- " +
                            "that source set merges into a release-derived build type",
                    )
                }
        }
    }

    private fun repoRelative(relativePath: String): Path {
        val direct = Path.of(relativePath)
        val fromParent = Path.of("..").resolve(relativePath)
        return listOf(direct, fromParent).map(Path::toAbsolutePath).firstOrNull { Files.exists(it) }
            ?: direct.toAbsolutePath()
    }
}
// KMK <--
