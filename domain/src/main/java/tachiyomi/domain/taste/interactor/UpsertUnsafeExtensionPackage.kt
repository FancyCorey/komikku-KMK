package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.UnsafeExtensionPackage
import tachiyomi.domain.taste.repository.UnsafeExtensionPackageRepository

// KMK -->
class UpsertUnsafeExtensionPackage(
    private val repository: UnsafeExtensionPackageRepository,
) {
    suspend fun await(pkg: UnsafeExtensionPackage) = repository.upsert(pkg)
}
// KMK <--
