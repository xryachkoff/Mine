package metrovoice.core.engine

import metrovoice.core.announce.Announcement
import metrovoice.core.announce.Announcer
import metrovoice.core.graph.MetroNetwork
import metrovoice.core.motion.MotionDetector
import metrovoice.core.motion.Phase
import metrovoice.core.motion.PhaseChange
import metrovoice.core.tracking.Estimate
import metrovoice.core.tracking.Tracker

/**
 * Glue between sensors and output. The Android service forwards raw sensor callbacks here;
 * the same class replays recorded traces in tests and on a laptop.
 */
class TripEngine(
    network: MetroNetwork,
    languages: List<String> = listOf("ru", "en"),
    detectorConfig: MotionDetector.Config = MotionDetector.Config(),
    trackerConfig: Tracker.Config = Tracker.Config(),
    private val listener: Listener,
) {
    interface Listener {
        fun onAnnouncements(announcements: List<Announcement>) {}
        fun onEstimate(estimate: Estimate, phase: Phase) {}
        fun onPhase(change: PhaseChange) {}
    }

    val detector = MotionDetector(detectorConfig)
    val tracker = Tracker(network, trackerConfig)
    private val announcer = Announcer(network, languages)

    private var movingSince: Double? = null
    private var movedInSegment = 0.0

    private var lastApproachCheck = 0.0

    fun onAccelerometer(tSeconds: Double, x: Double, y: Double, z: Double) {
        val change = detector.onSample(tSeconds, x, y, z)
        if (change != null) {
            listener.onPhase(change)
            handlePhase(change)
        }
        // Once a second while riding: are we close enough to the next station to say so?
        val since = movingSince
        if (since != null && detector.phase == Phase.MOVING && tSeconds - lastApproachCheck >= 1.0) {
            lastApproachCheck = tSeconds
            emit(announcer.onApproach(tracker.approach(movingForSeconds = tSeconds - since, atSeconds = tSeconds)))
        }
    }

    fun onLocation(tSeconds: Double, lat: Double, lon: Double, accuracyMeters: Double) {
        // Never gated by the motion phase: a detector stuck in MOVING must not blind the tracker.
        val wasOutside = tracker.outside
        tracker.onFix(lat, lon, accuracyMeters, tSeconds)
        // Out of the metro: the next ride starts from a clean slate (the same station may be announced again).
        if (tracker.outside && !wasOutside) announcer.reset()
        publishStanding()
    }

    fun setStation(stationId: String, towardsStationId: String? = null) {
        tracker.setStation(stationId, towardsStationId)
        publishStanding() // re-announces only if the station differs from what was last said
    }

    /** "Going home / to work": destination may be on another line. Null clears it. */
    fun setDestination(stationId: String?) {
        tracker.setDestination(stationId)
        publishStanding()
    }

    fun setDirectionTowards(stationId: String) {
        tracker.setDirectionTowards(stationId)
        publishStanding()
    }

    fun reset() {
        tracker.reset()
        announcer.reset()
        movingSince = null
        movedInSegment = 0.0
    }

    private fun handlePhase(change: PhaseChange) {
        val t = change.atSeconds
        when (change.phase) {
            Phase.MOVING -> {
                // A departure from a station resets the segment clock; resuming after a tunnel stop does not.
                if (change.previous == Phase.STOPPED || change.previous == Phase.WALKING) {
                    movingSince = t
                    tracker.onDeparture(t)
                    emit(announcer.onDeparture(tracker.estimate()))
                }
            }
            Phase.STOPPED -> {
                val since = movingSince
                if (change.previous == Phase.MOVING && since != null) {
                    tracker.onStop(movedSeconds = t - since, atSeconds = t)
                    movingSince = null
                    publishStanding()
                }
            }
            Phase.WALKING -> tracker.onWalking()
            Phase.UNKNOWN -> Unit
        }
        listener.onEstimate(tracker.estimate(), detector.phase)
    }

    private fun publishStanding() {
        val estimate = tracker.estimate()
        emit(announcer.onStandingUpdate(estimate))
        listener.onEstimate(estimate, detector.phase)
    }

    private fun emit(list: List<Announcement>) {
        if (list.isNotEmpty()) listener.onAnnouncements(list)
    }
}
