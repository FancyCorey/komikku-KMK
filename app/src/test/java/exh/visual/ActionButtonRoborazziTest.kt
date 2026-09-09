package exh.visual

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tachiyomi.presentation.core.components.ActionButton

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [30], qualifiers = "w360dp-h640dp-xxhdpi")
class ActionButtonRoborazziTest {

    @Test
    fun actionButtonUsesTheSharedMaterialPresentation() {
        captureRoboImage {
            MaterialTheme {
                Surface {
                    Box(
                        modifier = Modifier
                            .size(width = 180.dp, height = 120.dp)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ActionButton(
                            title = "Track locally",
                            icon = Icons.Outlined.Favorite,
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}
