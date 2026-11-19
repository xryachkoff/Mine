package metrovoice.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import metrovoice.core.announce.Announcement
import metrovoice.core.engine.TripEngine
import metrovoice.core.graph.MetroNetwork
import metrovoice.core.motion.Phase
import metrovoice.core.sim.Leg
import metrovoice.core.sim.RideSimulator
import metrovoice.core.sim.SimOptions
import metrovoice.core.tracking.Estimate
import metrovoice.core.trace.TraceRecord

/**
 * Plays a simulated ride through the real engine, voice and UI, sped up.
 * No sensors, no permissions, nothing recorded: a way to see and hear the app without the metro.
 */
object DemoRunner {
    private const val SPEED = 6.0
    private var job: Job? = null

    fun start(context: Context, network: MetroNetwork) {
        stop()
        val app = context.applicationContext
        job = CoroutineScope(Dispatchers.Default).launch {
            val speaker = Speaker(app) { lang -> TripBus.update { it.copy(missingVoices = it.missingVoices + lang) } }
            try {
                val ride = RideSimulator.generate(network, demoRoute(network), SimOptions(seed = 7))
                val engine = TripEngine(network, listOf("ru", "en"), listener = object : TripEngine.Listener {
                    override fun onAnnouncements(announcements: List<Announcement>) {
                        val settings = ProfileStore.profile.value
                        if (!settings.muted) speaker.speak(announcements.filter { it.language in settings.languages })
                    }
                    override fun onEstimate(estimate: Estimate, phase: Phase) =
                        TripBus.update { it.copy(estimate = estimate, phase = phase.name) }
                })
                TripBus.update { UiState(running = true, demo = true) }
                val startMs = System.currentTimeMillis()
                var lastPaced = 0.0
                for (r in ride.records) {
                    if (r.t - lastPaced >= 0.5) {
                        lastPaced = r.t
                        val wait = startMs + (r.t / SPEED * 1000).toLong() - System.currentTimeMillis()
                        if (wait > 0) delay(wait)
                    }
                    when (r) {
                        is TraceRecord.Acc -> engine.onAccelerometer(r.t, r.x, r.y, r.z)
                        is TraceRecord.Loc -> engine.onLocation(r.t, r.lat, r.lon, r.acc)
                        else -> Unit
                    }
                }
                delay(6_000) // let the last phrase finish
            } finally {
                speaker.shutdown()
                TripBus.update { UiState(destination = it.destination) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** First two lines joined at a transfer when the city has one; otherwise a few stations of line one. */
    private fun demoRoute(network: MetroNetwork): List<Leg> {
        val a = network.lines.first()
        val hub = a.stations.indexOfFirst { network.transferLines(a.id, it).isNotEmpty() }
        if (hub < 2) return listOf(Leg(a.id, a.stations.first(), a.stations[minOf(3, a.stations.lastIndex)]))
        val b = network.transferLines(a.id, a.stations[hub]).first()
        val hubOnB = network.transfersAt(a.stations[hub]).firstNotNullOf { t -> b.stations.indexOf(t.to).takeIf { it >= 0 } }
        val endOnB = if (hubOnB >= 2) hubOnB - 2 else minOf(hubOnB + 2, b.stations.lastIndex)
        return listOf(Leg(a.id, a.stations[hub - 2], a.stations[hub]), Leg(b.id, b.stations[hubOnB], b.stations[endOnB]))
    }
}
