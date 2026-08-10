package mihon.domain.extension.interactor

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.extension.repository.ExtensionStoreRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AddExtensionStoreUrlBoundaryTest {

    @Test
    fun `rejects unsafe URL before repository or network work`() = runTest {
        val repository = mockk<ExtensionStoreRepository>()
        val addExtensionStore = AddExtensionStore(repository)

        val result = addExtensionStore("file:///sdcard/private.txt")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.insert(any()) }
    }

    @Test
    fun `delegates valid HTTP(S) URL to repository`() = runTest {
        val repository = mockk<ExtensionStoreRepository>()
        coEvery { repository.insert("https://example.invalid/repo.json") } returns Result.success(Unit)
        val addExtensionStore = AddExtensionStore(repository)

        val result = addExtensionStore("https://example.invalid/repo.json")

        assertFalse(result.isFailure)
        coVerify(exactly = 1) { repository.insert("https://example.invalid/repo.json") }
    }
}
