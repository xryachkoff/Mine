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
)

sealed interface Command {
    /** User says "I am at this station" — correction for the tracker and ground truth for the log. */
    data class AtStation(val stationId: String) : Command
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
