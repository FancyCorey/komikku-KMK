package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.UnsafeExtensionPackageRepository

// KMK -->
class DeleteUnsafeExtensionPackage(
    private val repository: UnsafeExtensionPackageRepository,
) {
    suspend fun await(pkgName: String) = repository.deleteByPkg(pkgName)
}
// KMK <--
