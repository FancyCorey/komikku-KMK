package exh.recs.evaluation

import eu.kanade.tachiyomi.extension.util.ExtensionLoadSafetyPolicy
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class ExtensionLoadSafetyPolicyTest {

    private val dcmPkg = "eu.kanade.tachiyomi.extension.en.digitalcomicmuseum"
    private val safePkg = "eu.kanade.tachiyomi.extension.en.mangadex"

    @Test
    fun `DCM package is blocked by static guard`() {
        assertTrue(ExtensionLoadSafetyPolicy.shouldBlock(dcmPkg))
    }

    @Test
    fun `safe package is not blocked by default`() {
        assertFalse(ExtensionLoadSafetyPolicy.shouldBlock(safePkg))
    }

    @Test
    fun `empty string is not blocked`() {
        assertFalse(ExtensionLoadSafetyPolicy.shouldBlock(""))
    }

    @Test
    fun `user-blocked package is blocked when passed in`() {
        val userBlocked = setOf("eu.kanade.tachiyomi.extension.en.some.badext")
        assertTrue(ExtensionLoadSafetyPolicy.shouldBlock("eu.kanade.tachiyomi.extension.en.some.badext", userBlocked))
    }

    @Test
    fun `user-blocked set does not affect unrelated package`() {
        val userBlocked = setOf("eu.kanade.tachiyomi.extension.en.some.badext")
        assertFalse(ExtensionLoadSafetyPolicy.shouldBlock(safePkg, userBlocked))
    }

    @Test
    fun `DCM is still blocked even when user-blocked set is empty`() {
        assertTrue(ExtensionLoadSafetyPolicy.shouldBlock(dcmPkg, emptySet()))
    }

    @Test
    fun `safe package with empty user-blocked set is not blocked`() {
        assertFalse(ExtensionLoadSafetyPolicy.shouldBlock(safePkg, emptySet()))
    }

    @Test
    fun `package blocked only in user set is blocked`() {
        val userBlocked = setOf(safePkg)
        assertTrue(ExtensionLoadSafetyPolicy.shouldBlock(safePkg, userBlocked))
    }
}
// KMK <--
