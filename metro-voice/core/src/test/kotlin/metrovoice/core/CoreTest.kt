package metrovoice.core

import metrovoice.core.announce.Announcement
import metrovoice.core.announce.AnnouncementKind
import metrovoice.core.engine.TripEngine
import metrovoice.core.graph.Line
import metrovoice.core.graph.MetroGraph
import metrovoice.core.graph.MetroNetwork
import metrovoice.core.motion.MotionDetector
import metrovoice.core.motion.Phase
import metrovoice.core.motion.PhaseChange
import metrovoice.core.tracking.Position
import metrovoice.core.tracking.Tracker
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GraphTest {
    @Test
    fun rejectsUnknownStationsAndBadSegmentCount() {
        val bad = MetroGraph(
            city = "x",
            stations = Fixtures.graph.stations,
            lines = listOf(Line("Z", mapOf("en" to "Z"), null, listOf("A1", "NOPE"), listOf(1.0, 2.0))),
        )
        val e = assertFailsWith<IllegalArgumentException> { MetroNetwork(bad) }
        assertTrue("NOPE" in e.message!!)
        assertTrue("segmentSeconds" in e.message!!)
    }

    @Test
    fun parsesJsonAndEstimatesMissingSegmentTimes() {
        val json = """
            {"city":"t","stations":[
              {"id":"s1","name":{"en":"One"},"lat":41.0,"lon":44.0},
              {"id":"s2","name":{"en":"Two"},"lat":41.009,"lon":44.0}],
             "lines":[{"id":"L","name":{"en":"L"},"stations":["s1","s2"]}],
             "extraField":true}
        """.trimIndent()
        val net = MetroNetwork(MetroGraph.parse(json), avgSpeedMps = 10.0)
        val seconds = net.expectedSeconds("L", 0, 1)
        assertTrue(seconds in 95.0..105.0, "≈1 km at 10 m/s, got $seconds")
    }
}

class MotionDetectorTest {
    @Test
    fun detectsDepartureAndStopWithHysteresis() {
        val d = MotionDetector()
        val rnd = Random(1)
        val changes = mutableListOf<PhaseChange>()
        var t = 0.0
        fun feed(seconds: Double, sd: Double) {
            val end = t + seconds
            while (t < end) {
                fun n() = rnd.nextDouble(-1.0, 1.0) * sd * 1.732
                d.onSample(t, n(), n(), 9.81 + n())?.let { changes += it }
                t += 0.02
            }
        }
        feed(20.0, 0.02)
        feed(60.0, 0.3)
        feed(30.0, 0.02)

        assertEquals(listOf(Phase.STOPPED, Phase.MOVING, Phase.STOPPED), changes.map { it.phase })
        assertTrue(changes[1].atSeconds in 19.0..23.0, "departure at ${changes[1].atSeconds}")
        assertTrue(changes[2].atSeconds in 79.0..84.0, "stop at ${changes[2].atSeconds}")
    }
}

class TrackerTest {
    private val net = Fixtures.network

    @Test
    fun secondStationFixResolvesDirection() {
        val tr = Tracker(net)
        val a2 = net.station("A2")
        tr.onFix(a2.lat, a2.lon, 200.0, trainMoving = false)
        assertEquals("A2", tr.estimate().current?.stationId)
        assertTrue(tr.estimate().next!!.probability < 0.6, "direction unknown at start")

        tr.onDeparture()
        tr.onStop(movedSeconds = 92.0)
        val a3 = net.station("A3")
        tr.onFix(a3.lat, a3.lon, 200.0, trainMoving = false)

        val e = tr.estimate()
        assertEquals("A3", e.current?.stationId)
        assertTrue(e.current!!.probability > 0.85, "current ${e.current}")
        assertEquals("A4", e.next?.stationId)
        assertTrue(e.next!!.probability > 0.85, "next ${e.next}")
    }

    @Test
    fun segmentDurationAloneResolvesDirectionWhenSegmentsDiffer() {
        val tr = Tracker(net)
        tr.setStation("A3") // neighbours: A2 (90 s), A4 (150 s)
        tr.onDeparture()
        tr.onStop(movedSeconds = 148.0)
        val e = tr.estimate()
        assertEquals("A4", e.current?.stationId)
        assertTrue(e.current!!.probability > 0.85, "current ${e.current}")
    }

    @Test
    fun earlyTunnelStopIsNotAStation() {
        val tr = Tracker(net)
        tr.setStation("A1") // terminus, only one direction
        tr.onDeparture()
        tr.onStop(movedSeconds = 25.0) // stuck in the tunnel
        val mid = tr.estimate()
        assertTrue(mid.atStationProbability < 0.5, "tunnel stop taken for station: $mid")

        tr.onDeparture()
        tr.onStop(movedSeconds = 65.0) // 25 + 65 = 90 s = full segment
        val e = tr.estimate()
        assertEquals("A2", e.current?.stationId)
        assertTrue(e.current!!.probability > 0.85, "current ${e.current}")
    }

    @Test
    fun walkingAtTransferStationSpawnsOtherLine() {
        val tr = Tracker(net)
        tr.setStation("A3", towardsStationId = "A4")
        tr.onWalking()
        assertTrue(tr.hypotheses().any { it.lineId == "B" && it.position == Position.At(1) })
        val b2 = net.station("B2")
        tr.onFix(b2.lat, b2.lon, 100.0, trainMoving = false)
        tr.setDirectionTowards("B3")
        tr.onDeparture()
        tr.onStop(movedSeconds = 100.0)
        assertEquals("B3", tr.estimate().current?.stationId)
    }

    @Test
    fun wrongFixRecoversThroughReseeding() {
        val tr = Tracker(net)
        tr.setStation("A1")
        tr.onDeparture()
        tr.onStop(90.0)
        // Fix says we are actually at A4 (user picked the wrong start).
        val a4 = net.station("A4")
        repeat(3) { tr.onFix(a4.lat, a4.lon, 150.0, trainMoving = false) }
        assertEquals("A4", tr.estimate().current?.stationId)
    }
}

class EngineTest {
    private class Recorder : TripEngine.Listener {
        val said = mutableListOf<Announcement>()
        override fun onAnnouncements(announcements: List<Announcement>) { said += announcements }
    }

    @Test
    fun fullRideAnnouncesInRussianAndEnglishWithoutFalseStations() {
        val rec = Recorder()
        val engine = TripEngine(Fixtures.network, listOf("ru", "en"), listener = rec)
        val sim = RideSimulator(engine)

        sim.stand(15.0)
        sim.fixAt("A2", accuracy = 250.0, offsetMeters = 120.0)
        sim.stand(20.0)
        sim.ride(90.0)            // A2 -> A3, direction still unknown
        sim.stand(20.0)
        sim.fixAt("A3")
        sim.stand(10.0)
        sim.ride(40.0)            // A3 -> tunnel stop
        sim.stand(25.0)
        sim.ride(110.0)           // -> A4 (150 s total)
        sim.stand(25.0)

        val ru = rec.said.filter { it.language == "ru" }
        val texts = ru.map { it.text }

        assertEquals("Станция Браво.", texts.first())
        assertTrue(ru.any { it.kind == AnnouncementKind.DIRECTION_UNKNOWN })
        assertTrue("Станция Центральная. Пересадка: Линия Б." in texts, texts.toString())
        assertTrue("Следующая станция — Дельта." in texts, texts.toString())
        assertTrue("Станция Дельта." in texts, texts.toString())
        val stationOrder = ru.filter { it.kind == AnnouncementKind.STATION }.map { it.stationId }
        assertEquals(listOf("A2", "A3", "A4"), stationOrder, "no false or repeated stations")

        val en = rec.said.filter { it.language == "en" }.map { it.text }
        assertTrue("Next station: Delta." in en, en.toString())
        assertNotNull(rec.said.firstOrNull { it.key == "next_station/A4/en" })
    }
}
