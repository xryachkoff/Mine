package metrovoice.core.trace

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import metrovoice.core.announce.Announcement
import metrovoice.core.engine.TripEngine
import metrovoice.core.graph.MetroNetwork

/**
 * One line of a recorded trip (JSON Lines). `t` = seconds since trip start.
 * The phone records everything; the laptop replays it through [TripEngine] to tune thresholds.
 */
@Serializable
sealed interface TraceRecord {
    val t: Double

    @Serializable @SerialName("meta")
    data class Meta(override val t: Double, val city: String, val device: String, val app: String) : TraceRecord

    @Serializable @SerialName("acc")
    data class Acc(override val t: Double, val x: Double, val y: Double, val z: Double) : TraceRecord

    @Serializable @SerialName("gyro")
    data class Gyro(override val t: Double, val x: Double, val y: Double, val z: Double) : TraceRecord

    @Serializable @SerialName("mag")
    data class Mag(override val t: Double, val x: Double, val y: Double, val z: Double) : TraceRecord

    @Serializable @SerialName("baro")
    data class Baro(override val t: Double, val hPa: Double) : TraceRecord

    @Serializable @SerialName("loc")
    data class Loc(override val t: Double, val lat: Double, val lon: Double, val acc: Double, val provider: String? = null) : TraceRecord

    @Serializable
    data class CellObs(val type: String, val id: Long? = null, val area: Int? = null, val mcc: String? = null, val mnc: String? = null, val dbm: Int? = null, val registered: Boolean = false)

    @Serializable @SerialName("cell")
    data class Cell(override val t: Double, val cells: List<CellObs>) : TraceRecord

    @Serializable
    data class WifiObs(val bssid: String, val level: Int)

    @Serializable @SerialName("wifi")
    data class Wifi(override val t: Double, val aps: List<WifiObs>) : TraceRecord

    /** Ground truth: the user tapped "I am at this station". */
    @Serializable @SerialName("label")
    data class Label(override val t: Double, val stationId: String) : TraceRecord

    /** The rider chose where they are going (null = cleared). Replayed, unlike labels: it is input, not truth. */
    @Serializable @SerialName("dest")
    data class Dest(override val t: Double, val stationId: String? = null) : TraceRecord

    @Serializable @SerialName("phase")
    data class Phase(override val t: Double, val phase: String, val variance: Double) : TraceRecord

    @Serializable @SerialName("say")
    data class Say(override val t: Double, val key: String, val text: String) : TraceRecord
}

object TraceCodec {
    val json = Json { classDiscriminator = "type"; ignoreUnknownKeys = true; encodeDefaults = false }

    fun encode(r: TraceRecord): String = json.encodeToString(TraceRecord.serializer(), r)
    fun decode(line: String): TraceRecord = json.decodeFromString(TraceRecord.serializer(), line)
}

/** Replays a recorded trip through a fresh engine and reports what it would have said. */
object TraceReplayer {
    data class Spoken(val t: Double, val announcement: Announcement)
    data class Result(val spoken: List<Spoken>, val labels: List<TraceRecord.Label>)

    fun replay(
        lines: Sequence<String>,
        network: MetroNetwork,
        languages: List<String> = listOf("en"),
        destination: String? = null,
    ): Result = replayRecords(
        lines.filter { it.isNotBlank() }.mapNotNull { runCatching { TraceCodec.decode(it) }.getOrNull() },
        network, languages, destination,
    )

    fun replayRecords(
        records: Sequence<TraceRecord>,
        network: MetroNetwork,
        languages: List<String> = listOf("en"),
        destination: String? = null,
    ): Result {
        val spoken = mutableListOf<Spoken>()
        val labels = mutableListOf<TraceRecord.Label>()
        var now = 0.0
        val engine = TripEngine(network, languages, listener = object : TripEngine.Listener {
            override fun onAnnouncements(announcements: List<Announcement>) {
                announcements.forEach { spoken += Spoken(now, it) }
            }
        })
        destination?.let(engine::setDestination)
        for (r in records) {
            now = r.t
            when (r) {
                is TraceRecord.Acc -> engine.onAccelerometer(r.t, r.x, r.y, r.z)
                is TraceRecord.Loc -> engine.onLocation(r.t, r.lat, r.lon, r.acc)
                is TraceRecord.Dest -> engine.setDestination(r.stationId)
                is TraceRecord.Label -> labels += r // ground truth is not fed to the engine
                else -> Unit
            }
        }
        return Result(spoken, labels)
    }
}
