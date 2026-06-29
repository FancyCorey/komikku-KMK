package tachiyomi.domain.taste.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.taste.model.UnsafeExtensionPackage
import tachiyomi.domain.taste.repository.UnsafeExtensionPackageRepository

// KMK -->
class GetUnsafeExtensionPackages(
    private val repository: UnsafeExtensionPackageRepository,
) {
    fun subscribeAll(): Flow<List<UnsafeExtensionPackage>> = repository.getAllAsFlow()
    suspend fun awaitAll(): List<UnsafeExtensionPackage> = repository.getAll()
}
// KMK <--
