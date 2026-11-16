package metrovoice.app

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import metrovoice.app.ui.MetroTheme
import metrovoice.app.ui.ProfileScreen
import metrovoice.app.ui.StationPickerDialog
import metrovoice.app.ui.TripScreen
import metrovoice.app.ui.WelcomeScreen
import metrovoice.core.graph.MetroGraph
import metrovoice.core.graph.MetroNetwork
import metrovoice.core.tracking.Estimate
import metrovoice.core.tracking.StationBelief
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real screens with sample state into PNGs (screenshots/…).
 * Run: ./gradlew :app:recordRoborazziDebug
 */
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val network = MetroNetwork(MetroGraph.parse(File("src/main/assets/cities/tbilisi.json").readText()))
    private val profile = Profile(homeStationId = "didube", workStationId = "rustaveli")

    private fun shot(name: String, dark: Boolean = false, wholeScreen: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { MetroTheme(dark = dark) { Surface { content() } } }
        val path = "../docs/screenshots/$name.png"
        if (wholeScreen) captureScreenRoboImage(path) else compose.onRoot().captureRoboImage(path)
    }

    private val riding = UiState(
        running = true,
        phase = "STOPPED",
        estimate = Estimate(
            atStationProbability = 0.96,
            current = StationBelief("rustaveli", "1", 0.94),
            next = StationBelief("marjanishvili", "1", 0.91),
            hypothesisCount = 4,
        ),
        lastSaid = "Станция Руставели. Rustaveli.",
        destination = "didube",
    )

    @Test fun welcome() = shot("1-welcome") {
        WelcomeScreen(busy = false, message = null, onGoogle = {}, onApple = {}, onGuest = {})
    }

    @Test fun tripIdle() = shot("2-trip-idle") {
        TripScreen(network, UiState(), profile, {}, {}, {}, {}, {}, {})
    }

    @Test fun tripRiding() = shot("3-trip-riding") {
        TripScreen(network, riding, profile, {}, {}, {}, {}, {}, {})
    }

    @Test fun tripRidingDark() = shot("4-trip-riding-dark", dark = true) {
        TripScreen(network, riding, profile, {}, {}, {}, {}, {}, {})
    }

    @Test fun profileScreen() = shot("5-profile") {
        ProfileScreen(network, Account("u1", "Ника Б.", "nika@example.com", "google.com"), profile, {}, {}, {}, {})
    }

    @Test fun stationPicker() = shot("6-station-picker", wholeScreen = true) {
        StationPickerDialog(network, "Станция у дома", onPick = {}, onDismiss = {})
    }
}
