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
            "pocket, no fixes, destination set",
        )
        for (name in mustBeComplete) {
            val score = scores.getValue(name)
            assertEquals(score.stations, score.announced, "missed stations in: $name ($score)")
            assertTrue(score.nextSaid >= score.stations - 2, "too few next-station phrases in: $name ($score)")
        }
    }

    @Test
    fun tappingStationsAlongTheLineKeepsDirection() {
        // What the first real ride showed: every tap reset the direction to 50/50.
        val tr = Tracker(network)
        tr.setStation("technical-university")
        assertTrue(tr.estimate().next!!.probability < 0.6)
        tr.setStation("tsereteli")
        val e = tr.estimate()
        assertEquals("tsereteli", e.current?.stationId)
        assertEquals("station-square-2", e.next?.stationId)
        assertTrue(e.next!!.probability > 0.9, "next ${e.next}")
        tr.setStation("tsereteli") // tapping the same station again must not lose it
        assertTrue(tr.estimate().next!!.probability > 0.9)
    }
}
