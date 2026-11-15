package metrovoice.core

import metrovoice.core.engine.TripEngine
import metrovoice.core.graph.Line
import metrovoice.core.graph.MetroGraph
import metrovoice.core.graph.MetroNetwork
import metrovoice.core.graph.Station
import metrovoice.core.graph.Transfer
import kotlin.random.Random

/**
 * FICTIONAL test network — not real Tbilisi data.
 *
 * Line A runs north along a meridian, ~1.2 km between stations (A1..A5).
 * Line B crosses at A3 via a transfer to B2.
 * Segment A3–A4 is deliberately long (150 s) so direction can be inferred from duration alone.
 */
object Fixtures {
    private const val LAT0 = 41.70
    private const val LON0 = 44.80
    private const val STEP = 0.0108 // ≈ 1.2 km of latitude

    private fun st(id: String, en: String, ru: String, lat: Double, lon: Double) =
        Station(id, mapOf("en" to en, "ru" to ru), lat, lon)

    val graph = MetroGraph(
        city = "testville",
        stations = listOf(
            st("A1", "Alpha", "Альфа", LAT0, LON0),
            st("A2", "Bravo", "Браво", LAT0 + STEP, LON0),
            st("A3", "Central", "Центральная", LAT0 + 2 * STEP, LON0),
            st("A4", "Delta", "Дельта", LAT0 + 3 * STEP, LON0),
            st("A5", "Echo", "Эхо", LAT0 + 4 * STEP, LON0),
            st("B1", "West", "Западная", LAT0 + 2 * STEP, LON0 - 0.015),
            st("B2", "Central B", "Центральная Б", LAT0 + 2 * STEP + 0.0005, LON0 + 0.0005),
            st("B3", "East", "Восточная", LAT0 + 2 * STEP, LON0 + 0.015),
        ),
        lines = listOf(
            Line("A", mapOf("en" to "Line A", "ru" to "Линия А"), "#d00", listOf("A1", "A2", "A3", "A4", "A5"), listOf(90.0, 90.0, 150.0, 90.0)),
            Line("B", mapOf("en" to "Line B", "ru" to "Линия Б"), "#0a0", listOf("B1", "B2", "B3"), listOf(100.0, 100.0)),
        ),
        transfers = listOf(Transfer("A3", "B2", 180.0)),
    )

    val network = MetroNetwork(graph)

    fun station(id: String) = network.station(id)
}

/** Generates accelerometer streams for stopped / moving / walking periods at 50 Hz. */
class RideSimulator(private val engine: TripEngine, seed: Int = 42) {
    private val rnd = Random(seed)
    var t = 0.0
        private set
    private val dt = 1.0 / 50

    private fun noise(sd: Double) = rnd.nextDouble(-1.0, 1.0) * sd * 1.732 // uniform with given sd

    private fun run(seconds: Double, sd: Double) {
        val end = t + seconds
        while (t < end) {
            engine.onAccelerometer(t, noise(sd), noise(sd), 9.81 + noise(sd))
            t += dt
        }
    }

    fun stand(seconds: Double) = run(seconds, 0.02)
    fun ride(seconds: Double) = run(seconds, 0.3)
    fun walk(seconds: Double) = run(seconds, 2.5)

    fun fixAt(stationId: String, accuracy: Double = 200.0, offsetMeters: Double = 0.0) {
        val s = Fixtures.station(stationId)
        engine.onLocation(s.lat + offsetMeters / 111_000.0, s.lon, accuracy)
    }
}
