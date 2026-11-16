package metrovoice.core

import metrovoice.core.announce.Announcement
import metrovoice.core.engine.TripEngine
import metrovoice.core.graph.MetroGraph
import metrovoice.core.graph.MetroNetwork
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sanity check of the bundled Tbilisi package: plumbing only, timings are distance estimates. */
class TbilisiDataTest {
    private val network = MetroNetwork(MetroGraph.parse(File("../app/src/main/assets/cities/tbilisi.json").readText()))

    @Test
    fun graphShape() {
        assertEquals(listOf(16, 7), network.lines.map { it.stations.size })
        assertEquals("Площадь Свободы", network.station("liberty-square").name["ru"])
        assertEquals(listOf("2"), network.transferLines("1", "station-square-1").map { it.id })
    }

    @Test
    fun rideRustaveliToAvlabari() {
        val said = mutableListOf<Announcement>()
        val engine = TripEngine(network, listOf("ru"), listener = object : TripEngine.Listener {
            override fun onAnnouncements(announcements: List<Announcement>) { said += announcements }
        })
        val sim = RideSimulator(engine, seed = 7)
        fun fix(id: String) = network.station(id).let { engine.onLocation(it.lat, it.lon, 300.0) }

        sim.stand(15.0); fix("rustaveli"); sim.stand(15.0)
        sim.ride(network.expectedSeconds("1", 6, 5)); sim.stand(20.0); fix("liberty-square"); sim.stand(10.0)
        sim.ride(network.expectedSeconds("1", 5, 4)); sim.stand(25.0)

        val texts = said.map { it.text }
        assertEquals("Станция Руставели.", texts.first())
        assertTrue("Станция Площадь Свободы." in texts, texts.toString())
        assertTrue("Следующая станция — Авлабари." in texts, texts.toString())
        assertTrue("Станция Авлабари." in texts, texts.toString())
    }
}
