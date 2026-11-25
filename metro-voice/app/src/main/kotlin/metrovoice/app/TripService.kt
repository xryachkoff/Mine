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
import metrovoice.core.announce.AnnouncementKind
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
        const val ACTION_TOGGLE_MUTE = "metrovoice.TOGGLE_MUTE"
        private const val CHANNEL = "trip"
        private const val NOTIFICATION_ID = 1
        private const val CELL_PERIOD_MS = 10_000L
        private const val WIFI_PERIOD_MS = 30_000L
        private const val MAX_FIX_AGE_NS = 20_000_000_000L

        const val EXTRA_AUTO = "auto"
        const val EXTRA_STATION = "station"
        private const val FINISHED_NOTIFICATION_ID = 2
        private const val WATCHDOG_PERIOD_MS = 30_000L
        /** Out of the metro for this long: the trip ends by itself. Long enough to change entrances on foot. */
        private const val LEAVE_GRACE_MS = 2 * 60_000L
        /** No new station for this long: a forgotten trip. Shorter when the trip started by itself. */
        private const val IDLE_LIMIT_AUTO_MS = 15 * 60_000L
        private const val IDLE_LIMIT_MANUAL_MS = 60 * 60_000L

        fun startIntent(context: Context, auto: Boolean = false, stationId: String? = null): Intent =
            Intent(context, TripService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_AUTO, auto).putExtra(EXTRA_STATION, stationId)

        /** [auto] = started by a station geofence rather than by the rider. */
        fun start(context: Context, auto: Boolean = false, stationId: String? = null) =
            ContextCompat.startForegroundService(context, startIntent(context, auto, stationId))

        fun stop(context: Context) = context.startService(Intent(context, TripService::class.java).setAction(ACTION_STOP))
    }

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private var network: MetroNetwork? = null
    private var engine: TripEngine? = null
    private var trace: TraceWriter? = null
    private var speaker: Speaker? = null
    private var startNs = 0L
    private var lastTitle = "Поездка начата"
    private var lastText = "Ищу станцию…"
    private var tripFile: File? = null
    private val visited = ArrayList<String>()
    private var autoStarted = false
    /** Entry station of a self-started trip: not spoken until another station is reached. */
    private var silentAtStation: String? = null
    @Volatile private var outsideSinceMs: Long? = null
    @Volatile private var lastProgressMs = 0L

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
            ACTION_TOGGLE_MUTE -> {
                ProfileStore.setMuted(this, !ProfileStore.profile.value.muted)
                if (!::handler.isInitialized) stopSelf() // no trip running: nothing to keep alive
                return START_NOT_STICKY
            }
            else -> if (!::handler.isInitialized) {
                startTrip(intent?.getBooleanExtra(EXTRA_AUTO, false) == true, intent?.getStringExtra(EXTRA_STATION))
            }
        }
        return START_NOT_STICKY
    }

    private fun startTrip(auto: Boolean, entryStation: String?) {
        createChannel()
        ProfileStore.init(this)
        val net = CityRepository.load(this)
        if (auto && entryStation != null && net != null) {
            lastTitle = "Поездка начата: ${net.station(entryStation).name.let { it["ru"] ?: it["en"] ?: entryStation }}"
            lastText = "Запустилась сама у станции. Остановится, когда выйдете из метро."
        }
        val started = runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        }.isSuccess
        // Location permission revoked, or the system refused a background start: nothing to run.
        if (!started || net == null) { stopSelf(); return }
        getSystemService(NotificationManager::class.java).cancel(AutoStart.PROMPT_NOTIFICATION_ID) // the reminder did its job
        network = net
        autoStarted = auto
        // A trip that started by itself stays silent until the rider really travels: walking past a
        // station must not put "Station X" into the headphones.
        silentAtStation = entryStation.takeIf { auto }
        lastProgressMs = SystemClock.elapsedRealtime()

        thread = HandlerThread("trip").apply { start() }
        handler = Handler(thread.looper)
        startNs = SystemClock.elapsedRealtimeNanos()

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(CityRepository.tripsDir(this), "trip-$stamp.jsonl.gz")
        tripFile = file
        trace = TraceWriter(file)
        speaker = Speaker(this) { lang -> TripBus.update { it.copy(missingVoices = it.missingVoices + lang) } }

        handler.post {
            trace?.write(TraceRecord.Meta(0.0, net.graph.city, "${Build.MANUFACTURER} ${Build.MODEL} API ${Build.VERSION.SDK_INT}", "0.7.1"))
            engine = TripEngine(net, listOf("ru", "en"), listener = EngineListener()).also { e ->
                TripBus.state.value.destination?.let {
                    trace?.write(TraceRecord.Dest(0.0, it))
                    e.setDestination(it)
                }
            }
        }

        registerSensors()
        requestLocation()
        handler.postDelayed(cellTick, 1_000)
        registerWifi()
        handler.postDelayed(watchdog, WATCHDOG_PERIOD_MS)

        lifecycleScope.launch {
            TripBus.commands.collect { cmd ->
                handler.post {
                    when (cmd) {
                        is Command.AtStation -> {
                            trace?.write(TraceRecord.Label(now(), cmd.stationId))
                            engine?.setStation(cmd.stationId)
                            visit(cmd.stationId)
                        }
                        is Command.Destination -> {
                            trace?.write(TraceRecord.Dest(now(), cmd.stationId))
                            engine?.setDestination(cmd.stationId)
                        }
                    }
                }
            }
        }
        // Mute can be toggled from the notification or the screen: keep the notification action in sync.
        lifecycleScope.launch { ProfileStore.profile.collect { refreshNotification() } }
        TripBus.update {
            UiState(running = true, tripFile = file.absolutePath, destination = it.destination, startedElapsedMs = SystemClock.elapsedRealtime())
        }
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
        if (visited.size >= 2) AutoStart.sync(this) // a finished trip may change which stations count as "mine"
        // The destination belongs to this ride: tomorrow's trip starts without yesterday's "home".
        TripBus.update { it.copy(running = false, phase = "—", estimate = null, lastSaid = null, destination = null) }
        super.onDestroy()
    }

    // --- engine output -----------------------------------------------------------------

    private inner class EngineListener : TripEngine.Listener {
        override fun onAnnouncements(announcements: List<Announcement>) {
            val t = now()
            announcements.forEach { trace?.write(TraceRecord.Say(t, it.key, it.text)) }
            val station = announcements.firstOrNull { it.kind == AnnouncementKind.STATION || it.kind == AnnouncementKind.APPROACHING }?.stationId
            if (station != null && station != silentAtStation) silentAtStation = null
            val settings = ProfileStore.profile.value
            if (!settings.muted && silentAtStation == null) {
                speaker?.speak(announcements.filter { it.language in settings.languages })
            }
            station?.let(::visit)
            TripBus.update { it.copy(lastSaid = announcements.joinToString(" ") { a -> a.text }) }
        }

        override fun onEstimate(estimate: Estimate, phase: Phase) {
            if (!estimate.outside) outsideSinceMs = null
            else if (outsideSinceMs == null) outsideSinceMs = SystemClock.elapsedRealtime()
            TripBus.update { it.copy(estimate = estimate, phase = phase.name) }
            updateNotification(estimate)
        }

        override fun onPhase(change: PhaseChange) {
            trace?.write(TraceRecord.Phase(change.atSeconds, change.phase.name, engine?.detector?.lastVariance ?: 0.0))
        }
    }

    /** Remembers the stations passed and keeps the sidecar file for the history list up to date. */
    private fun visit(stationId: String) {
        if (visited.lastOrNull() == stationId) return
        visited += stationId
        lastProgressMs = SystemClock.elapsedRealtime()
        tripFile?.let { f -> runCatching { TripHistory.stationsFile(f).writeText(visited.joinToString("\n")) } }
    }

    private fun updateNotification(e: Estimate) {
        val net = network ?: return
        fun name(id: String) = net.station(id).name.let { it["ru"] ?: it["en"] ?: id }
        val current = e.current?.takeIf { e.atStationProbability > 0.5 }
        val next = e.next?.takeIf { it.probability >= 0.85 }
        val (title, text) = when {
            current != null && current.stationId == TripBus.state.value.destination -> name(current.stationId) to "Вы приехали. Хорошего дня!"
            current != null -> name(current.stationId) to (next?.let { "Следующая: ${name(it.stationId)}" } ?: "Направление уточняется")
            next != null -> "→ ${name(next.stationId)}" to "В пути"
            e.outside -> "Вы вышли из метро" to "Поездка скоро завершится сама"
            e.current == null && e.next == null -> "Ищу станцию" to "Жду сигнал сети"
            else -> "Между станциями" to "Направление уточняется"
        }
        if (title == lastTitle && text == lastText) return
        lastTitle = title
        lastText = text
        refreshNotification()
    }

    /** Ends trips nobody is going to end: the rider walked out of the metro, or simply forgot. */
    private val watchdog = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val outside = outsideSinceMs
            val idleLimit = if (autoStarted) IDLE_LIMIT_AUTO_MS else IDLE_LIMIT_MANUAL_MS
            when {
                outside != null && now - outside >= LEAVE_GRACE_MS -> finish("Вы вышли из метро.")
                now - lastProgressMs >= idleLimit -> finish("Давно нет новых станций.")
                else -> handler.postDelayed(this, WATCHDOG_PERIOD_MS)
            }
        }
    }

    private fun finish(reason: String) {
        val route = visited.takeIf { it.size >= 2 }?.let { v ->
            val net = network
            fun name(id: String) = net?.station(id)?.name?.let { it["ru"] ?: it["en"] } ?: id
            " ${name(v.first())} → ${name(v.last())}."
        }.orEmpty()
        // A self-started trip that went nowhere (walked past a station) ends without a word.
        if (!(autoStarted && visited.size < 2)) {
            val n = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_metro)
                .setContentTitle("Поездка завершена")
                .setContentText("$reason$route Запись сохранена в истории.")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$reason$route Запись сохранена в истории."))
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                .build()
            getSystemService(NotificationManager::class.java).notify(FINISHED_NOTIFICATION_ID, n)
        } else {
            // Nothing happened on it: do not litter the history either.
            trace?.close()
            trace = null
            tripFile?.let { TripHistory.delete(it.absolutePath) }
        }
        stopSelf()
    }

    private fun refreshNotification() {
        if (!::handler.isInitialized) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
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
                TripBus.update { it.copy(lastFixElapsedMs = loc.elapsedRealtimeNanos / 1_000_000, lastFixAccuracyMeters = loc.accuracy.toInt()) }
                engine?.onLocation(t, loc.latitude, loc.longitude, loc.accuracy.toDouble())
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocation() {
        // High accuracy: the phone scans Wi-Fi and cells as often as it can. Underground a station gives
        // only ~20 s of signal, so every second counts; the battery cost is accepted for the ride.
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000)
            .setMinUpdateIntervalMillis(1_000)
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

    private fun serviceAction(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(this, requestCode, Intent(this, TripService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)

    private fun notification() = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_metro)
        .setContentTitle(lastTitle)
        .setContentText(lastText)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
        )
        .addAction(0, if (ProfileStore.profile.value.muted) "Включить звук" else "Без звука", serviceAction(ACTION_TOGGLE_MUTE, 2))
        .addAction(0, "Стоп", serviceAction(ACTION_STOP, 1))
        .build()
}
