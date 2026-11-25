package metrovoice.app

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import metrovoice.app.ui.HistoryScreen
import metrovoice.app.ui.HomeScreen
import metrovoice.app.ui.LineScreen
import metrovoice.app.ui.LocalPalette
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
 * Renders the real screens with sample state into PNGs (docs/screenshots/…).
 * Run: ./gradlew :app:recordRoborazziDebug
 */
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val network = MetroNetwork(MetroGraph.parse(File("src/main/assets/cities/tbilisi.json").readText()))
    private val profile = Profile(homeStationId = "didube", workStationId = "rustaveli", nearStation = NearStation.REMIND)

    private fun shot(name: String, dark: Boolean = false, wholeScreen: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { MetroTheme(dark = dark) { Surface(color = LocalPalette.current.canvas) { content() } } }
        val path = "../docs/screenshots/$name.png"
        if (wholeScreen) captureScreenRoboImage(path) else compose.onRoot().captureRoboImage(path)
    }

    private val now = 1_000_000L
    private val riding = UiState(
        running = true,
        phase = "STOPPED",
        estimate = Estimate(
            0.96, StationBelief("rustaveli", "1", 0.94), StationBelief("marjanishvili", "1", 0.91), 4,
            towards = StationBelief("akhmeteli-theatre", "1", 0.95),
        ),
        destination = "didube",
        startedElapsedMs = now - 400_000,
        lastFixElapsedMs = now - 12_000,
        lastFixAccuracyMeters = 40,
    )
    private val inTunnel = riding.copy(
        estimate = Estimate(
            0.03, StationBelief("rustaveli", "1", 0.02), StationBelief("marjanishvili", "1", 0.95), 3,
            towards = StationBelief("akhmeteli-theatre", "1", 0.97),
        ),
        lastFixElapsedMs = now - 55_000,
    )

    private val stats = TripStats(
        arrivals = mapOf("didube" to 14, "rustaveli" to 11, "technical-university" to 5, "avlabari" to 2, "isani" to 1),
        departures = mapOf("didube" to 13, "rustaveli" to 12, "technical-university" to 4),
    )

    @Composable
    private fun Home(profile: Profile = this.profile, destination: String? = "didube", stats: TripStats = this.stats) =
        HomeScreen(network, profile, destination, {}, {}, {}, {}, {}, {}, {}, {}, stats)

    @Composable
    private fun Trip(state: UiState, profile: Profile = this.profile) =
        TripScreen(network, state, profile, now, {}, {}, {}, {}, {})

    @Test fun home() = shot("01-home") { Home() }

    @Test fun homeFirstRun() = shot("02-home-first-run") { Home(Profile(), null, TripStats()) }

    @Test fun homeDark() = shot("03-home-dark", dark = true) { Home() }

    @Test fun tripAtStation() = shot("04-trip-station") { Trip(riding) }

    @Test fun tripAtStationDark() = shot("05-trip-station-dark", dark = true) { Trip(riding) }

    @Test fun tripInTunnel() = shot("06-trip-tunnel") { Trip(inTunnel) }

    @Test fun tripSearching() = shot("07-trip-searching") {
        Trip(UiState(running = true, startedElapsedMs = now - 20_000), profile.copy(muted = true))
    }

    @Test fun tripLongName() = shot("08-trip-long-name") {
        Trip(
            riding.copy(
                estimate = Estimate(0.97, StationBelief("technical-university", "2", 0.96), StationBelief("medical-university", "2", 0.5), 4),
                destination = "delisi", lastFixElapsedMs = now - 320_000,
            ),
        )
    }

    @Test fun arrived() = shot("09b-arrived") {
        val e = Estimate(
            0.97, StationBelief("didube", "1", 0.96), StationBelief("ghrmaghele", "1", 0.95), 3,
            towards = StationBelief("akhmeteli-theatre", "1", 0.95),
        )
        Trip(riding.copy(estimate = e))
    }

    @Test fun leftTheMetro() = shot("09-left-the-metro") {
        Trip(riding.copy(estimate = Estimate(0.0, null, null, 0, outside = true)))
    }

    @Test fun line() = shot("10-line") { LineScreen(network, UiState(), "1", {}, {}) }

    @Test fun lineCorrection() = shot("11-line-correct-position", dark = true) { LineScreen(network, riding, "1", {}, {}) }

    /** Riding against the list order: the arrow on the full line points up. */
    @Test fun lineReverseDirection() = shot("11b-line-reverse-direction") {
        val e = Estimate(
            0.96, StationBelief("rustaveli", "1", 0.94), StationBelief("liberty-square", "1", 0.93), 4,
            towards = StationBelief("varketili", "1", 0.95),
        )
        LineScreen(network, riding.copy(estimate = e, destination = "isani"), "1", {}, {})
    }

    @Test fun tripReverseDirection() = shot("04b-trip-reverse-direction") {
        val e = Estimate(
            0.96, StationBelief("rustaveli", "1", 0.94), StationBelief("liberty-square", "1", 0.93), 4,
            towards = StationBelief("varketili", "1", 0.95),
        )
        Trip(riding.copy(estimate = e, destination = "isani"))
    }

    @Test fun confirmStation() = shot("12-confirm-station", wholeScreen = true) {
        LineScreen(network, riding, "1", {}, {}, initialConfirmStation = "marjanishvili")
    }

    @Test fun profileScreen() = shot("13-profile") {
        ProfileScreen(
            network, Account("u1", "Ника Б.", "nika@example.com", "google.com"), profile, signInAvailable = true,
            onBack = {}, onSetHome = {}, onSetWork = {}, onToggleMute = {}, onToggleLanguage = {}, onTestVoice = {},
            onNearStation = {}, onDemo = {}, onSignOut = {},
        )
    }

    @Test fun history() = shot("14-history") {
        HistoryScreen(
            trips = listOf(
                TripInfo("a", "Руставели → Дидубе", "17 ноября, 18:42 · 31 мин · 6 станций · 4,2 МБ", recording = false),
                TripInfo("b", "Дидубе → Технический университет", "17 ноября, 09:05 · 24 мин · 8 станций · 3,1 МБ", recording = false),
                TripInfo("c", "16 ноября, 19:20", "12 мин · 1,6 МБ", recording = false),
            ),
            onBack = {}, onShare = {}, onDelete = {},
        )
    }

    @Test fun stationPicker() = shot("15-station-picker", wholeScreen = true) {
        StationPickerDialog(
            network, "Куда едете?", onPick = {}, onDismiss = {},
            shortcuts = listOf("didube" to "Домой · Дидубе", "rustaveli" to "На работу · Руставели"), onClear = {},
            frequent = stats.arrivals,
        )
    }

    @Test fun welcome() = shot("16-welcome") {
        WelcomeScreen(busy = false, message = null, onGoogle = {}, onApple = {}, onGuest = {})
    }

    /** Accessibility check: system font at 150%. */
    @Test fun largeFont() {
        RuntimeEnvironment.setFontScale(1.5f)
        shot("17-large-font") { Trip(riding) }
    }

    @Test fun largeFontHome() {
        RuntimeEnvironment.setFontScale(1.5f)
        shot("18-large-font-home") { Home() }
    }

    /** Unfolded foldable / small tablet. */
    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h900dp-xhdpi")
    fun unfolded() = shot("19-unfolded") { Trip(riding) }
}
