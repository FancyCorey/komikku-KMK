package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.UnsafeExtensionPackageRepository

// KMK -->
class ClearUnsafeExtensionPackages(
    private val repository: UnsafeExtensionPackageRepository,
) {
    suspend fun await() = repository.deleteAll()
}
// KMK <--
