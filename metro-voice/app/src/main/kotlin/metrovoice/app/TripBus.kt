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
    /** SystemClock.elapsedRealtime() when the trip started and when the last location fix arrived. */
    val startedElapsedMs: Long? = null,
    val lastFixElapsedMs: Long? = null,
    val lastFixAccuracyMeters: Int? = null,
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
    private val dateFormat = java.text.SimpleDateFormat("d MMMM, HH:mm", java.util.Locale("ru"))

    private fun isTrip(f: File) = f.name.startsWith("trip-") && (f.name.endsWith(".jsonl") || f.name.endsWith(".jsonl.gz"))

    /** Sidecar with the stations passed, written during the ride: `trip-….stations`, one id per line. */
    fun stationsFile(tripFile: File): File = File(tripFile.parentFile, tripFile.name.substringBefore('.') + ".stations")

    fun list(context: Context, network: MetroNetwork?, recordingPath: String?): List<TripInfo> =
        CityRepository.tripsDir(context).listFiles().orEmpty()
            .filter(::isTrip)
            .sortedByDescending { it.name }
            .map { f ->
                val start = runCatching { nameFormat.parse(f.name.removePrefix("trip-").substringBefore('.')) }.getOrNull()
                val date = start?.let(dateFormat::format) ?: f.name
                val minutes = start?.let { ((f.lastModified() - it.time) / 60_000).coerceAtLeast(0) }
                val size = if (f.length() >= 1_000_000) "%.1f МБ".format(f.length() / 1e6) else "${f.length() / 1000} КБ"
                val stations = runCatching { stationsFile(f).readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())
                fun name(id: String) = runCatching { network?.station(id)?.name?.let { it["ru"] ?: it["en"] } }.getOrNull() ?: id
                val route = when {
                    stations.size >= 2 -> "${name(stations.first())} → ${name(stations.last())}"
                    stations.size == 1 -> name(stations.first())
                    else -> null
                }
                TripInfo(
                    path = f.absolutePath,
                    title = route ?: date,
                    subtitle = listOfNotNull(
                        date.takeIf { route != null },
                        minutes?.let { "$it мин" },
                        stations.size.takeIf { it >= 2 }?.let { metrovoice.app.ui.stationsCount(it) },
                        size,
                    ).joinToString(" · "),
                    recording = f.absolutePath == recordingPath,
                )
            }

    fun delete(path: String) {
        val f = File(path)
        stationsFile(f).delete()
        f.delete()
    }
}
