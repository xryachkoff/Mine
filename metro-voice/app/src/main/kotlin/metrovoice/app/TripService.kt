package metrovoice.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import metrovoice.core.announce.Announcement
import metrovoice.core.engine.TripEngine
import metrovoice.core.graph.MetroNetwork
import metrovoice.core.motion.Phase
import metrovoice.core.motion.PhaseChange
import metrovoice.core.tracking.Estimate
import metrovoice.core.trace.TraceRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that owns a trip: sensors → [TripEngine] → voice + notification,
 * while recording every signal to a JSONL trace for later tuning.
 * All engine and trace work runs on one handler thread.
 */
class TripService : LifecycleService() {

    companion object {
        const val ACTION_START = "metrovoice.START"
        const val ACTION_STOP = "metrovoice.STOP"
        private const val CHANNEL = "trip"
        private const val NOTIFICATION_ID = 1
        private const val CELL_PERIOD_MS = 10_000L
        private const val WIFI_PERIOD_MS = 30_000L
        private const val MAX_FIX_AGE_NS = 20_000_000_000L

        fun start(context: Context) = ContextCompat.startForegroundService(
            context, Intent(context, TripService::class.java).setAction(ACTION_START),
        )

        fun stop(context: Context) = context.startService(Intent(context, TripService::class.java).setAction(ACTION_STOP))
    }

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private var network: MetroNetwork? = null
    private var engine: TripEngine? = null
    private var trace: TraceWriter? = null
    private var speaker: Speaker? = null
    private var startNs = 0L
    private var lastNotificationText = ""

    private val sensors by lazy { getSystemService(SensorManager::class.java) }
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val telephony by lazy { getSystemService(TelephonyManager::class.java) }
    private val wifi by lazy { applicationContext.getSystemService(WifiManager::class.java) }

    /** 4 decimals: below sensor noise, keeps the trip file small. */
    private fun r4(x: Float): Double = Math.round(x * 10_000.0) / 10_000.0
    private fun r4(x: Double): Double = Math.round(x * 10_000.0) / 10_000.0

    private fun now(): Double = (SystemClock.elapsedRealtimeNanos() - startNs) / 1e9
    private fun tOf(timestampNs: Long): Double = (timestampNs - startNs) / 1e9

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            else -> if (engine == null) startTrip()
        }
        return START_NOT_STICKY
    }

    private fun startTrip() {
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification("Поездка начата", "Жду станцию…"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        val net = CityRepository.load(this) ?: run { stopSelf(); return }
        network = net

        thread = HandlerThread("trip").apply { start() }
        handler = Handler(thread.looper)
        startNs = SystemClock.elapsedRealtimeNanos()

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(CityRepository.tripsDir(this), "trip-$stamp.jsonl.gz")
        trace = TraceWriter(file)
        speaker = Speaker(this) { lang -> TripBus.update { it.copy(missingVoices = it.missingVoices + lang) } }

        handler.post {
            trace?.write(TraceRecord.Meta(0.0, net.graph.city, "${Build.MANUFACTURER} ${Build.MODEL} API ${Build.VERSION.SDK_INT}", "0.2.1"))
            engine = TripEngine(net, listOf("ru", "en"), listener = EngineListener()).also { e ->
                TripBus.state.value.destination?.let(e::setDestination)
            }
        }

        registerSensors()
        requestLocation()
        handler.postDelayed(cellTick, 1_000)
        registerWifi()

        lifecycleScope.launch {
            TripBus.commands.collect { cmd ->
                handler.post {
                    when (cmd) {
                        is Command.AtStation -> {
                            trace?.write(TraceRecord.Label(now(), cmd.stationId))
                            engine?.setStation(cmd.stationId)
                        }
                        is Command.Destination -> engine?.setDestination(cmd.stationId)
                    }
                }
            }
        }
        TripBus.update { UiState(running = true, tripFile = file.absolutePath, destination = it.destination) }
    }

    override fun onDestroy() {
        if (::handler.isInitialized) {
            sensors.unregisterListener(sensorListener)
            fused.removeLocationUpdates(locationCallback)
            handler.removeCallbacksAndMessages(null)
            runCatching { unregisterReceiver(wifiReceiver) }
            handler.post { trace?.close() }
            thread.quitSafely()
        }
        speaker?.shutdown()
        engine = null
        TripBus.update { it.copy(running = false, phase = "—") }
        super.onDestroy()
    }

    // --- engine output -----------------------------------------------------------------

    private inner class EngineListener : TripEngine.Listener {
        override fun onAnnouncements(announcements: List<Announcement>) {
            val t = now()
            announcements.forEach { trace?.write(TraceRecord.Say(t, it.key, it.text)) }
            speaker?.speak(announcements)
            TripBus.update { it.copy(lastSaid = announcements.joinToString(" ") { a -> a.text }) }
        }

        override fun onEstimate(estimate: Estimate, phase: Phase) {
            TripBus.update { it.copy(estimate = estimate, phase = phase.name) }
            updateNotification(estimate)
        }

        override fun onPhase(change: PhaseChange) {
            trace?.write(TraceRecord.Phase(change.atSeconds, change.phase.name, engine?.detector?.lastVariance ?: 0.0))
        }
    }

    private fun updateNotification(e: Estimate) {
        val net = network ?: return
        fun name(id: String?) = id?.let { net.station(it).name["ru"] ?: net.station(it).name["en"] } ?: "?"
        val title = e.current?.takeIf { e.atStationProbability > 0.5 }
            ?.let { "Станция ${name(it.stationId)} · ${(it.probability * 100).toInt()}%" } ?: "В пути"
        val text = e.next?.let { "Следующая: ${name(it.stationId)} · ${(it.probability * 100).toInt()}%" } ?: "Направление неизвестно"
        if (title + text == lastNotificationText) return
        lastNotificationText = title + text
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title, text))
    }

    // --- sensors ---------------------------------------------------------------------------

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val t = r4(tOf(e.timestamp))
            val v = e.values
            when (e.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    val (x, y, z) = Triple(r4(v[0]), r4(v[1]), r4(v[2]))
                    trace?.write(TraceRecord.Acc(t, x, y, z))
                    engine?.onAccelerometer(t, x, y, z)
                }
                Sensor.TYPE_GYROSCOPE -> trace?.write(TraceRecord.Gyro(t, r4(v[0]), r4(v[1]), r4(v[2])))
                Sensor.TYPE_MAGNETIC_FIELD -> trace?.write(TraceRecord.Mag(t, r4(v[0]), r4(v[1]), r4(v[2])))
                Sensor.TYPE_PRESSURE -> trace?.write(TraceRecord.Baro(t, r4(v[0])))
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun registerSensors() {
        val periods = mapOf(
            Sensor.TYPE_ACCELEROMETER to 20_000,
            Sensor.TYPE_GYROSCOPE to 20_000,
            Sensor.TYPE_MAGNETIC_FIELD to 20_000,
            Sensor.TYPE_PRESSURE to 200_000,
        )
        for ((type, us) in periods) {
            sensors.getDefaultSensor(type)?.let { sensors.registerListener(sensorListener, it, us, handler) }
        }
    }

    // --- location (network fixes at stations) ----------------------------------------------

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) {
                if (SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos > MAX_FIX_AGE_NS) continue
                val t = tOf(loc.elapsedRealtimeNanos)
                trace?.write(TraceRecord.Loc(t, loc.latitude, loc.longitude, loc.accuracy.toDouble(), loc.provider))
                engine?.onLocation(loc.latitude, loc.longitude, loc.accuracy.toDouble())
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocation() {
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5_000)
            .setMinUpdateIntervalMillis(2_000)
            .build()
        runCatching { fused.requestLocationUpdates(request, locationCallback, thread.looper) }
    }

    // --- cell & wifi (logged only, future offline anchors) -----------------------------------

    private val cellTick = object : Runnable {
        @SuppressLint("MissingPermission")
        override fun run() {
            runCatching { telephony.allCellInfo }.getOrNull()?.let { infos ->
                trace?.write(TraceRecord.Cell(now(), infos.mapNotNull(::cellObs)))
            }
            handler.postDelayed(this, CELL_PERIOD_MS)
        }
    }

    private fun cellObs(c: CellInfo): TraceRecord.CellObs? {
        val p = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P // mcc/mnc strings exist from API 28
        return when (c) {
            is CellInfoLte -> c.cellIdentity.let { id ->
                TraceRecord.CellObs("lte", id.ci.toLong(), id.tac, if (p) id.mccString else null, if (p) id.mncString else null, c.cellSignalStrength.dbm, c.isRegistered)
            }
            is CellInfoGsm -> c.cellIdentity.let { id ->
                TraceRecord.CellObs("gsm", id.cid.toLong(), id.lac, if (p) id.mccString else null, if (p) id.mncString else null, c.cellSignalStrength.dbm, c.isRegistered)
            }
            is CellInfoWcdma -> c.cellIdentity.let { id ->
                TraceRecord.CellObs("wcdma", id.cid.toLong(), id.lac, if (p) id.mccString else null, if (p) id.mncString else null, c.cellSignalStrength.dbm, c.isRegistered)
            }
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && c is android.telephony.CellInfoNr) {
                val id = c.cellIdentity as CellIdentityNr
                TraceRecord.CellObs("nr", id.nci, id.tac, id.mccString, id.mncString, c.cellSignalStrength.dbm, c.isRegistered)
            } else null
        }
    }

    private val wifiReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val results = runCatching { wifi.scanResults }.getOrNull() ?: return
            val aps = results.map { TraceRecord.WifiObs(it.BSSID, it.level) }
            handler.post { trace?.write(TraceRecord.Wifi(now(), aps)) }
        }
    }

    private val wifiTick = object : Runnable {
        override fun run() {
            @Suppress("DEPRECATION") runCatching { wifi.startScan() } // throttled by the OS; best effort
            handler.postDelayed(this, WIFI_PERIOD_MS)
        }
    }

    private fun registerWifi() {
        ContextCompat.registerReceiver(
            this, wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        handler.post(wifiTick)
    }

    // --- notification -----------------------------------------------------------------------

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Поездка", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(title: String, text: String) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle(title)
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
        )
        .addAction(
            0, "Стоп",
            PendingIntent.getService(this, 1, Intent(this, TripService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE),
        )
        .build()
}
