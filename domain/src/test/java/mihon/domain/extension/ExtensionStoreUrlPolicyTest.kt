package mihon.domain.extension

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtensionStoreUrlPolicyTest {

    @Test
    fun `accepts valid HTTP and HTTPS repository URLs`() {
        listOf(
            "http://example.invalid/repo.json",
            "https://example.invalid/index.json?channel=stable",
            "HTTPS://[2001:db8::1]/repo.json",
        ).forEach { assertTrue(ExtensionStoreUrlPolicy.isAllowed(it), it) }
    }

    @Test
    fun `rejects local, script, malformed, and scheme lookalike URLs`() {
        listOf(
            "file:///sdcard/private.txt",
            "content://com.example.provider/repo.json",
            "javascript:alert(1)",
            "intent://example.invalid/repo.json",
            "httpx://example.invalid/repo.json",
            "http:example.invalid/repo.json",
            "http:///repo.json",
            "not a uri",
        ).forEach { assertFalse(ExtensionStoreUrlPolicy.isAllowed(it), it) }
    }

    @Test
    fun `rejects credentials and fragments`() {
        listOf(
            "https://user:password@example.invalid/repo.json",
            "https://example.invalid/repo.json#fragment",
        ).forEach { assertFalse(ExtensionStoreUrlPolicy.isAllowed(it), it) }
    }
}
