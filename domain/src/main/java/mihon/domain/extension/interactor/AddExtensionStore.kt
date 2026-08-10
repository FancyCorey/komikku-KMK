package mihon.domain.extension.interactor

import mihon.domain.extension.ExtensionStoreUrlPolicy
import mihon.domain.extension.repository.ExtensionStoreRepository

class AddExtensionStore(
    private val repository: ExtensionStoreRepository,
) {
    suspend operator fun invoke(indexUrl: String): Result<Unit> {
        if (!ExtensionStoreUrlPolicy.isAllowed(indexUrl)) {
            return Result.failure(IllegalArgumentException("Invalid extension store URL"))
        }
        return repository.insert(indexUrl)
    }
}
