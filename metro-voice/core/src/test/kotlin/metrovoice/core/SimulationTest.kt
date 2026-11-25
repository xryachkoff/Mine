package metrovoice.core

import metrovoice.core.graph.MetroGraph
import metrovoice.core.graph.MetroNetwork
import metrovoice.core.sim.RideEvaluator
import metrovoice.core.sim.RideSimulator
import metrovoice.core.sim.Scenarios
import metrovoice.core.tracking.Tracker
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Scenario bench on the real Tbilisi graph with synthetic sensors. */
class SimulationTest {
    private val network = MetroNetwork(MetroGraph.parse(File("../app/src/main/assets/cities/tbilisi.json").readText()))
    private val scores = Scenarios.tbilisi().associate { sc ->
        sc.name to RideEvaluator.run(network, RideSimulator.generate(network, sc.legs, sc.options))
    }

    @Test
    fun neverAnnouncesAWrongStationOrDirection() {
        for ((name, score) in scores) {
            assertEquals(0, score.falseStations, "false station in: $name ($score)")
            assertEquals(0, score.wrongNext, "wrong next station in: $name ($score)")
        }
    }

    @Test
    fun fullCoverageWheneverEveryStationHasAFix() {
        val mustBeComplete = listOf(
            "pocket + fix at every station",
            "in hand + fix at every station", // the failure seen on the first real ride: detector stuck in MOVING
            "no accelerometer + fixes",
            "pocket + tunnel stop",
            "pocket + late fix (20 s)",
            "transfer line 1 → 2",
            "transfer, in hand",
            "left the metro, trip left running",
            "got off, long escalator at the station",
            "pocket, no fixes, destination set",
        )
        for (name in mustBeComplete) {
            val score = scores.getValue(name)
            assertEquals(score.stations, score.announced, "missed stations in: $name ($score)")
            assertTrue(score.nextSaid >= score.stations - 2, "too few next-station phrases in: $name ($score)")
        }
    }

    @Test
    fun standingAtAStationNeverRevealsDirection() {
        // First real ride: 50 near-identical fixes at the entrance, then a departure, and the app
        // claimed to know the direction before the train had gone anywhere.
        val tr = Tracker(network)
        val s = network.station("liberty-square")
        var t = 0.0
        fun fix(northMeters: Double, eastMeters: Double) {
            tr.onFix(s.lat + northMeters / 111_320.0, s.lon + eastMeters / 83_000.0, 12.0, t)
            t += 5.0
        }
        repeat(10) { fix(60.0 + it, 40.0) }
        tr.onDeparture()
        repeat(40) { fix(50.0 + it % 7, 45.0 - it % 5) }
        val next = tr.estimate().next!!
        assertTrue(next.probability < 0.7, "direction must still be open, got $next")
    }

    @Test
    fun leavingTheMetroStopsTracking() {
        val tr = Tracker(network)
        val s = network.station("marjanishvili")
        tr.onFix(s.lat, s.lon, 30.0, 0.0)
        assertEquals("marjanishvili", tr.estimate().current?.stationId)
        var t = 10.0
        repeat(30) { // 280 m away, accurate GPS, for two and a half minutes
            tr.onFix(s.lat + 280.0 / 111_320.0, s.lon + it * 1e-6, 16.0, t)
            t += 5.0
        }
        assertTrue(tr.estimate().outside)
        assertEquals(null, tr.estimate().current)
        tr.onDeparture(); tr.onStop(68.0, t) // street noise: nothing to move
        assertEquals(null, tr.estimate().current)
        tr.onFix(s.lat, s.lon, 30.0, t + 600) // back at the station: tracking resumes
        assertEquals("marjanishvili", tr.estimate().current?.stationId)
        assertTrue(!tr.estimate().outside)
    }

    @Test
    fun tappingStationsAlongTheLineKeepsDirection() {
        // What the first real ride showed: every tap reset the direction to 50/50.
        val tr = Tracker(network)
        tr.setStation("technical-university")
        assertTrue(tr.estimate().next!!.probability < 0.6)
        assertTrue(tr.estimate().towards!!.probability < 0.6, "no direction claimed while it is unknown")
        tr.setStation("tsereteli")
        val e = tr.estimate()
        assertEquals("tsereteli", e.current?.stationId)
        assertEquals("station-square-2", e.next?.stationId)
        assertTrue(e.next!!.probability > 0.9, "next ${e.next}")
        assertEquals("station-square-2", e.towards?.stationId, "direction is named by the terminus ahead")
        assertTrue(e.towards!!.probability > 0.9)
        tr.setStation("tsereteli") // tapping the same station again must not lose it
        assertTrue(tr.estimate().next!!.probability > 0.9)
    }
}
