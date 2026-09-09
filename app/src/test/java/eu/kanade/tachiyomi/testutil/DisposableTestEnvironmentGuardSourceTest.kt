package eu.kanade.tachiyomi.testutil

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

// KMK F2-02 hardening (2026-08-28), identity-and-content correction (2026-08-27), host-proof
// portability correction (2026-08-27) (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM) -->
/**
 * Source-guard proof that [DisposableTestEnvironmentGuard.assumeDisposableEnvironment] genuinely
 * enforces all required signals (emulator, `.dev` application id, a nonce instrumentation argument,
 * and marker-CONTENT validity) -- not merely that the class exists. Every check here reads only this
 * class's own worktree-relative source file, so this suite is fully portable and runs unmodified in
 * any checkout or CI.
 *
 * KMK F2-02 host-proof portability correction (2026-08-27): an independent review correctly found
 * that an earlier version of this file also cross-checked the guard's source against
 * `private/tools/provision_disposable_test_marker.ps1` -- a private, control-root-only file that
 * does not exist (and must not exist) in another checkout or in CI -- by hardcoding this laptop's
 * own absolute path to it. That made this PUBLIC Gradle suite fail elsewhere solely from a path
 * difference, not a real defect. Those cross-file checks (marker-path drift, instrumentation-argument
 * key drift, AVD-identity-contract drift, fresh-nonce-generation) were moved to
 * `private/tools/validate_f2_02_disposable_marker_contract.py`, which resolves both files' real
 * locations from the procedure router's own `workspace.control_root`/`workspace.active_code_worktree`
 * fields instead of a hardcoded path. Run that validator to confirm the cross-file contract; this
 * suite proves the guard's own in-worktree behavior only.
 */
class DisposableTestEnvironmentGuardSourceTest {

    private val guardText: String by lazy {
        stripComments(source("app/src/androidTest/java/eu/kanade/tachiyomi/testutil/DisposableTestEnvironmentGuard.kt"))
    }

    @Test
    fun `assumeDisposableEnvironment checks all four required signals in order before returning`() {
        val body = functionBody("assumeDisposableEnvironment")

        val isEmulatorIndex = body.indexOf("isEmulator()")
        val devSuffixIndex = body.indexOf("BuildConfig.APPLICATION_ID.endsWith(\".dev\")")
        val nonceArgIndex = body.indexOf("NONCE_ARGUMENT_KEY")
        val markerIndex = body.indexOf("hasValidMarker(")

        assertTrue(isEmulatorIndex >= 0, "must check isEmulator()")
        assertTrue(devSuffixIndex >= 0, "must check BuildConfig.APPLICATION_ID.endsWith(\".dev\")")
        assertTrue(nonceArgIndex >= 0, "must read the NONCE_ARGUMENT_KEY instrumentation argument -- the 2026-08-27 content-check signal")
        assertTrue(markerIndex >= 0, "must check hasValidMarker(...) -- marker CONTENT, not merely existence")
        assertTrue(
            isEmulatorIndex < devSuffixIndex && devSuffixIndex < nonceArgIndex && nonceArgIndex < markerIndex,
            "the four checks must run in a stable, documented order: emulator, then .dev, then the " +
                "nonce argument's presence, then the marker's actual content",
        )
    }

    @Test
    fun `every assumeDisposableEnvironment check uses assumeTrue, not a mere comment or log`() {
        val body = functionBody("assumeDisposableEnvironment")
        val assumeTrueCount = Regex("""assumeTrue\(""").findAll(body).count()

        assertEquals(4, assumeTrueCount, "expected exactly 4 assumeTrue(...) calls -- one per required signal")
    }

    @Test
    fun `hasValidMarker compares actual file content against the expected nonce, and fails closed`() {
        val body = functionBody("hasValidMarker")

        assertTrue(body.contains("File(DISPOSABLE_MARKER_PATH)"), "must read the declared marker path constant, not a hardcoded duplicate")
        assertTrue(
            body.contains("readText()") && body.contains("contains(expectedNonce)"),
            "must compare the marker file's actual CONTENT against expectedNonce, not merely check existence/readability",
        )
        assertTrue(
            body.contains("catch") && body.contains("false"),
            "must catch a failure and return false (fail closed) rather than let an exception propagate out of a guard",
        )
    }

    @Test
    fun `the guard reads the nonce from InstrumentationRegistry arguments, not a hardcoded or bypassable source`() {
        assertTrue(
            guardText.contains("InstrumentationRegistry.getArguments()") && guardText.contains("NONCE_ARGUMENT_KEY"),
            "the expected nonce must come from the real instrumentation-argument bundle for this exact " +
                "test invocation (disposableMarkerNonce), not a constant or a value the guard invents itself",
        )
    }

    @Test
    fun `the marker path lives under data local tmp, the documented world-readable emulator convention`() {
        val guardPath = Regex("""DISPOSABLE_MARKER_PATH\s*=\s*"([^"]+)"""").find(guardText)?.groupValues?.get(1)

        assertTrue(guardPath != null, "could not find DISPOSABLE_MARKER_PATH in the guard source")
        assertTrue(
            guardPath!!.startsWith("/data/local/tmp/"),
            "the marker must live under /data/local/tmp -- the standard world-readable/writable emulator " +
                "location for a host-to-device test fixture marker, not an app-private or arbitrary path",
        )
    }

    private fun functionBody(functionName: String): String {
        val markers = listOf("fun $functionName(", "internal fun $functionName(")
        val start = markers.firstNotNullOfOrNull { marker -> guardText.indexOf(marker).takeIf { it >= 0 } }
        assertTrue(start != null, "$functionName not found")
        val nextFunOrBrace = listOf("\n    fun ", "\n    internal fun ", "\n}").mapNotNull { needle ->
            guardText.indexOf(needle, start!! + 1).takeIf { it >= 0 }
        }.minOrNull()
        assertTrue(nextFunOrBrace != null && nextFunOrBrace > start!!, "could not bound $functionName's body")
        return guardText.substring(start!!, nextFunOrBrace!!)
    }

    private fun stripComments(input: String): String {
        val noBlockComments = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL).replace(input, " ")
        return noBlockComments.lineSequence().joinToString(separator = "\n") { line ->
            val idx = line.indexOf("//")
            if (idx >= 0) line.substring(0, idx) else line
        }
    }

    private fun source(relativePath: String): String {
        val direct = Path.of(relativePath)
        val fromParent = Path.of("..").resolve(relativePath)
        val fromGrandparent = Path.of("../..").resolve(relativePath)
        val path = listOf(direct, fromParent, fromGrandparent)
            .map(Path::toAbsolutePath)
            .firstOrNull(Files::isRegularFile)
            ?: error("Unable to resolve source file: $relativePath")
        return String(Files.readAllBytes(path), Charsets.UTF_8)
    }
}
// KMK <--
