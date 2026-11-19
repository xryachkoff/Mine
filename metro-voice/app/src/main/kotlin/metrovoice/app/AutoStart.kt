package metrovoice.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * Starts a trip by itself near a metro station.
 *
 * The app does not stay in the background for this: a geofence around every station is handed to the
 * system (Google Play services), which wakes the app only when the phone enters one. That needs the
 * "allow all the time" location permission. The trip ends by itself too (see TripService watchdog).
 */
object AutoStart {
    /** Geofences below ~100 m are unreliable; entrances lie within ~80 m of the station node. */
    private const val RADIUS_METERS = 150f
    private const val CHANNEL = "nearby"
    private const val PROMPT_NOTIFICATION_ID = 3

    fun hasBackgroundLocation(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine && background
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, StationReceiver::class.java).setAction(StationReceiver.ACTION_GEOFENCE),
        // Mutable: the system adds the geofence event to the intent.
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
    )

    /** Registers or removes the station geofences to match the setting and the permission. */
    @SuppressLint("MissingPermission")
    fun sync(context: Context) {
        val app = context.applicationContext
        val client = LocationServices.getGeofencingClient(app)
        if (!ProfileStore.profile.value.autoStart || !hasBackgroundLocation(app)) {
            client.removeGeofences(pendingIntent(app))
            return
        }
        val network = CityRepository.load(app) ?: return
        val fences = network.graph.stations.map { s ->
            Geofence.Builder()
                .setRequestId(s.id)
                .setCircularRegion(s.lat, s.lon, RADIUS_METERS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }
        // No initial trigger: switching the setting on while standing at a station should not start a trip.
        val request = GeofencingRequest.Builder().setInitialTrigger(0).addGeofences(fences).build()
        client.addGeofences(request, pendingIntent(app))
    }

    fun onNearStation(context: Context, stationId: String?) {
        if (TripBus.state.value.running) return
        try {
            // Background starts of a foreground service are allowed for geofence events.
            TripService.start(context, auto = true, stationId = stationId)
        } catch (e: Exception) {
            // The system said no (battery restrictions, OEM rules): ask the rider with one tap instead.
            prompt(context, stationId)
        }
    }

    private fun prompt(context: Context, stationId: String?) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Рядом станция метро", NotificationManager.IMPORTANCE_DEFAULT))
        val name = stationId?.let { id -> CityRepository.load(context)?.station(id)?.name?.let { it["ru"] ?: it["en"] } }
        val start = PendingIntent.getForegroundService(
            context, 3, TripService.startIntent(context, auto = true, stationId = stationId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(name?.let { "Рядом станция $it" } ?: "Рядом станция метро")
            .setContentText("Нажмите, чтобы начать поездку")
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60_000L)
            .setContentIntent(start)
            .build()
        runCatching { nm.notify(PROMPT_NOTIFICATION_ID, n) }
    }
}

/** Woken by the system: the phone entered a station geofence, or the phone restarted (geofences are lost on reboot). */
class StationReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_GEOFENCE = "metrovoice.GEOFENCE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        ProfileStore.init(context)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> AutoStart.sync(context)
            ACTION_GEOFENCE -> {
                val event = GeofencingEvent.fromIntent(intent) ?: return
                if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
                if (!ProfileStore.profile.value.autoStart) return
                AutoStart.onNearStation(context, event.triggeringGeofences?.firstOrNull()?.requestId)
            }
        }
    }
}
