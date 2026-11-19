package metrovoice.core.tracking

import metrovoice.core.graph.MetroNetwork
import metrovoice.core.graph.distanceMeters
import metrovoice.core.graph.distanceToSegmentMeters
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
    /** The rider has left the metro (accurate fixes away from every station): nothing is tracked. */
    val outside: Boolean = false,
    /** Direction of travel, named the way metros name it: the terminus the train is heading to. */
    val towards: StationBelief? = null,
)

/**
 * Probabilistic tracker over the metro graph (forward filter of a hidden Markov model).
 *
 * Hidden state: (line, station-or-segment, direction). Observations: network/GPS fixes (primary anchor,
 * valid in any motion phase), departure and stop events from [metrovoice.core.motion.MotionDetector], walking (possible transfer),
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
        val reseedMass: Double = 0.03,
        /**
         * Prior that the train moved one station without the motion detector noticing.
         * Deliberately high: on a real phone (in the hand, in a bag) the detector is the weak link,
         * while a fix at the neighbouring station is strong evidence.
         */
        val missedMove: Double = 0.5,
        /** Prior that a fix near the end of the current segment means "arrived", before a stop is detected. */
        val arriveOnFix: Double = 0.9,
        /** How many stations a fix may jump ahead of the believed position. */
        val maxMissedStations: Int = 3,
        /** Prior that the phone gets a fix inside a tunnel at all, relative to at a station. */
        val tunnelFixPrior: Double = 0.5,
        /** Probability that a fix is garbage (cell handover, stale cache): keeps one outlier from wiping beliefs. */
        val fixOutlier: Double = 1e-3,
        /** Direction prior floor when the direction is inferred from the previous position. */
        val directionFloor: Double = 0.05,
        /**
         * Fixes taken at a station land within this distance of its node (first real ride: 28–78 m);
         * farther than this plus twice the accuracy is "not at a station" (same ride, on the street: 223–282 m).
         */
        val stationZoneMeters: Double = 150.0,
        /** Accuracy needed for a fix to bring the rider back into the metro after walking out. */
        val reentryAccuracyMeters: Double = 100.0,
        /** Slow fixes away from all stations for this long mean the rider walked out of the metro. */
        val leaveAfterSeconds: Double = 90.0,
        /** Above this speed the rider is still on a train (a fix inside a tunnel), not on foot. */
        val walkingSpeedMps: Double = 4.0,
        /** A fix this recent can veto an "arrived at station" conclusion drawn from motion alone. */
        val fixVetoSeconds: Double = 10.0,
        /** Fixes this close to the previous one, this soon after it, are the same evidence repeated. */
        val sameFixMeters: Double = 30.0,
        val sameFixSeconds: Double = 30.0,
        val pruneBelow: Double = 1e-4,
        val maxHypotheses: Int = 300,
    )

    private var hypotheses: List<Hypothesis> = emptyList()
    private var lastFix: Triple<Double, Double, Double>? = null

    private class FixObs(val lat: Double, val lon: Double, val sigma: Double, val t: Double?)
    /** Last fix that updated the beliefs / last fix received at all. */
    private var processedFix: FixObs? = null
    private var recentFix: FixObs? = null
    /** First of the current run of fixes away from every station. */
    private var farSince: FixObs? = null

    /** True after the rider walked out of the metro; cleared by a fix at a station or a tap. */
    var outside: Boolean = false
        private set

    /** Station the user is heading to ("home", "work"), possibly on another line. */
    var destination: String? = null
        private set

    val isTracking: Boolean get() = hypotheses.isNotEmpty()

    fun hypotheses(): List<Hypothesis> = hypotheses

    fun reset() {
        hypotheses = emptyList()
        destination = null
        lastFix = null
        processedFix = null
        recentFix = null
        farSince = null
        outside = false
    }

    /**
     * Destination known (e.g. "going home"): hypotheses heading away from it are strongly down-weighted.
     * Soft, not a hard filter, so a wrong destination cannot make the tracker blind.
     */
    fun setDestination(stationId: String?) {
        destination = stationId
        if (stationId != null && hypotheses.isNotEmpty()) hypotheses = normalize(hypotheses.map(::applyDestination))
    }

    /**
     * User says "I am at station X" (tap on the line strip).
     * Direction is inferred from where we believed the train was before: tapping the next station
     * along the line tells the direction; tapping the same station keeps it.
     */
    fun setStation(stationId: String, towardsStationId: String? = null) {
        val seeded = mutableListOf<Hypothesis>()
        for (line in network.lines) {
            val i = line.stations.indexOf(stationId)
            if (i < 0) continue
            val prior = directionPrior(line.id, i)
            for (d in directionsFrom(line.id, i)) {
                if (towardsStationId != null && !isTowards(line.id, i, d, towardsStationId)) continue
                seeded += Hypothesis(line.id, Position.At(i), d, prior.getValue(d))
            }
        }
        hypotheses = normalize(seeded.map(::applyDestination))
        // The tap is the truth for now: forget "left the metro" and let the next fix speak again.
        outside = false
        farSince = null
        processedFix = null
    }

    /** User says "I am going towards X" (destination or terminus). Keeps only consistent hypotheses. */
    fun setDirectionTowards(stationId: String) {
        val kept = hypotheses.filter { h ->
            isTowards(h.lineId, baseIndex(h), h.direction, stationId)
        }
        if (kept.isNotEmpty()) hypotheses = normalize(kept)
    }

    /**
     * A location fix (network or GNSS). Processed in any motion phase: fixes are the primary anchor and
     * must work even when the motion detector is wrong. A fix at the next station along the line both
     * moves the belief there and tells the direction.
     */
    fun onFix(lat: Double, lon: Double, accuracyMeters: Double, atSeconds: Double? = null) {
        val sigma = max(accuracyMeters, config.minFixSigmaMeters)
        val obs = FixObs(lat, lon, sigma, atSeconds)
        recentFix = obs
        if (detectLeaving(obs, accuracyMeters)) return

        // The provider repeats positions (cached, or the rider simply stands still). Counting the same
        // evidence again and again turns a small bias into false certainty — seen on a real ride.
        val key = Triple(lat, lon, accuracyMeters)
        if (key == lastFix) return
        lastFix = key
        val last = processedFix
        if (last != null && hypotheses.isNotEmpty() && distanceMeters(last.lat, last.lon, lat, lon) < config.sameFixMeters) {
            val dt = if (atSeconds != null && last.t != null) atSeconds - last.t else 0.0
            if (dt < config.sameFixSeconds) return
        }
        processedFix = obs

        if (hypotheses.isEmpty()) {
            // Start only at a station, not when merely walking towards one. At the very start a coarse fix
            // may be all there is; after walking out, re-entering takes an accurate fix at a station —
            // on the first real ride a 47 m fix from the street 260 m away would otherwise have counted.
            val zone = when {
                !outside -> max(config.stationZoneMeters, minOf(2 * accuracyMeters, 600.0))
                accuracyMeters <= config.reentryAccuracyMeters -> config.stationZoneMeters
                else -> return
            }
            hypotheses = normalize(seedFromFix(lat, lon, sigma, zone).map(::applyDestination))
            if (hypotheses.isNotEmpty()) outside = false
            return
        }
        val fresh = seedFromFix(lat, lon, sigma).map(::applyDestination)
        val updated = hypotheses.flatMap { fixBranches(it, lat, lon, sigma) }
        val reseed = fresh.map { it.copy(weight = it.weight * config.reseedMass) }
        hypotheses = normalize(merge(updated + reseed))
    }

    /**
     * Slow, accurate fixes away from every station for a while: the rider walked out. Tracking stops,
     * so that street noise in the motion sensors cannot be mistaken for the next station.
     */
    private fun detectLeaving(obs: FixObs, accuracyMeters: Double): Boolean {
        val nearest = network.lines.minOf { line -> line.stations.indices.minOf { distToStation(line.id, it, obs.lat, obs.lon) } }
        if (nearest <= config.stationZoneMeters + 2 * accuracyMeters) {
            farSince = null
            return false
        }
        if (outside) return true
        val start = farSince ?: obs.also { farSince = it }
        val dt = if (obs.t != null && start.t != null) obs.t - start.t else return false
        if (dt < config.leaveAfterSeconds) return false
        if (distanceMeters(start.lat, start.lon, obs.lat, obs.lon) / dt > config.walkingSpeedMps) {
            farSince = obs // moving fast: on a train with signal in the tunnel; restart the clock
            return false
        }
        hypotheses = emptyList()
        processedFix = null
        outside = true
        return true
    }

    private fun fixBranches(h: Hypothesis, lat: Double, lon: Double, sigma: Double): List<Hypothesis> {
        val out = mutableListOf<Hypothesis>()
        fun jumpsFrom(startIndex: Int, direction: Int, firstPrior: Double) {
            var prior = firstPrior
            for (n in 1..config.maxMissedStations) {
                val idx = startIndex + n * direction
                if (!network.isValidIndex(h.lineId, idx)) break
                val like = gauss(distToStation(h.lineId, idx, lat, lon), sigma)
                if (like > 1e-6) out += h.copy(position = Position.At(idx), direction = direction, weight = h.weight * prior * like, movingSeconds = 0.0)
                prior *= config.missedMove
            }
        }
        when (val p = h.position) {
            is Position.At -> {
                out += h.copy(weight = h.weight * (gauss(distToStation(h.lineId, p.index, lat, lon), sigma) + config.fixOutlier))
                // Moved on without a detected departure. At a terminus the only way is back.
                val d = if (network.isValidIndex(h.lineId, p.index + h.direction)) h.direction else -h.direction
                jumpsFrom(p.index, d, config.missedMove)
            }
            is Position.Between -> {
                val a = network.stationAt(h.lineId, p.from)
                val b = network.stationAt(h.lineId, p.to)
                val dStart = distanceMeters(a.lat, a.lon, lat, lon)
                val dEnd = distanceMeters(b.lat, b.lon, lat, lon)
                val atEnd = gauss(dEnd, sigma)
                val onSegment = gauss(distanceToSegmentMeters(lat, lon, a.lat, a.lon, b.lat, b.lon), sigma)
                // Around the origin station a fix must not favour one direction: which side of the station
                // node the entrance or the Wi-Fi sits on says nothing about where the train is heading.
                val nearStart = gauss(dStart, 2 * sigma)
                // Deeper in the tunnel a fix is spread along the whole segment rather than concentrated at one
                // point, and tunnels often have no signal at all: "in this tunnel" explains a fix worse than
                // "at the station it points to" — e.g. when another line's station lies next to the tunnel.
                val tunnel = minOf(1.0, 2.5 * sigma / distanceMeters(a.lat, a.lon, b.lat, b.lon)) * config.tunnelFixPrior
                val along = nearStart * gauss(dStart, sigma) + (1 - nearStart) * onSegment * tunnel
                // "Still in the tunnel" only if the fix is clearly short of the end station (2 sigma zone around it).
                out += h.copy(weight = h.weight * (along * (1 - gauss(dEnd, 2 * sigma)) + config.fixOutlier))
                if (atEnd > 1e-6) out += h.copy(position = Position.At(p.to), weight = h.weight * config.arriveOnFix * atEnd, movingSeconds = 0.0)
                jumpsFrom(p.to, h.direction, config.arriveOnFix * config.missedMove)
            }
        }
        return out
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
    fun onStop(movedSeconds: Double, atSeconds: Double? = null) {
        // A fix from the last few seconds knows where we are: an "arrived at X" guess from motion alone
        // must agree with it. (Older fixes do not count — the provider replays stale positions in tunnels.)
        val veto = recentFix?.takeIf { f -> atSeconds != null && f.t != null && atSeconds - f.t in 0.0..config.fixVetoSeconds }
        val next = hypotheses.flatMap { h ->
            when (val p = h.position) {
                is Position.At -> listOf(h)
                is Position.Between -> branchesOnStop(h, p, h.movingSeconds + movedSeconds).map { branch ->
                    val at = branch.position as? Position.At
                    if (veto == null || at == null) branch
                    else branch.copy(weight = branch.weight * (gauss(distToStation(h.lineId, at.index, veto.lat, veto.lon), veto.sigma) + config.fixOutlier))
                }
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
        val towards = hypotheses
            .map { h ->
                val stations = network.line(h.lineId).stations
                // Standing at a terminus facing outwards: the only way is back.
                val p = h.position
                val dir = if (p is Position.At && !network.isValidIndex(h.lineId, p.index + h.direction)) -h.direction else h.direction
                Triple(if (dir > 0) stations.last() else stations.first(), h.lineId, h.weight)
            }
            .groupBy { it.first to it.second }
            .map { (k, v) -> StationBelief(k.first, k.second, v.sumOf { it.third }) }
            .maxByOrNull { it.probability }
        return Estimate(atStation, current, next, hypotheses.size, outside, towards)
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

    private fun seedFromFix(lat: Double, lon: Double, sigma: Double, maxDistance: Double = Double.MAX_VALUE): List<Hypothesis> {
        val gate = minOf(config.seedRadiusMeters, max(2.5 * sigma, 400.0), maxDistance)
        val out = mutableListOf<Hypothesis>()
        for (line in network.lines) {
            line.stations.forEachIndexed { i, _ ->
                val d = distToStation(line.id, i, lat, lon)
                if (d > gate) return@forEachIndexed
                val w = gauss(d, sigma)
                val prior = directionPrior(line.id, i)
                for (dir in directionsFrom(line.id, i)) out += Hypothesis(line.id, Position.At(i), dir, w * prior.getValue(dir))
            }
        }
        return out
    }

    /**
     * Direction prior for "now at index [k] of [lineId]", from the current beliefs: coming from a lower
     * index means heading up, and vice versa; the same station keeps its direction. Beliefs on another
     * line count through the transfer station. Uniform when nothing is known.
     */
    private fun directionPrior(lineId: String, k: Int): Map<Int, Double> {
        val dirs = directionsFrom(lineId, k)
        val score = dirs.associateWith { 0.0 }.toMutableMap()
        val stations = network.line(lineId).stations
        for (h in hypotheses) {
            val sameLine = h.lineId == lineId
            val anchors = if (sameLine) listOf(baseIndex(h)) else {
                val sid = network.line(h.lineId).stations[baseIndex(h)]
                network.transfersAt(sid).mapNotNull { t -> stations.indexOf(t.to).takeIf { it >= 0 } }
            }
            for (i in anchors) {
                val d = when {
                    k > i -> 1
                    k < i -> -1
                    sameLine -> h.direction
                    else -> continue
                }
                if (d in score) score[d] = score.getValue(d) + h.weight
            }
        }
        val total = score.values.sum()
        if (total <= 0.0) return dirs.associateWith { 1.0 / dirs.size }
        return dirs.associateWith { d -> score.getValue(d) / total * (1 - config.directionFloor) + config.directionFloor / dirs.size }
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
