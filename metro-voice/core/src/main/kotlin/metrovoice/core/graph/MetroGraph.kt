package metrovoice.core.graph

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Localized names: language code ("en", "ru", "ka") -> text. */
typealias Names = Map<String, String>

@Serializable
data class Station(
    val id: String,
    val name: Names,
    val lat: Double,
    val lon: Double,
)

@Serializable
data class Line(
    val id: String,
    val name: Names,
    val color: String? = null,
    /** Station ids in track order. */
    val stations: List<String>,
    /** Measured moving time per segment (size = stations.size - 1). Null = estimate from distance. */
    val segmentSeconds: List<Double>? = null,
    /** Kind of transport: "subway", later "tram", "bus", "light_rail". Lines are grouped by it in the UI. */
    val mode: String = "subway",
)

@Serializable
data class Transfer(
    val from: String,
    val to: String,
    val walkSeconds: Double = 120.0,
)

/** City package as stored in graph.json. */
@Serializable
data class MetroGraph(
    val city: String,
    /** Display name of the city by language. */
    val cityName: Names = emptyMap(),
    val stations: List<Station>,
    val lines: List<Line>,
    val transfers: List<Transfer> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): MetroGraph = json.decodeFromString(serializer(), text)
    }
}

/** Indexed, validated view over [MetroGraph] used by the tracker. */
class MetroNetwork(
    val graph: MetroGraph,
    /**
     * Average speed (m/s) between departure and stop, used when a segment has no measured time.
     * Calibrated on the first recorded Tbilisi ride: 1346 m in ~80 s and 904 m in ~51 s.
     */
    private val avgSpeedMps: Double = 17.0,
) {
    private val stationsById = graph.stations.associateBy { it.id }
    private val linesById = graph.lines.associateBy { it.id }
    private val transfersFrom: Map<String, List<Transfer>> =
        (graph.transfers + graph.transfers.map { Transfer(it.to, it.from, it.walkSeconds) })
            .groupBy { it.from }

    val lines: List<Line> get() = graph.lines

    init {
        val errors = validate()
        require(errors.isEmpty()) { "Invalid metro graph:\n" + errors.joinToString("\n") }
    }

    fun station(id: String): Station = stationsById.getValue(id)
    fun line(id: String): Line = linesById.getValue(id)

    fun stationAt(lineId: String, index: Int): Station = station(line(lineId).stations[index])

    fun isValidIndex(lineId: String, index: Int): Boolean = index in line(lineId).stations.indices

    fun transfersAt(stationId: String): List<Transfer> = transfersFrom[stationId].orEmpty()

    /** Lines (other than [lineId]) reachable by transfer from [stationId]. */
    fun transferLines(lineId: String, stationId: String): List<Line> =
        transfersAt(stationId)
            .flatMap { t -> graph.lines.filter { it.id != lineId && t.to in it.stations } }
            .distinctBy { it.id }

    /** Expected moving seconds between adjacent indices [a] and [b] on [lineId]. */
    fun expectedSeconds(lineId: String, a: Int, b: Int): Double {
        require(kotlin.math.abs(a - b) == 1) { "Not adjacent: $a -> $b" }
        val line = line(lineId)
        val seg = minOf(a, b)
        line.segmentSeconds?.getOrNull(seg)?.let { return it }
        val s1 = stationAt(lineId, a)
        val s2 = stationAt(lineId, b)
        return distanceMeters(s1.lat, s1.lon, s2.lat, s2.lon) / avgSpeedMps
    }

    /** True when the segment time comes from measurements rather than a distance estimate. */
    fun isMeasured(lineId: String, a: Int, b: Int): Boolean =
        line(lineId).segmentSeconds?.getOrNull(minOf(a, b)) != null

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        val ids = graph.stations.map { it.id }
        ids.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { errors += "Duplicate station id: $it" }
        for (line in graph.lines) {
            if (line.stations.size < 2) errors += "Line ${line.id} has fewer than 2 stations"
            line.stations.filter { it !in stationsById }.forEach { errors += "Line ${line.id}: unknown station $it" }
            line.segmentSeconds?.let {
                if (it.size != line.stations.size - 1) errors += "Line ${line.id}: segmentSeconds size ${it.size} != ${line.stations.size - 1}"
            }
        }
        for (t in graph.transfers) {
            if (t.from !in stationsById) errors += "Transfer: unknown station ${t.from}"
            if (t.to !in stationsById) errors += "Transfer: unknown station ${t.to}"
        }
        return errors
    }
}

private const val EARTH_RADIUS_M = 6_371_000.0

fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
}

/** Distance from a point to the straight segment between two coordinates (local flat projection). */
fun distanceToSegmentMeters(lat: Double, lon: Double, lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val mPerDegLat = 111_320.0
    val mPerDegLon = 111_320.0 * cos(Math.toRadians(lat))
    val px = (lon - lon1) * mPerDegLon
    val py = (lat - lat1) * mPerDegLat
    val bx = (lon2 - lon1) * mPerDegLon
    val by = (lat2 - lat1) * mPerDegLat
    val len2 = bx * bx + by * by
    val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
    val dx = px - t * bx
    val dy = py - t * by
    return sqrt(dx * dx + dy * dy)
}
