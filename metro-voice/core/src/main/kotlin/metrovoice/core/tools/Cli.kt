package metrovoice.core.tools

import kotlinx.serialization.json.Json
import metrovoice.core.graph.MetroGraph
import metrovoice.core.graph.MetroNetwork
import metrovoice.core.osm.OsmImporter
import metrovoice.core.trace.TraceReplayer
import java.io.File

private val pretty = Json { prettyPrint = true }

/**
 * Developer tools:
 *   import-osm <overpass.json> <graph.json> <city>
 *   replay <graph.json> <trip.jsonl>
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "import-osm" -> {
            val (input, output, city) = Triple(args[1], args[2], args.getOrElse(3) { "city" })
            val graph = OsmImporter.import(File(input).readText(), OsmImporter.Options(city))
            MetroNetwork(graph) // validates
            File(output).apply { parentFile?.mkdirs() }.writeText(pretty.encodeToString(MetroGraph.serializer(), graph))
            println("Wrote $output: ${graph.lines.size} lines, ${graph.stations.size} stations, ${graph.transfers.size} transfers")
            graph.lines.forEach { l -> println("  ${l.id} ${l.name["en"]}: " + l.stations.joinToString(" → ")) }
        }
        "replay" -> {
            val network = MetroNetwork(MetroGraph.parse(File(args[1]).readText()))
            val result = File(args[2]).bufferedReader().useLines { TraceReplayer.replay(it, network) }
            val events = result.spoken.map { it.t to "SAY   ${it.announcement.text}" } +
                result.labels.map { it.t to "TRUTH ${it.stationId}" }
            events.sortedBy { it.first }.forEach { (t, s) -> println("%8.1f  %s".format(t, s)) }
        }
        else -> println("usage: import-osm <overpass.json> <graph.json> <city> | replay <graph.json> <trip.jsonl>")
    }
}
