package eu.kanade.tachiyomi.ui.main

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import eu.kanade.tachiyomi.ui.library.LibraryTab
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

// KMK F2-05.0.2 (KFC-V0.8.21-FIX2-CORRECTIVE-RECHECK-AND-RELEASE-PROGRAM) -->
/**
 * Real, on-device proof that [LibraryTab.LIBRARY_READY_CONTENT_TEST_TAG] is genuinely usable by
 * the actual device-automation path (UI Automator), not merely present in Compose source.
 *
 * An independent review found the prior closure's claim insufficient: the test tag existed, but
 * nothing had wired `androidx.compose.ui.semantics.testTagsAsResourceId` -- without it, a Compose
 * `Modifier.testTag` is invisible to `UiDevice`/`By.res(...)`, which only sees Android View
 * `resource-id`. [MainActivity] now applies `Modifier.semantics { testTagsAsResourceId = true }`
 * once, high in its Compose hierarchy (its root `Scaffold`), per the official guidance at
 * developer.android.com/develop/ui/compose/testing/interoperability. This test proves that wiring
 * actually works end-to-end on a real device, using the exact same `UiDevice`/`By` mechanism
 * [eu.kanade.tachiyomi.automation.KmkUiAutomationCaptureTest] already establishes as this
 * project's own device-automation path -- not a different, ad hoc mechanism.
 */
@RunWith(AndroidJUnit4::class)
class LibraryReadyContentSelectorDeviceTest {

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun libraryReadyContentTestTagIsVisibleToUiAutomatorAsAResourceId() {
        LibraryTab.resetForNewActivityInstance()

        ActivityScenario.launch(MainActivity::class.java).use {
            val found = device.wait(
                Until.hasObject(By.res(LibraryTab.LIBRARY_READY_CONTENT_TEST_TAG)),
                30_000,
            )
            if (found == null) {
                val hierarchyBytes = java.io.ByteArrayOutputStream()
                device.dumpWindowHierarchy(hierarchyBytes)
                error(
                    "UI Automator never found a resource-id matching " +
                        "'${LibraryTab.LIBRARY_READY_CONTENT_TEST_TAG}' -- testTagsAsResourceId is " +
                        "not correctly wired, or the Library-ready content never rendered within " +
                        "30s. Current window hierarchy dump follows for diagnosis:\n" +
                        hierarchyBytes.toString(Charsets.UTF_8.name()),
                )
            }

            val node = device.findObject(By.res(LibraryTab.LIBRARY_READY_CONTENT_TEST_TAG))
            assertNotNull(
                "resource-id was detected by wait() but findObject() could not retrieve the node",
                node,
            )
        }
    }
}
// KMK <--
