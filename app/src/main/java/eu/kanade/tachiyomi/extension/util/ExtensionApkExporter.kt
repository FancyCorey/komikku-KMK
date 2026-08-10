package eu.kanade.tachiyomi.extension.util

// KMK v0.8.18 -->
import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports the raw, already-installed extension APK/archive bytes a user chooses -- for sharing
 * outside the app's own repo/store mechanism (e.g. sideloading onto another device). Never
 * repackages, modifies, or re-signs anything; copies bytes unchanged. Never touches
 * `Extension.Available` (remote catalogue) entries -- only already-installed extensions have a
 * real local file to export. Does not add import/install behavior; export only.
 */
object ExtensionApkExporter {

    // KMK: mirrors ExtensionLoader's private constant/dir exactly -- ExtensionLoader.getPrivateExtensionDir
    // and PRIVATE_EXTENSION_EXTENSION are both `private`, so the same path is reconstructed here
    // rather than widening their visibility just for this export path.
    private const val PRIVATE_EXTENSION_EXTENSION = "ext"
    private fun privateExtensionDir(context: Context) = File(context.filesDir, "exts")

    /**
     * Resolves the on-disk APK/archive file for an installed extension. Returns null (a
     * non-fatal, reportable failure -- never a crash) when the file cannot be found or read,
     * e.g. a shared/system extension whose package was uninstalled between listing and export.
     */
    fun resolveSourceFile(context: Context, extension: Extension.Installed): File? {
        return if (extension.isShared) {
            // Shared/system-installed extension APK -- resolved via PackageManager, never assumed
            // to live in the app's own private extension directory.
            runCatching {
                val info = context.packageManager.getApplicationInfo(extension.pkgName, 0)
                File(info.publicSourceDir ?: info.sourceDir ?: return null)
            }.getOrNull()?.takeIf { it.exists() && it.canRead() }
        } else {
            File(privateExtensionDir(context), "${extension.pkgName}.$PRIVATE_EXTENSION_EXTENSION")
                .takeIf { it.exists() && it.canRead() }
        }
    }

    fun suggestedApkFileName(extension: Extension.Installed): String {
        val safeName = extension.name.replace(Regex("[^a-zA-Z0-9]+"), "_").trim('_')
        return "komikku-extension-$safeName-${extension.pkgName}-${extension.versionName}.apk"
    }

    fun suggestedZipFileName(): String = "komikku-extensions-export.zip"

    @Serializable
    data class ManifestEntry(
        val pkgName: String,
        val name: String,
        val lang: String?,
        val versionName: String,
        val versionCode: Long,
        val signatureHash: String,
        val isNsfw: Boolean,
        val isShared: Boolean,
        val sourceCount: Int,
        val storeName: String?,
    )

    @Serializable
    data class ExportManifest(
        val exportedAt: Long,
        val appVersion: String,
        val kmkVersion: String,
        val extensions: List<ManifestEntry>,
    )

    private fun manifestEntry(extension: Extension.Installed) = ManifestEntry(
        pkgName = extension.pkgName,
        name = extension.name,
        lang = extension.lang,
        versionName = extension.versionName,
        versionCode = extension.versionCode,
        signatureHash = extension.signatureHash,
        isNsfw = extension.isNsfw,
        isShared = extension.isShared,
        sourceCount = extension.sources.size,
        storeName = extension.storeName,
    )

    sealed interface ExportResult {
        data object Success : ExportResult
        data object SourceFileMissing : ExportResult
        data object WriteFailed : ExportResult
    }

    // KMK_CLAUDE_REMAINING_FIXTURE_BLOCKER_IMPLEMENTATION_PLAN_2026-08-03 Phase 5 -->
    /**
     * Removes exactly the document a caller just exported, through the same SAF `Uri` the user
     * granted write access to via the system document picker (`ACTION_CREATE_DOCUMENT` /
     * `exportLauncher` in `ExtensionDetailsScreen`/`ExtensionsScreen`). This cannot delete outside
     * that single already-granted document -- `ContentResolver.delete` on a SAF document Uri only
     * ever affects the exact document the Uri identifies, the same file-provider contract
     * [exportSingle]/[exportMultiple] already write through. Never called automatically; only ever
     * offered as an explicit, separate user action after a confirmed successful export, so an
     * export the user actually wanted to keep is never silently removed.
     */
    fun deleteExported(context: Context, destUri: Uri): Boolean {
        return try {
            android.provider.DocumentsContract.deleteDocument(context.contentResolver, destUri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
    // KMK <--

    /** Copies one installed extension's raw APK/archive bytes, unchanged, to [destUri]. */
    suspend fun exportSingle(context: Context, extension: Extension.Installed, destUri: Uri): ExportResult {
        val sourceFile = resolveSourceFile(context, extension) ?: return ExportResult.SourceFileMissing
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    sourceFile.inputStream().use { it.copyTo(out) }
                } ?: return@withContext ExportResult.WriteFailed
                ExportResult.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExportResult.WriteFailed
            }
        }
    }

    data class MultiExportSummary(val exportedCount: Int, val skippedPkgNames: List<String>)

    /** Thrown (as a [Result.failure]) by [exportMultiple] when no selected extension is exportable. */
    class NoExtensionsExportableException : Exception("None of the selected extensions could be exported")

    /**
     * Writes every resolvable extension's raw APK/archive bytes plus one non-sensitive
     * [ExportManifest] into a single zip at [destUri]. Extensions whose source file cannot be
     * resolved are skipped (reported in [MultiExportSummary.skippedPkgNames]), never failing the
     * whole export. The manifest never includes cookies, credentials, source preferences,
     * ratings, recommendation data, reading history, or backups -- only the non-sensitive
     * identity/version metadata listed in [ManifestEntry].
     */
    suspend fun exportMultiple(
        context: Context,
        extensions: List<Extension.Installed>,
        destUri: Uri,
        appVersion: String,
        kmkVersion: String,
    ): Result<MultiExportSummary> = withContext(Dispatchers.IO) {
        try {
            val skipped = mutableListOf<String>()
            val exportable = extensions.mapNotNull { ext ->
                val file = resolveSourceFile(context, ext)
                if (file == null) {
                    skipped += ext.pkgName
                    null
                } else {
                    ext to file
                }
            }
            // Corrective pass 2026-08-03 (C1): reject before any destination write when nothing is
            // exportable -- `exportable` is computed above without touching `destUri`, so this
            // return happens strictly before `context.contentResolver.openOutputStream(destUri)`.
            // No manifest-only zip is ever written to the user-chosen SAF document, and no
            // `MultiExportSummary` with `exportedCount == 0` can ever be returned as a success. This
            // is the chosen resolution for the "manifest-only success" edge case: a bulk export the
            // user cannot meaningfully use (no extension content) is reported as a failure, not a
            // hollow success that would need its own cleanup-eligibility carve-out.
            if (exportable.isEmpty()) {
                return@withContext Result.failure(NoExtensionsExportableException())
            }
            val json = Json { prettyPrint = false }
            val manifest = ExportManifest(
                exportedAt = System.currentTimeMillis(),
                appVersion = appVersion,
                kmkVersion = kmkVersion,
                extensions = exportable.map { (ext, _) -> manifestEntry(ext) },
            )
            val out = context.contentResolver.openOutputStream(destUri)
                ?: return@withContext Result.failure(IllegalStateException("Could not open destination"))
            out.use { stream ->
                ZipOutputStream(stream).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write(json.encodeToString(manifest).toByteArray())
                    zip.closeEntry()
                    exportable.forEach { (ext, file) ->
                        zip.putNextEntry(ZipEntry("${ext.pkgName}.apk"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            Result.success(MultiExportSummary(exportedCount = exportable.size, skippedPkgNames = skipped))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
// KMK <--
