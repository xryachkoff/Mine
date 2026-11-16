package metrovoice.core.osm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import metrovoice.core.graph.Line
import metrovoice.core.graph.MetroGraph
import metrovoice.core.graph.Names
import metrovoice.core.graph.Station
import metrovoice.core.graph.Transfer
import metrovoice.core.graph.distanceMeters

/**
 * Converts an Overpass API JSON export (subway route relations + their nodes + station nodes)
 * into a [MetroGraph]. Segment times are left empty: they get estimated from distance until
 * recorded rides provide measured values.
 */
object OsmImporter {

    @Serializable
    data class OsmMember(val type: String, val ref: Long, val role: String = "")

    @Serializable
    data class OsmElement(
        val type: String,
        val id: Long,
        val lat: Double? = null,
        val lon: Double? = null,
        val tags: Map<String, String> = emptyMap(),
        val members: List<OsmMember> = emptyList(),
    )

    @Serializable
    data class OsmExport(val elements: List<OsmElement>)

    private val json = Json { ignoreUnknownKeys = true }

    data class Options(
        val city: String,
        /** A stop position is matched to a station node within this distance. */
        val stationMatchMeters: Double = 400.0,
        /** Stations of different lines closer than this become a transfer. */
        val transferMeters: Double = 350.0,
        /** Hand-made name fixes applied on top of OSM. */
        val overrides: NameOverrides = NameOverrides(),
    )

    /** data/overrides/<city>.json: {"stations": {id: {lang: name}}, "lines": {id: {lang: name}}} */
    @Serializable
    data class NameOverrides(
        val stations: Map<String, Names> = emptyMap(),
        val lines: Map<String, Names> = emptyMap(),
    ) {
        companion object {
            fun parse(text: String): NameOverrides = Json { ignoreUnknownKeys = true }.decodeFromString(serializer(), text)
        }
    }

    fun import(overpassJson: String, options: Options): MetroGraph {
        val export = json.decodeFromString(OsmExport.serializer(), overpassJson)
        val nodes = export.elements.filter { it.type == "node" && it.lat != null && it.lon != null }.associateBy { it.id }
        val stationNodes = nodes.values.filter { it.tags["railway"] == "station" }
        val routes = export.elements.filter { it.type == "relation" && it.tags["route"] == "subway" }
        require(routes.isNotEmpty()) { "No route=subway relations in export" }

        val stations = linkedMapOf<String, Station>()
        val lines = mutableListOf<Line>()

        // One relation per direction is typical: group by ref/name and keep the one with most stops.
        val groups = routes.groupBy { it.tags["ref"] ?: it.tags["name:en"] ?: it.tags["name"] ?: it.id.toString() }
        for ((key, rels) in groups) {
            val rel = rels.maxBy { r -> r.members.count { it.isStop() } }
            val stopNodes = rel.members.filter { it.type == "node" && it.isStop() }.mapNotNull { nodes[it.ref] }
            val ids = mutableListOf<String>()
            for (stop in stopNodes) {
                val station = stationNodes
                    .map { it to distanceMeters(stop.lat!!, stop.lon!!, it.lat!!, it.lon!!) }
                    .filter { it.second <= options.stationMatchMeters }
                    .minByOrNull { it.second }?.first ?: stop
                val s = toStation(station)
                stations.putIfAbsent(s.id, s)
                if (ids.lastOrNull() != s.id) ids += s.id
            }
            if (ids.size < 2) continue
            val lineId = slug(key)
            // Route names carry the direction ("Line: A → B"); keep only the line name.
            val lineNames = names(rel.tags).mapValues { it.value.substringBefore(":").trim() }
            lines += Line(
                id = lineId,
                name = lineNames + options.overrides.lines[lineId].orEmpty(),
                color = rel.tags["colour"],
                stations = ids,
            )
        }

        val transfers = mutableListOf<Transfer>()
        for ((i, a) in lines.withIndex()) for (b in lines.drop(i + 1)) {
            for (sa in a.stations) for (sb in b.stations) {
                if (sa == sb) continue
                val x = stations.getValue(sa)
                val y = stations.getValue(sb)
                val d = distanceMeters(x.lat, x.lon, y.lat, y.lon)
                if (d <= options.transferMeters) transfers += Transfer(sa, sb, walkSeconds = 60.0 + d / 1.2)
            }
        }
        val finalStations = stations.values.map { st ->
            options.overrides.stations[st.id]?.let { st.copy(name = st.name + it) } ?: st
        }
        val unknown = options.overrides.stations.keys - stations.keys
        require(unknown.isEmpty()) { "Overrides for unknown stations: $unknown" }
        return MetroGraph(options.city, finalStations, lines, transfers)
    }

    private fun OsmMember.isStop() = role == "stop" || role.startsWith("stop_")

    private fun toStation(n: OsmElement): Station {
        val nm = names(n.tags)
        val id = slug(nm["en"] ?: nm["ka"] ?: "osm-${n.id}")
        return Station(id, nm, n.lat!!, n.lon!!)
    }

    private fun names(tags: Map<String, String>): Names = buildMap {
        tags["name"]?.let { put("ka", it) }
        tags["name:ka"]?.let { put("ka", it) }
        tags["name:en"]?.let { put("en", it) }
        tags["name:ru"]?.let { put("ru", it) }
        if ("en" !in this) tags["int_name"]?.let { put("en", it) }
    }

    fun slug(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifEmpty { "x" }
}
