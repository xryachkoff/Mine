package metrovoice.core.tracking

import metrovoice.core.graph.MetroNetwork
import metrovoice.core.graph.distanceMeters
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/** Where a hypothesis puts the train on one line. */
sealed interface Position {
    /** Standing at station [index]. */
    data class At(val index: Int) : Position

    /** Moving from station [from] to adjacent station [to]. */
    data class Between(val from: Int, val to: Int) : Position
}

data class Hypothesis(
    val lineId: String,
    val position: Position,
    /** +1 = towards higher indices, -1 = towards lower. */
    val direction: Int,
    val weight: Double,
    /** Moving time accumulated in the current segment (seconds), excluding tunnel stops. */
    val movingSeconds: Double = 0.0,
)

data class StationBelief(val stationId: String, val lineId: String, val probability: Double)

data class Estimate(
    /** Probability that the train is standing at a station (vs. in a tunnel). */
    val atStationProbability: Double,
    val current: StationBelief?,
    val next: StationBelief?,
    val hypothesisCount: Int,
)

/**
 * Probabilistic tracker over the metro graph (forward filter of a hidden Markov model).
 *
 * Hidden state: (line, station-or-segment, direction). Observations: network/GPS fixes at stations,
 * departure and stop events from [metrovoice.core.motion.MotionDetector], walking (possible transfer),
 * and explicit user input. Several hypotheses are kept until an observation tells them apart.
 */
class Tracker(
    private val network: MetroNetwork,
    private val config: Config = Config(),
) {
    data class Config(
        /** Minimum sigma for a location fix, metres. Network fixes underground are coarse. */
        val minFixSigmaMeters: Double = 150.0,
        /** Stations farther than this from a fix are not seeded. */
        val seedRadiusMeters: Double = 1500.0,
        /** Sigma of ln(actual / expected) segment time when the time was measured. */
        val measuredSpread: Double = 0.15,
        /** Sigma of ln(actual / expected) when the time is estimated from distance. */
        val estimatedSpread: Double = 0.25,
        /** Likelihood that a stop early in a segment is a tunnel stop. */
        val earlyTunnelStop: Double = 0.15,
        /** Likelihood that a stop late in a segment is a tunnel stop. */
        val lateTunnelStop: Double = 0.03,
        /** Prior weight of passing a station without stopping. */
        val skipStation: Double = 0.05,
        /** Share of weight moved to transfer hypotheses when walking is detected at a transfer station. */
        val transferShare: Double = 0.4,
        /** Weight factor for hypotheses heading away from the user's destination. */
        val wrongWayToDestination: Double = 0.02,
        /** Mass of fresh hypotheses mixed in on every fix, for recovery from earlier mistakes. */
        val reseedMass: Double = 0.05,
        val pruneBelow: Double = 1e-4,
        val maxHypotheses: Int = 300,
    )

    private var hypotheses: List<Hypothesis> = emptyList()

    /** Station the user is heading to ("home", "work"), possibly on another line. */
    var destination: String? = null
        private set

    val isTracking: Boolean get() = hypotheses.isNotEmpty()

    fun hypotheses(): List<Hypothesis> = hypotheses

    fun reset() {
        hypotheses = emptyList()
        destination = null
    }

    /**
     * Destination known (e.g. "going home"): hypotheses heading away from it are strongly down-weighted.
     * Soft, not a hard filter, so a wrong destination cannot make the tracker blind.
     */
    fun setDestination(stationId: String?) {
        destination = stationId
        if (stationId != null && hypotheses.isNotEmpty()) hypotheses = normalize(hypotheses.map(::applyDestination))
    }

    /** User says "I am at station X" (tap on the line strip). Direction stays unknown unless given. */
    fun setStation(stationId: String, towardsStationId: String? = null) {
        val seeded = mutableListOf<Hypothesis>()
        for (line in network.lines) {
            val i = line.stations.indexOf(stationId)
            if (i < 0) continue
            for (d in directionsFrom(line.id, i)) {
                if (towardsStationId != null && !isTowards(line.id, i, d, towardsStationId)) continue
                seeded += Hypothesis(line.id, Position.At(i), d, 1.0)
            }
        }
        hypotheses = normalize(seeded.map(::applyDestination))
    }

    /** User says "I am going towards X" (destination or terminus). Keeps only consistent hypotheses. */
    fun setDirectionTowards(stationId: String) {
        val kept = hypotheses.filter { h ->
            isTowards(h.lineId, baseIndex(h), h.direction, stationId)
        }
        if (kept.isNotEmpty()) hypotheses = normalize(kept)
    }

    /** A location fix (network or GNSS). Ignored while moving: tunnels have no fresh fixes. */
    fun onFix(lat: Double, lon: Double, accuracyMeters: Double, trainMoving: Boolean) {
        if (trainMoving) return
        val sigma = max(accuracyMeters, config.minFixSigmaMeters)
        val fresh = seedFromFix(lat, lon, sigma).map(::applyDestination)
        if (fresh.isEmpty()) return
        if (hypotheses.isEmpty()) {
            hypotheses = normalize(fresh)
            return
        }
        val updated = hypotheses.map { h ->
            val like = when (val p = h.position) {
                is Position.At -> gauss(distToStation(h.lineId, p.index, lat, lon), sigma)
                // A fix while "between" stations means we are probably at one of its ends.
                is Position.Between -> 0.3 * max(
                    gauss(distToStation(h.lineId, p.from, lat, lon), sigma),
                    gauss(distToStation(h.lineId, p.to, lat, lon), sigma),
                )
            }
            h.copy(weight = h.weight * (like + 1e-3))
        }
        val reseed = normalize(fresh).map { it.copy(weight = it.weight * config.reseedMass) }
        hypotheses = normalize(merge(normalize(updated) + reseed))
    }

    /** Train started moving. */
    fun onDeparture() {
        val next = hypotheses.flatMap { h ->
            when (val p = h.position) {
                is Position.Between -> listOf(h) // resuming after a tunnel stop
                is Position.At -> {
                    val to = p.index + h.direction
                    if (network.isValidIndex(h.lineId, to)) {
                        listOf(h.copy(position = Position.Between(p.index, to), movingSeconds = 0.0))
                    } else {
                        // Terminus: the train can only go back.
                        val back = p.index - h.direction
                        if (network.isValidIndex(h.lineId, back)) {
                            listOf(h.copy(position = Position.Between(p.index, back), direction = -h.direction, movingSeconds = 0.0))
                        } else emptyList()
                    }
                }
            }
        }
        hypotheses = normalize(merge(next))
    }

    /** Train stopped after [movedSeconds] of movement since the previous departure or tunnel stop. */
    fun onStop(movedSeconds: Double) {
        val next = hypotheses.flatMap { h ->
            when (val p = h.position) {
                is Position.At -> listOf(h)
                is Position.Between -> branchesOnStop(h, p, h.movingSeconds + movedSeconds)
            }
        }
        hypotheses = normalize(merge(next))
    }

    /** User started walking while the train is stopped: could be a transfer. */
    fun onWalking() {
        val next = hypotheses.flatMap { h ->
            val p = h.position as? Position.At ?: return@flatMap listOf(h)
            val stationId = network.line(h.lineId).stations[p.index]
            val transfers = network.transfersAt(stationId)
            val targets = transfers.flatMap { t ->
                network.lines.filter { it.id != h.lineId }.mapNotNull { line ->
                    val j = line.stations.indexOf(t.to)
                    if (j < 0) null else line.id to j
                }
            }
            if (targets.isEmpty()) return@flatMap listOf(h)
            val stay = h.copy(weight = h.weight * (1 - config.transferShare))
            val perTarget = h.weight * config.transferShare / targets.size
            listOf(stay) + targets.flatMap { (lineId, j) ->
                val dirs = directionsFrom(lineId, j)
                dirs.map { d -> Hypothesis(lineId, Position.At(j), d, perTarget / dirs.size) }
            }
        }
        hypotheses = normalize(merge(next))
    }

    fun estimate(): Estimate {
        val atStation = hypotheses.filter { it.position is Position.At }.sumOf { it.weight }
        val current = hypotheses
            .mapNotNull { h -> (h.position as? Position.At)?.let { Triple(network.line(h.lineId).stations[it.index], h.lineId, h.weight) } }
            .groupBy { it.first to it.second }
            .map { (k, v) -> StationBelief(k.first, k.second, v.sumOf { it.third }) }
            .maxByOrNull { it.probability }
        val next = hypotheses
            .mapNotNull { h -> nextIndex(h)?.let { Triple(network.line(h.lineId).stations[it], h.lineId, h.weight) } }
            .groupBy { it.first to it.second }
            .map { (k, v) -> StationBelief(k.first, k.second, v.sumOf { it.third }) }
            .maxByOrNull { it.probability }
        return Estimate(atStation, current, next, hypotheses.size)
    }

    // --- internals -------------------------------------------------------------------------

    private fun branchesOnStop(h: Hypothesis, p: Position.Between, moved: Double): List<Hypothesis> {
        val expected = network.expectedSeconds(h.lineId, p.from, p.to)
        val spread = if (network.isMeasured(h.lineId, p.from, p.to)) config.measuredSpread else config.estimatedSpread
        val out = mutableListOf<Hypothesis>()

        // Log-ratio: arriving 3x faster than expected is impossible, 20% slower is ordinary.
        val arrive = durationLikelihood(moved, expected, spread) + 1e-4
        out += h.copy(position = Position.At(p.to), weight = h.weight * arrive, movingSeconds = 0.0)

        val tunnel = if (moved < 0.85 * expected) config.earlyTunnelStop else config.lateTunnelStop
        out += h.copy(weight = h.weight * tunnel, movingSeconds = moved)

        val beyond = p.to + h.direction
        if (network.isValidIndex(h.lineId, beyond)) {
            val expected2 = expected + network.expectedSeconds(h.lineId, p.to, beyond)
            val skip = config.skipStation * durationLikelihood(moved, expected2, spread)
            if (skip > 1e-6) out += h.copy(position = Position.At(beyond), weight = h.weight * skip, movingSeconds = 0.0)
        }
        return out
    }

    private fun nextIndex(h: Hypothesis): Int? = when (val p = h.position) {
        is Position.Between -> p.to
        is Position.At -> (p.index + h.direction).takeIf { network.isValidIndex(h.lineId, it) }
    }

    private fun seedFromFix(lat: Double, lon: Double, sigma: Double): List<Hypothesis> {
        val out = mutableListOf<Hypothesis>()
        for (line in network.lines) {
            line.stations.forEachIndexed { i, _ ->
                val d = distToStation(line.id, i, lat, lon)
                if (d > config.seedRadiusMeters) return@forEachIndexed
                val w = gauss(d, sigma)
                for (dir in directionsFrom(line.id, i)) out += Hypothesis(line.id, Position.At(i), dir, w)
            }
        }
        return out
    }

    private fun directionsFrom(lineId: String, i: Int): List<Int> =
        listOf(1, -1).filter { network.isValidIndex(lineId, i + it) }

    private fun isTowards(lineId: String, i: Int, direction: Int, targetId: String): Boolean {
        val j = targetIndex(lineId, targetId) ?: return false
        return j != i && (j - i) * direction > 0
    }

    private fun baseIndex(h: Hypothesis): Int = when (val p = h.position) {
        is Position.At -> p.index
        is Position.Between -> p.from
    }

    /** Index of [targetId] on [lineId], or of the transfer station leading to the target's line. */
    private fun targetIndex(lineId: String, targetId: String): Int? {
        val stations = network.line(lineId).stations
        stations.indexOf(targetId).takeIf { it >= 0 }?.let { return it }
        val targetLines = network.lines.filter { targetId in it.stations }.map { it.id }.toSet()
        return stations.indexOfFirst { sid -> network.transferLines(lineId, sid).any { it.id in targetLines } }
            .takeIf { it >= 0 }
    }

    private fun applyDestination(h: Hypothesis): Hypothesis {
        val dest = destination ?: return h
        val j = targetIndex(h.lineId, dest) ?: return h
        val i = baseIndex(h)
        if (j == i) return h
        return if ((j - i) * h.direction > 0) h else h.copy(weight = h.weight * config.wrongWayToDestination)
    }

    private fun distToStation(lineId: String, index: Int, lat: Double, lon: Double): Double {
        val s = network.stationAt(lineId, index)
        return distanceMeters(s.lat, s.lon, lat, lon)
    }

    private fun merge(list: List<Hypothesis>): List<Hypothesis> =
        list.groupBy { Triple(it.lineId, it.position, it.direction) }
            .map { (_, group) ->
                val w = group.sumOf { it.weight }
                val moving = if (w > 0) group.sumOf { it.movingSeconds * it.weight } / w else 0.0
                group.first().copy(weight = w, movingSeconds = moving)
            }

    private fun normalize(list: List<Hypothesis>): List<Hypothesis> {
        val total = list.sumOf { it.weight }
        if (total <= 0.0) return emptyList()
        return list.map { it.copy(weight = it.weight / total) }
            .filter { it.weight >= config.pruneBelow }
            .sortedByDescending { it.weight }
            .take(config.maxHypotheses)
            .let { kept ->
                val t = kept.sumOf { it.weight }
                kept.map { it.copy(weight = it.weight / t) }
            }
    }

    private fun durationLikelihood(actual: Double, expected: Double, spread: Double): Double =
        if (actual <= 0.0) 0.0 else gauss(ln(actual / expected), spread)

    private fun gauss(x: Double, sigma: Double): Double = exp(-0.5 * (x / sigma) * (x / sigma))
}
