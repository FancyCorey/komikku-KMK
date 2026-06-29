package tachiyomi.domain.taste.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.taste.model.UnsafeExtensionPackage

// KMK -->
interface UnsafeExtensionPackageRepository {
    fun getAllAsFlow(): Flow<List<UnsafeExtensionPackage>>
    suspend fun getAll(): List<UnsafeExtensionPackage>
    suspend fun upsert(pkg: UnsafeExtensionPackage)
    suspend fun deleteByPkg(pkgName: String)
    suspend fun deleteAll()
}
// KMK <--
