package metrovoice.core

import metrovoice.core.announce.Announcement
import metrovoice.core.engine.TripEngine
import metrovoice.core.graph.MetroNetwork
import metrovoice.core.osm.OsmImporter
import metrovoice.core.trace.TraceCodec
import metrovoice.core.trace.TraceRecord
import metrovoice.core.trace.TraceReplayer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OsmImporterTest {
    // Synthetic Overpass export: two directions of one line + a second line crossing it. Not real data.
    private val export = """
    {"elements":[
      {"type":"relation","id":1,"tags":{"route":"subway","ref":"1","name:en":"Red: A → C","colour":"#f00"},
       "members":[{"type":"node","ref":11,"role":"stop"},{"type":"node","ref":21,"role":"platform"},
                  {"type":"node","ref":12,"role":"stop"},{"type":"node","ref":13,"role":"stop_exit_only"}]},
      {"type":"relation","id":2,"tags":{"route":"subway","ref":"1","name:en":"Red: C → A"},
       "members":[{"type":"node","ref":13,"role":"stop"},{"type":"node","ref":12,"role":"stop"}]},
      {"type":"relation","id":3,"tags":{"route":"subway","ref":"2","name:en":"Blue"},
       "members":[{"type":"node","ref":14,"role":"stop"},{"type":"node","ref":15,"role":"stop"}]},
      {"type":"node","id":11,"lat":41.700,"lon":44.800,"tags":{"public_transport":"stop_position"}},
      {"type":"node","id":12,"lat":41.710,"lon":44.800,"tags":{"public_transport":"stop_position"}},
      {"type":"node","id":13,"lat":41.720,"lon":44.800,"tags":{"public_transport":"stop_position"}},
      {"type":"node","id":14,"lat":41.7102,"lon":44.8020,"tags":{"public_transport":"stop_position"}},
      {"type":"node","id":15,"lat":41.7100,"lon":44.8200,"tags":{"public_transport":"stop_position"}},
      {"type":"node","id":101,"lat":41.7001,"lon":44.8001,"tags":{"railway":"station","name":"ა","name:en":"Alpha","name:ru":"Альфа"}},
      {"type":"node","id":102,"lat":41.7101,"lon":44.8001,"tags":{"railway":"station","name":"ბ","name:en":"Bravo Square 1"}},
      {"type":"node","id":103,"lat":41.7201,"lon":44.8001,"tags":{"railway":"station","name:en":"Charlie"}},
      {"type":"node","id":104,"lat":41.7103,"lon":44.8021,"tags":{"railway":"station","name:en":"Bravo Square 2"}},
      {"type":"node","id":105,"lat":41.7101,"lon":44.8201,"tags":{"railway":"station","name:en":"Delta"}}
    ]}
    """.trimIndent()

    @Test
    fun buildsLinesStationsAndTransfers() {
        val g = OsmImporter.import(export, OsmImporter.Options("test"))
        MetroNetwork(g)
        assertEquals(2, g.lines.size)
        val red = g.lines.first { it.id == "1" }
        assertEquals(listOf("alpha", "bravo-square-1", "charlie"), red.stations, "longest direction kept, platform ignored")
        assertEquals("Альфа", g.stations.first { it.id == "alpha" }.name["ru"])
        assertEquals("ა", g.stations.first { it.id == "alpha" }.name["ka"])
        assertEquals(1, g.transfers.size)
        assertEquals(setOf("bravo-square-1", "bravo-square-2"), setOf(g.transfers[0].from, g.transfers[0].to))
    }
}

class TraceTest {
    @Test
    fun recordsRoundTripAsJsonLines() {
        val r = TraceRecord.Cell(1.5, listOf(TraceRecord.CellObs("lte", 123L, 7, "282", "02", -90, true)))
        val line = TraceCodec.encode(r)
        assertTrue(line.startsWith("{\"type\":\"cell\""), line)
        assertEquals(r, TraceCodec.decode(line))
    }

    @Test
    fun replayReproducesLiveAnnouncements() {
        // Record a simulated ride, then replay the JSONL and compare.
        val lines = mutableListOf<String>()
        val live = mutableListOf<Announcement>()
        val engine = TripEngine(Fixtures.network, listOf("en"), listener = object : TripEngine.Listener {
            override fun onAnnouncements(announcements: List<Announcement>) { live += announcements }
        })
        val rnd = Random(3)
        var t = 0.0
        fun period(sec: Double, sd: Double) {
            val end = t + sec
            while (t < end) {
                fun n() = rnd.nextDouble(-1.0, 1.0) * sd * 1.732
                val (x, y, z) = Triple(n(), n(), 9.81 + n())
                engine.onAccelerometer(t, x, y, z)
                lines += TraceCodec.encode(TraceRecord.Acc(t, x, y, z))
                t += 0.02
            }
        }
        fun fix(id: String) {
            val s = Fixtures.station(id)
            engine.onLocation(s.lat, s.lon, 150.0)
            lines += TraceCodec.encode(TraceRecord.Loc(t, s.lat, s.lon, 150.0))
            lines += TraceCodec.encode(TraceRecord.Label(t, id))
        }
        period(15.0, 0.02); fix("A1"); period(10.0, 0.02)
        period(90.0, 0.3); period(20.0, 0.02); fix("A2"); period(10.0, 0.02)

        val result = TraceReplayer.replay(lines.asSequence(), Fixtures.network, listOf("en"))
        assertEquals(live.map { it.text }, result.spoken.map { it.announcement.text })
        assertEquals(listOf("A1", "A2"), result.labels.map { it.stationId })
        assertTrue(live.any { it.text == "Bravo." }, live.toString())
    }
}
