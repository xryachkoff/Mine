package metrovoice.app

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import metrovoice.core.graph.MetroGraph
import metrovoice.core.graph.MetroNetwork
import metrovoice.core.tracking.Estimate
import java.io.File

data class UiState(
    val running: Boolean = false,
    val phase: String = "—",
    val estimate: Estimate? = null,
    val lastSaid: String? = null,
    val tripFile: String? = null,
    val missingVoices: Set<String> = emptySet(),
    /** Station the user is heading to (home / work), may be set before the trip starts. */
    val destination: String? = null,
    /** Simulated ride (no sensors, nothing recorded). */
    val demo: Boolean = false,
)

sealed interface Command {
    /** User says "I am at this station" — correction for the tracker and ground truth for the log. */
    data class AtStation(val stationId: String) : Command

    /** "Going home / to work" — gives the tracker the direction right away. */
    data class Destination(val stationId: String?) : Command
}

/** Process-wide channel between the foreground service and the UI. */
object TripBus {
    val state = MutableStateFlow(UiState())
    val commands = MutableSharedFlow<Command>(extraBufferCapacity = 16)

    fun update(block: (UiState) -> UiState) = state.update(block)
}

object CityRepository {
    const val DEFAULT_CITY = "tbilisi"

    @Volatile private var cached: MetroNetwork? = null

    /** Loads `assets/cities/<city>.json`; null when the city package is not bundled yet. */
    fun load(context: Context, city: String = DEFAULT_CITY): MetroNetwork? {
        cached?.let { return it }
        val text = runCatching {
            context.assets.open("cities/$city.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null
        return MetroNetwork(MetroGraph.parse(text)).also { cached = it }
    }

    fun tripsDir(context: Context): File =
        File(context.getExternalFilesDir(null), "trips").apply { mkdirs() }
}

/** One recorded trip in the history list. */
data class TripInfo(val path: String, val title: String, val subtitle: String, val recording: Boolean)

object TripHistory {
    private val nameFormat = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
    private val titleFormat = java.text.SimpleDateFormat("d MMMM, HH:mm", java.util.Locale("ru"))

    fun list(context: Context, recordingPath: String?): List<TripInfo> =
        CityRepository.tripsDir(context).listFiles().orEmpty()
            .filter { it.name.startsWith("trip-") }
            .sortedByDescending { it.name }
            .map { f ->
                val start = runCatching { nameFormat.parse(f.name.removePrefix("trip-").substringBefore('.')) }.getOrNull()
                val minutes = start?.let { ((f.lastModified() - it.time) / 60_000).coerceAtLeast(0) }
                val size = if (f.length() >= 1_000_000) "%.1f МБ".format(f.length() / 1e6) else "${f.length() / 1000} КБ"
                TripInfo(
                    path = f.absolutePath,
                    title = start?.let(titleFormat::format) ?: f.name,
                    subtitle = listOfNotNull(minutes?.let { "$it мин" }, size).joinToString(" · "),
                    recording = f.absolutePath == recordingPath,
                )
            }
}
