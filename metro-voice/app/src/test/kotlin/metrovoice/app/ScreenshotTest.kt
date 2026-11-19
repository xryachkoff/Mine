package metrovoice.app

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import metrovoice.app.ui.HistoryScreen
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
import org.robolectric.RuntimeEnvironment
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

    private val now = 1_000_000L
    private val riding = UiState(
        running = true,
        phase = "STOPPED",
        estimate = Estimate(
            atStationProbability = 0.96,
            current = StationBelief("rustaveli", "1", 0.94),
            next = StationBelief("marjanishvili", "1", 0.91),
            hypothesisCount = 4,
        ),
        destination = "didube",
        startedElapsedMs = now - 400_000,
        lastFixElapsedMs = now - 12_000,
        lastFixAccuracyMeters = 140,
    )
    private val inTunnel = riding.copy(
        estimate = Estimate(0.03, StationBelief("rustaveli", "1", 0.02), StationBelief("marjanishvili", "1", 0.95), 3),
        lastFixElapsedMs = now - 95_000,
    )

    @Composable
    private fun Trip(state: UiState, profile: Profile = this.profile, confirm: String? = null) =
        TripScreen(network, state, profile, now, {}, {}, {}, {}, {}, {}, {}, initialConfirmStation = confirm)

    @Test fun welcome() = shot("1-welcome") {
        WelcomeScreen(busy = false, message = null, onGoogle = {}, onApple = {}, onGuest = {})
    }

    @Test fun tripIdle() = shot("2-trip-idle") { Trip(UiState()) }

    @Test fun tripIdleFirstRun() = shot("2b-trip-first-run") { Trip(UiState(), Profile()) }

    @Test fun tripRiding() = shot("3-trip-riding") { Trip(riding) }

    @Test fun tripRidingDark() = shot("4-trip-riding-dark", dark = true) { Trip(riding) }

    @Test fun profileScreen() = shot("5-profile") {
        ProfileScreen(
            network, Account("u1", "Ника Б.", "nika@example.com", "google.com"), profile, signInAvailable = true,
            onBack = {}, onSetHome = {}, onSetWork = {}, onToggleMute = {}, onToggleLanguage = {}, onTestVoice = {}, onDemo = {}, onSignOut = {},
        )
    }

    @Test fun stationPicker() = shot("6-station-picker", wholeScreen = true) {
        StationPickerDialog(network, "Станция у дома", onPick = {}, onDismiss = {})
    }

    @Test fun tripInTunnel() = shot("7-trip-in-tunnel", dark = true) { Trip(inTunnel) }

    @Test fun history() = shot("8-history") {
        HistoryScreen(
            trips = listOf(
                TripInfo("a", "Руставели → Дидубе", "17 ноября, 18:42 · 31 мин · 6 станций · 4,2 МБ", recording = false),
                TripInfo("b", "Дидубе → Технический университет", "17 ноября, 09:05 · 24 мин · 8 станций · 3,1 МБ", recording = false),
                TripInfo("c", "16 ноября, 19:20", "12 мин · 1,6 МБ", recording = false),
            ),
            onBack = {}, onShare = {}, onDelete = {},
        )
    }

    @Test fun confirmStation() = shot("9-confirm-station", wholeScreen = true) { Trip(riding, confirm = "marjanishvili") }

    @Test fun staleFix() = shot("10-no-fix-warning") {
        Trip(inTunnel.copy(lastFixElapsedMs = now - 320_000, destination = "technical-university"), profile.copy(muted = true))
    }

    /** Accessibility check: system font at 150%. */
    @Test fun largeFont() {
        RuntimeEnvironment.setFontScale(1.5f)
        shot("11-large-font") { Trip(riding) }
    }

    /** Unfolded foldable / small tablet. */
    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h900dp-xhdpi")
    fun unfolded() = shot("12-unfolded") { Trip(riding) }
}
