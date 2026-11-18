package metrovoice.core.tools

import kotlinx.serialization.json.Json
import metrovoice.core.graph.MetroGraph
import metrovoice.core.graph.MetroNetwork
import metrovoice.core.osm.OsmImporter
import metrovoice.core.trace.TraceReplayer
import java.io.File

private val pretty = Json { prettyPrint = true }

/** Reads .jsonl or .jsonl.gz; a gzip cut short (app killed) yields the lines written so far. */
fun openTrace(file: File): java.io.BufferedReader {
    if (!file.name.endsWith(".gz")) return file.bufferedReader()
    // Read raw bytes until the stream ends or breaks, so nothing decoded is lost on truncation.
    val bytes = java.io.ByteArrayOutputStream()
    java.util.zip.GZIPInputStream(file.inputStream()).use { input ->
        val buf = ByteArray(8192)
        try {
            while (true) { val n = input.read(buf); if (n < 0) break; bytes.write(buf, 0, n) }
        } catch (e: java.io.EOFException) {
            System.err.println("warning: trip file truncated, using the part that was written")
        }
    }
    return bytes.toString(Charsets.UTF_8).reader().buffered()
}

/**
 * Developer tools:
 *   import-osm <overpass.json> <graph.json> <city> [overrides.json]
 *   replay <graph.json> <trip.jsonl[.gz]>
 *   simulate <graph.json>            scenario bench on synthetic rides
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "import-osm" -> {
            val (input, output, city) = Triple(args[1], args[2], args.getOrElse(3) { "city" })
            val overrides = args.getOrNull(4)?.let { OsmImporter.NameOverrides.parse(File(it).readText()) }
                ?: OsmImporter.NameOverrides()
            val graph = OsmImporter.import(File(input).readText(), OsmImporter.Options(city, overrides = overrides))
            MetroNetwork(graph) // validates
            File(output).apply { parentFile?.mkdirs() }.writeText(pretty.encodeToString(MetroGraph.serializer(), graph))
            println("Wrote $output: ${graph.lines.size} lines, ${graph.stations.size} stations, ${graph.transfers.size} transfers")
            graph.lines.forEach { l -> println("  ${l.id} ${l.name["en"]}: " + l.stations.joinToString(" → ")) }
        }
        "replay" -> {
            val network = MetroNetwork(MetroGraph.parse(File(args[1]).readText()))
            val result = openTrace(File(args[2])).useLines { TraceReplayer.replay(it, network) }
            val events = result.spoken.map { it.t to "SAY   ${it.announcement.text}" } +
                result.labels.map { it.t to "TRUTH ${it.stationId}" }
            events.sortedBy { it.first }.forEach { (t, s) -> println("%8.1f  %s".format(t, s)) }
        }
        "simulate" -> {
            val network = MetroNetwork(MetroGraph.parse(File(args[1]).readText()))
            println("%-40s %s".format("scenario", "result"))
            for (sc in metrovoice.core.sim.Scenarios.tbilisi()) {
                val ride = metrovoice.core.sim.RideSimulator.generate(network, sc.legs, sc.options)
                println("%-40s %s".format(sc.name, metrovoice.core.sim.RideEvaluator.run(network, ride)))
                if (args.getOrNull(2)?.let { it in sc.name } == true) {
                    val r = metrovoice.core.trace.TraceReplayer.replayRecords(ride.records.asSequence(), network, listOf("en"), sc.options.destination)
                    val events = r.spoken.map { it.t to "SAY   ${it.announcement.text}" } +
                        ride.truth.map { it.arrive to "TRUTH arrive ${it.stationId} (line ${it.lineId}), departs %.0f".format(it.depart) } +
                        ride.records.filterIsInstance<metrovoice.core.trace.TraceRecord.Loc>().map { it.t to "FIX" }
                    events.sortedBy { it.first }.forEach { (t, e) -> println("    %7.1f  %s".format(t, e)) }
                }
            }
        }
        else -> println("usage: import-osm <overpass.json> <graph.json> <city> [overrides.json] | replay <graph.json> <trip.jsonl>")
    }
}
