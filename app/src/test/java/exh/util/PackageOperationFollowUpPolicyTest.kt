package exh.util

import eu.kanade.tachiyomi.extension.model.Extension
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

// KMK -->
class PackageOperationFollowUpPolicyTest {

    private fun receipt(
        kind: PackageOperationKind,
        packageName: String = "eu.kanade.tachiyomi.extension.en.a",
        signatureHash: String? = "sig1",
        versionCode: Long? = 1L,
        artifactUri: String? = "https://example.invalid/a.apk",
    ) = PackageOperationReceipt(
        id = PackageOperationReceipt.newId(),
        timestamp = System.currentTimeMillis(),
        kind = kind,
        packageName = packageName,
        signatureHash = signatureHash,
        versionCode = versionCode,
        artifactUri = artifactUri,
    )

    private fun installed(
        pkgName: String = "eu.kanade.tachiyomi.extension.en.a",
        signatureHash: String = "sig1",
        versionCode: Long = 1L,
    ) = Extension.Installed(
        name = "A",
        pkgName = pkgName,
        versionName = "1.0.0",
        versionCode = versionCode,
        libVersion = 1.5,
        lang = "en",
        isNsfw = false,
        signatureHash = signatureHash,
        storeName = null,
        pkgFactory = null,
        sources = emptyList(),
        icon = null,
        isShared = false,
    )

    private fun available(
        pkgName: String = "eu.kanade.tachiyomi.extension.en.a",
        signatureHash: String = "sig1",
        apkUrl: String = "https://example.invalid/a.apk",
    ) = Extension.Available(
        name = "A",
        pkgName = pkgName,
        versionName = "1.0.0",
        versionCode = 1L,
        libVersion = 1.5,
        lang = "en",
        isNsfw = false,
        signatureHash = signatureHash,
        storeName = "Store",
        sources = emptyList(),
        apkUrl = apkUrl,
        iconUrl = "",
        store = ExtensionStore(
            indexUrl = "https://example.invalid/index.json",
            name = "Store",
            badgeLabel = "Store",
            signingKey = signatureHash,
            contact = ExtensionStore.Contact(website = "", discord = null),
            isLegacy = false,
            extensionListUrl = null,
        ),
    )

    // --- uninstall follow-up (INSTALL/UPDATE receipts) ---

    @Test
    fun `uninstall follow-up is offered when the currently installed package matches the receipt exactly`() {
        val result = PackageOperationFollowUpPolicy.evaluateUninstallFollowUp(
            receipt = receipt(PackageOperationKind.INSTALL),
            currentlyInstalled = installed(),
        )
        assertEquals(PackageOperationFollowUpPolicy.UninstallFollowUp.Offered, result)
    }

    @Test
    fun `uninstall follow-up is refused when the package is not currently installed`() {
        val result = PackageOperationFollowUpPolicy.evaluateUninstallFollowUp(
            receipt = receipt(PackageOperationKind.INSTALL),
            currentlyInstalled = null,
        )
        assertEquals(
            PackageOperationFollowUpPolicy.UninstallFollowUp.Unavailable(PackageOperationFollowUpPolicy.UninstallFollowUp.Reason.PACKAGE_NOT_INSTALLED),
            result,
        )
    }

    @Test
    fun `uninstall follow-up is refused when the installed package name differs from the receipt`() {
        val result = PackageOperationFollowUpPolicy.evaluateUninstallFollowUp(
            receipt = receipt(PackageOperationKind.INSTALL, packageName = "eu.kanade.tachiyomi.extension.en.a"),
            currentlyInstalled = installed(pkgName = "eu.kanade.tachiyomi.extension.en.b"),
        )
        assertEquals(
            PackageOperationFollowUpPolicy.UninstallFollowUp.Unavailable(PackageOperationFollowUpPolicy.UninstallFollowUp.Reason.PACKAGE_NOT_INSTALLED),
            result,
        )
    }

    @Test
    fun `uninstall follow-up is refused on a signature mismatch`() {
        val result = PackageOperationFollowUpPolicy.evaluateUninstallFollowUp(
            receipt = receipt(PackageOperationKind.UPDATE, signatureHash = "sig1"),
            currentlyInstalled = installed(signatureHash = "sig2"),
        )
        assertEquals(
            PackageOperationFollowUpPolicy.UninstallFollowUp.Unavailable(PackageOperationFollowUpPolicy.UninstallFollowUp.Reason.SIGNATURE_MISMATCH),
            result,
        )
    }

    @Test
    fun `uninstall follow-up is refused on a version mismatch -- something updated it again since the receipt`() {
        val result = PackageOperationFollowUpPolicy.evaluateUninstallFollowUp(
            receipt = receipt(PackageOperationKind.INSTALL, versionCode = 1L),
            currentlyInstalled = installed(versionCode = 2L),
        )
        assertEquals(
            PackageOperationFollowUpPolicy.UninstallFollowUp.Unavailable(PackageOperationFollowUpPolicy.UninstallFollowUp.Reason.VERSION_MISMATCH),
            result,
        )
    }

    @Test
    fun `uninstall follow-up throws for a receipt kind it does not apply to`() {
        assertThrows(IllegalArgumentException::class.java) {
            PackageOperationFollowUpPolicy.evaluateUninstallFollowUp(
                receipt = receipt(PackageOperationKind.UNINSTALL),
                currentlyInstalled = installed(),
            )
        }
    }

    // --- reinstall follow-up (UNINSTALL receipts) ---

    @Test
    fun `reinstall follow-up is offered when a matching available artifact still exists and nothing reinstalled it`() {
        val result = PackageOperationFollowUpPolicy.evaluateReinstallFollowUp(
            receipt = receipt(PackageOperationKind.UNINSTALL),
            currentlyInstalled = null,
            availableMatch = available(),
        )
        assertEquals(PackageOperationFollowUpPolicy.ReinstallFollowUp.Offered, result)
    }

    @Test
    fun `reinstall follow-up is refused when the package is already installed again`() {
        val result = PackageOperationFollowUpPolicy.evaluateReinstallFollowUp(
            receipt = receipt(PackageOperationKind.UNINSTALL),
            currentlyInstalled = installed(),
            availableMatch = available(),
        )
        assertEquals(
            PackageOperationFollowUpPolicy.ReinstallFollowUp.Unavailable(PackageOperationFollowUpPolicy.ReinstallFollowUp.Reason.PACKAGE_ALREADY_INSTALLED),
            result,
        )
    }

    @Test
    fun `reinstall follow-up is refused when no matching available artifact exists`() {
        val result = PackageOperationFollowUpPolicy.evaluateReinstallFollowUp(
            receipt = receipt(PackageOperationKind.UNINSTALL),
            currentlyInstalled = null,
            availableMatch = null,
        )
        assertEquals(
            PackageOperationFollowUpPolicy.ReinstallFollowUp.Unavailable(PackageOperationFollowUpPolicy.ReinstallFollowUp.Reason.ARTIFACT_NOT_AVAILABLE),
            result,
        )
    }

    @Test
    fun `reinstall follow-up is refused when the available artifact's signature no longer matches the receipt`() {
        val result = PackageOperationFollowUpPolicy.evaluateReinstallFollowUp(
            receipt = receipt(PackageOperationKind.UNINSTALL, signatureHash = "sig1"),
            currentlyInstalled = null,
            availableMatch = available(signatureHash = "sig2"),
        )
        assertEquals(
            PackageOperationFollowUpPolicy.ReinstallFollowUp.Unavailable(PackageOperationFollowUpPolicy.ReinstallFollowUp.Reason.ARTIFACT_NOT_AVAILABLE),
            result,
        )
    }

    @Test
    fun `reinstall follow-up is refused when the available artifact has a blank apk url`() {
        val result = PackageOperationFollowUpPolicy.evaluateReinstallFollowUp(
            receipt = receipt(PackageOperationKind.UNINSTALL),
            currentlyInstalled = null,
            availableMatch = available(apkUrl = ""),
        )
        assertEquals(
            PackageOperationFollowUpPolicy.ReinstallFollowUp.Unavailable(PackageOperationFollowUpPolicy.ReinstallFollowUp.Reason.ARTIFACT_NOT_AVAILABLE),
            result,
        )
    }

    @Test
    fun `reinstall follow-up throws for a receipt kind it does not apply to`() {
        assertThrows(IllegalArgumentException::class.java) {
            PackageOperationFollowUpPolicy.evaluateReinstallFollowUp(
                receipt = receipt(PackageOperationKind.INSTALL),
                currentlyInstalled = null,
                availableMatch = available(),
            )
        }
    }
}
// KMK <--
