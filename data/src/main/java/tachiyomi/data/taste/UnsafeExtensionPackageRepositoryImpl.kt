package tachiyomi.data.taste

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.taste.model.UnsafeExtensionPackage
import tachiyomi.domain.taste.repository.UnsafeExtensionPackageRepository

// KMK -->
class UnsafeExtensionPackageRepositoryImpl(
    private val handler: DatabaseHandler,
) : UnsafeExtensionPackageRepository {

    override fun getAllAsFlow(): Flow<List<UnsafeExtensionPackage>> {
        return handler.subscribeToList {
            unsafe_extension_packageQueries.getAllAsFlow(unsafePackageMapper)
        }
    }

    override suspend fun getAll(): List<UnsafeExtensionPackage> {
        return handler.awaitList {
            unsafe_extension_packageQueries.getAll(unsafePackageMapper)
        }
    }

    override suspend fun upsert(pkg: UnsafeExtensionPackage) {
        handler.await(inTransaction = false) {
            unsafe_extension_packageQueries.upsert(
                pkgName = pkg.pkgName,
                extensionName = pkg.extensionName,
                reason = pkg.reason,
                source = pkg.source,
                removable = if (pkg.removable) 1L else 0L,
                now = pkg.updatedAt,
            )
        }
    }

    override suspend fun deleteByPkg(pkgName: String) {
        handler.await { unsafe_extension_packageQueries.deleteByPkg(pkgName) }
    }

    override suspend fun deleteAll() {
        handler.await { unsafe_extension_packageQueries.deleteAll() }
    }
}

private val unsafePackageMapper = {
        pkgName: String,
        extensionName: String?,
        reason: String,
        source: String,
        removable: Long,
        createdAt: Long,
        updatedAt: Long,
    ->
    UnsafeExtensionPackage(
        pkgName = pkgName,
        extensionName = extensionName,
        reason = reason,
        source = source,
        removable = removable != 0L,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
// KMK <--
