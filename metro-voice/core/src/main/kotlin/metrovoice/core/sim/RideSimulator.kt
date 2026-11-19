package metrovoice.core.sim

import metrovoice.core.announce.AnnouncementKind
import metrovoice.core.graph.MetroNetwork
import metrovoice.core.trace.TraceRecord
import metrovoice.core.trace.TraceReplayer
import kotlin.math.cos
import kotlin.random.Random

/** One ride along a line, in travel order. Consecutive legs are joined by a walking transfer. */
data class Leg(val lineId: String, val from: String, val to: String)

/**
 * Conditions of a simulated ride. These are assumptions about the real world, not measurements:
 * the simulator checks the logic, real trips check the assumptions.
 */
data class SimOptions(
    val seed: Int = 1,
    /** Phone held in the hand: tremor hides the "train stopped" signal from the motion detector. */
    val handheld: Boolean = false,
    /** No accelerometer data at all (pure location tracking). */
    val noAccelerometer: Boolean = false,
    /** Chance that a station (after the first) yields a location fix. */
    val fixProbability: Double = 1.0,
    /** Seconds after arrival until the network fix comes. */
    val fixDelaySeconds: Double = 8.0,
    /** Accuracy the provider reports, metres. */
    val fixAccuracyMeters: Double = 150.0,
    /** Actual error of the fix, metres (std dev). */
    val fixErrorMeters: Double = 80.0,
    val dwellSeconds: Double = 25.0,
    /** Real segment time relative to the estimate in the city package (0.75 = trains 25% faster). */
    val speedFactor: Double = 1.0,
    /** Index of the segment (0-based over the whole ride) with a stop in the tunnel. */
    val tunnelStopSegment: Int? = null,
    /** Destination given by the user ("home"/"work" button). */
    val destination: String? = null,
    /**
     * After the last station the rider walks 280 m away and leaves the trip running, with street
     * noise that looks like "moved for a minute, then stopped". Taken from the first real ride,
     * where this produced an announcement of a station the rider never reached.
     */
    val leavesAndForgetsToStop: Boolean = false,
)

/** Ground truth: the train stood at [stationId] from [arrive] to [depart]; [nextStationId] comes after it. */
data class TruthStop(
    val stationId: String,
    val lineId: String,
    val arrive: Double,
    val depart: Double,
    val nextStationId: String?,
    /**
     * Platform reached on foot at a transfer hub. Its twin on the other line (≈150 m away) was already
     * announced and a fix cannot tell the two apart, so announcing it is optional.
     */
    val transferArrival: Boolean = false,
)

class SimulatedRide(val records: List<TraceRecord>, val truth: List<TruthStop>, val options: SimOptions)

object RideSimulator {
    private const val HZ = 50
    const val STAND_SD = 0.06
    const val RIDE_SD = 0.8
    const val WALK_SD = 5.0

    fun generate(network: MetroNetwork, legs: List<Leg>, options: SimOptions = SimOptions()): SimulatedRide {
        val rnd = Random(options.seed)
        val records = ArrayList<TraceRecord>()
        val truth = ArrayList<TruthStop>()
        var t = 0.0

        fun r4(x: Double) = Math.round(x * 10_000.0) / 10_000.0
        fun acc(seconds: Double, sd: Double) {
            val n = (seconds * HZ).toInt()
            if (options.noAccelerometer) { t += n.toDouble() / HZ; return }
            repeat(n) {
                fun noise() = rnd.nextDouble(-1.0, 1.0) * sd * 1.732
                records += TraceRecord.Acc(r4(t), r4(noise()), r4(noise()), r4(9.81 + noise()))
                t += 1.0 / HZ
            }
        }
        // Noise levels matched to the first recorded ride (variance of linear acceleration as the detector
        // sees it): train standing ≈ 0.0007, riding ≈ 0.1, walking ≈ 4. In the hand, tremor hides the stops.
        val standSd = if (options.handheld) 0.45 else STAND_SD
        val rideSd = if (options.handheld) 0.9 else RIDE_SD
        fun fix(stationId: String) {
            val s = network.station(stationId)
            fun gaussian() = (1..6).sumOf { rnd.nextDouble(-1.0, 1.0) } / 1.414 // ≈ N(0,1)
            val dLat = gaussian() * options.fixErrorMeters / 111_320.0
            val dLon = gaussian() * options.fixErrorMeters / (111_320.0 * cos(Math.toRadians(s.lat)))
            records += TraceRecord.Loc(r4(t), s.lat + dLat, s.lon + dLon, options.fixAccuracyMeters, "sim")
        }
        fun dwell(stationId: String, lineId: String, next: String?, withFix: Boolean, transferArrival: Boolean = false) {
            val arrive = t
            records += TraceRecord.Label(r4(t), stationId)
            if (withFix && options.fixDelaySeconds < options.dwellSeconds) {
                acc(options.fixDelaySeconds, standSd)
                fix(stationId)
                acc(options.dwellSeconds - options.fixDelaySeconds, standSd)
            } else {
                acc(options.dwellSeconds, standSd)
            }
            truth += TruthStop(stationId, lineId, arrive, t, next, transferArrival)
        }

        var segment = 0
        legs.forEachIndexed { legIndex, leg ->
            val stations = network.line(leg.lineId).stations
            val from = stations.indexOf(leg.from)
            val to = stations.indexOf(leg.to)
            require(from >= 0 && to >= 0 && from != to) { "Bad leg $leg" }
            val step = if (to > from) 1 else -1
            if (legIndex > 0) acc(120.0, WALK_SD) // walking transfer

            var i = from
            // The first station of every leg has a fix: the user is at the platform with signal.
            dwell(stations[i], leg.lineId, stations[i + step], withFix = true, transferArrival = legIndex > 0)
            while (i != to) {
                val expected = network.expectedSeconds(leg.lineId, i, i + step)
                val travel = expected * options.speedFactor * (1 + rnd.nextDouble(-0.05, 0.05))
                if (segment == options.tunnelStopSegment) {
                    acc(travel * 0.4, rideSd)
                    acc(20.0, standSd)
                    acc(travel * 0.6, rideSd)
                } else {
                    acc(travel, rideSd)
                }
                segment++
                i += step
                // At the end of a leg the engine cannot know the rider gets off: the next station along the line is still right.
                val next = stations.getOrNull(i + step)
                dwell(stations[i], leg.lineId, next, withFix = rnd.nextDouble() < options.fixProbability)
            }
        }
        if (options.leavesAndForgetsToStop) {
            val exit = network.station(truth.last().stationId)
            fun gps(metersNorth: Double) {
                records += TraceRecord.Loc(r4(t), exit.lat + metersNorth / 111_320.0, exit.lon + rnd.nextDouble(-2e-5, 2e-5), 16.0, "sim-gps")
            }
            repeat(20) { i -> acc(6.0, WALK_SD); gps(280.0 * (i + 1) / 20) } // two minutes on foot
            repeat(4) {
                repeat(3) { acc(20.0, STAND_SD); gps(280.0) }
                acc(68.0, RIDE_SD) // a tram passing, the phone being carried: "train-like" vibration
                gps(280.0)
                acc(15.0, STAND_SD)
            }
        }
        return SimulatedRide(records, truth, options)
    }
}

data class Score(
    val stations: Int,
    /** Stations announced correctly while the train stood there. */
    val announced: Int,
    /** "Station X" said when the train was not at X — the failure that matters most. */
    val falseStations: Int,
    val nextSaid: Int,
    val wrongNext: Int,
    val meanLatencySeconds: Double?,
) {
    override fun toString(): String =
        "stations %d/%d, false %d, next ok %d, next wrong %d, latency %s".format(
            announced, stations, falseStations, nextSaid - wrongNext, wrongNext,
            meanLatencySeconds?.let { "%.0f s".format(it) } ?: "—",
        )
}

object RideEvaluator {
    private const val EARLY = 30.0
    private const val LATE = 15.0

    /** Runs the engine over the simulated ride (ground-truth labels are not fed in) and scores what it said. */
    fun run(network: MetroNetwork, ride: SimulatedRide): Score {
        val result = TraceReplayer.replayRecords(ride.records.asSequence(), network, listOf("en"), ride.options.destination)
        return score(ride.truth, result.spoken)
    }

    fun score(truth: List<TruthStop>, spoken: List<TraceReplayer.Spoken>): Score {
        val hit = HashSet<Int>()
        val latencies = ArrayList<Double>()
        var falseStations = 0
        var nextSaid = 0
        var wrongNext = 0
        for (s in spoken) {
            val a = s.announcement
            when (a.kind) {
                AnnouncementKind.STATION -> {
                    val k = truth.indexOfFirst { it.stationId == a.stationId && s.t >= it.arrive - EARLY && s.t <= it.depart + LATE }
                    if (k < 0) falseStations++ else if (hit.add(k)) latencies += s.t - truth[k].arrive
                }
                AnnouncementKind.NEXT_STATION -> {
                    nextSaid++
                    val ok = truth.withIndex().any { (k, stop) ->
                        val until = truth.getOrNull(k + 1)?.arrive ?: Double.MAX_VALUE
                        stop.nextStationId == a.stationId && s.t >= stop.arrive - EARLY && s.t < until
                    }
                    if (!ok) wrongNext++
                }
                AnnouncementKind.DIRECTION_UNKNOWN -> Unit
            }
        }
        val required = truth.indices.count { !truth[it].transferArrival || it in hit }
        return Score(required, hit.size, falseStations, nextSaid, wrongNext, latencies.takeIf { it.isNotEmpty() }?.average())
    }
}

/** Named scenarios on a concrete network — the regression bench for the tracker. */
object Scenarios {
    data class Scenario(val name: String, val legs: List<Leg>, val options: SimOptions, val note: String)

    fun tbilisi(): List<Scenario> {
        val line1 = listOf(Leg("1", "isani", "nadzaledevi")) // 8 segments through the centre
        val transfer = listOf(Leg("1", "rustaveli", "station-square-1"), Leg("2", "station-square-2", "delisi"))
        return listOf(
            Scenario("pocket + fix at every station", line1, SimOptions(), "ideal case"),
            Scenario("in hand + fix at every station", line1, SimOptions(handheld = true), "detector blind, fixes carry it"),
            Scenario("no accelerometer + fixes", line1, SimOptions(noAccelerometer = true), "location only"),
            Scenario("in hand + fix at 50% of stations", line1, SimOptions(handheld = true, fixProbability = 0.5, seed = 3), "gaps must be silent, not wrong"),
            Scenario("pocket + coarse fixes (600 m)", line1, SimOptions(fixAccuracyMeters = 600.0, fixErrorMeters = 300.0), "cell-only positioning"),
            Scenario("pocket + tunnel stop", line1, SimOptions(tunnelStopSegment = 3), "no false station in the tunnel"),
            Scenario("pocket + late fix (20 s)", line1, SimOptions(fixDelaySeconds = 20.0), "slow network"),
            Scenario("transfer line 1 → 2", transfer, SimOptions(), "walking transfer at Station Square"),
            Scenario("transfer, in hand", transfer, SimOptions(handheld = true), "transfer without motion detection"),
            Scenario("left the metro, trip left running", listOf(Leg("1", "liberty-square", "marjanishvili")), SimOptions(leavesAndForgetsToStop = true), "street noise must not become a station"),
            Scenario("pocket, no fixes, destination set", line1, SimOptions(fixProbability = 0.0, destination = "nadzaledevi"), "dead reckoning on estimated times"),
            Scenario("pocket, no fixes, trains 25% faster", line1, SimOptions(fixProbability = 0.0, destination = "nadzaledevi", speedFactor = 0.75), "estimated times are wrong"),
            Scenario("in hand, no fixes", line1, SimOptions(handheld = true, fixProbability = 0.0, destination = "nadzaledevi"), "nothing to go on: must stay silent"),
        )
    }
}
