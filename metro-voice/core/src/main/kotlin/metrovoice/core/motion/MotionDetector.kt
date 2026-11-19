package metrovoice.core.motion

import kotlin.math.sqrt

enum class Phase { UNKNOWN, STOPPED, MOVING, WALKING }

data class PhaseChange(val phase: Phase, val previous: Phase, val atSeconds: Double)

/**
 * Classifies "train stopped / train moving / person walking" from raw accelerometer samples.
 *
 * Approach (after SubwayPS): remove gravity with a low-pass filter, then look at the variance of the
 * linear-acceleration magnitude over a sliding window. A moving train vibrates; a stopped one does not;
 * walking produces much larger periodic peaks. Hysteresis + minimum durations suppress flicker.
 *
 * Thresholds are starting points and must be tuned on recorded rides.
 */
class MotionDetector(private val config: Config = Config()) {

    data class Config(
        val windowSeconds: Double = 2.0,
        val gravityAlpha: Double = 0.9,
        // First recorded ride (phone in a pocket): riding 0.025–0.3, train standing 0.0007,
        // standing at a platform with people moving 0.008–0.02. One ride — revisit with more data.
        /** Variance (m²/s⁴) above which the train is considered moving. */
        val moveVariance: Double = 0.02,
        /** Variance below which the train is considered stopped. */
        val stopVariance: Double = 0.006,
        /** Variance above which the user is considered walking. */
        val walkVariance: Double = 1.0,
        val minMoveSeconds: Double = 4.0,
        val minStopSeconds: Double = 5.0,
        val minWalkSeconds: Double = 6.0,
    )

    var phase: Phase = Phase.UNKNOWN
        private set

    /** Latest windowed variance, exposed for logging and threshold tuning. */
    var lastVariance: Double = 0.0
        private set

    private var gx = 0.0
    private var gy = 0.0
    private var gz = 0.0
    private var gravityInitialized = false

    private val window = ArrayDeque<Pair<Double, Double>>()
    private var sum = 0.0
    private var sumSq = 0.0

    private var candidate: Phase? = null
    private var candidateSince = 0.0

    /** Feeds one sample. Returns a [PhaseChange] when the phase switches. */
    fun onSample(tSeconds: Double, x: Double, y: Double, z: Double): PhaseChange? {
        if (!gravityInitialized) {
            gx = x; gy = y; gz = z; gravityInitialized = true
        } else {
            val a = config.gravityAlpha
            gx = a * gx + (1 - a) * x
            gy = a * gy + (1 - a) * y
            gz = a * gz + (1 - a) * z
        }
        val lx = x - gx
        val ly = y - gy
        val lz = z - gz
        val m = sqrt(lx * lx + ly * ly + lz * lz)

        window.addLast(tSeconds to m)
        sum += m
        sumSq += m * m
        while (window.isNotEmpty() && tSeconds - window.first().first > config.windowSeconds) {
            val (_, old) = window.removeFirst()
            sum -= old
            sumSq -= old * old
        }
        val span = tSeconds - window.first().first
        if (span < config.windowSeconds * 0.9) return null

        val n = window.size
        val mean = sum / n
        val variance = (sumSq / n - mean * mean).coerceAtLeast(0.0)
        lastVariance = variance

        val observed = classify(variance)
        return updateState(observed, tSeconds)
    }

    private fun classify(variance: Double): Phase? = when {
        variance >= config.walkVariance -> Phase.WALKING
        variance >= config.moveVariance -> Phase.MOVING
        variance <= config.stopVariance -> Phase.STOPPED
        else -> null // hysteresis band: keep whatever we are doing
    }

    private fun updateState(observed: Phase?, t: Double): PhaseChange? {
        if (observed == null || observed == phase) {
            candidate = null
            return null
        }
        if (candidate != observed) {
            candidate = observed
            candidateSince = t
            return null
        }
        val needed = when (observed) {
            Phase.MOVING -> config.minMoveSeconds
            Phase.STOPPED -> config.minStopSeconds
            Phase.WALKING -> config.minWalkSeconds
            Phase.UNKNOWN -> Double.MAX_VALUE
        }
        if (t - candidateSince < needed) return null
        val change = PhaseChange(observed, phase, candidateSince)
        phase = observed
        candidate = null
        return change
    }
}
