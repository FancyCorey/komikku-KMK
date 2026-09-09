package exh.visual

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceItem
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [30], qualifiers = "w360dp-h640dp-xxhdpi")
class BestVersionFixturePreferenceRoborazziTest {
    @Test
    fun runningFixtureActionRemainsMountedWithVisibleStatus() {
        captureRoboImage {
            MaterialTheme {
                Surface {
                    PreferenceItem(
                        item = Preference.PreferenceItem.TextPreference(
                            title = "Prepare and open Best Version fixture",
                            subtitle = "Preparing isolated records…",
                            onClick = {},
                        ),
                        highlightKey = null,
                    )
                }
            }
        }
    }
}
