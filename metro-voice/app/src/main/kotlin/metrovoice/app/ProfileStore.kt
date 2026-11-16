package metrovoice.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Profile(
    val homeStationId: String? = null,
    val workStationId: String? = null,
)

/** Local profile (on the phone only). Cloud sync can come later on top of the signed-in account. */
object ProfileStore {
    private const val PREFS = "profile"
    private const val KEY_HOME = "home"
    private const val KEY_WORK = "work"
    private const val KEY_GUEST = "guest"

    private val _profile = MutableStateFlow(Profile())
    val profile: StateFlow<Profile> = _profile

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(context: Context) {
        val p = prefs(context)
        _profile.value = Profile(p.getString(KEY_HOME, null), p.getString(KEY_WORK, null))
    }

    fun setHome(context: Context, stationId: String?) {
        prefs(context).edit().putString(KEY_HOME, stationId).apply()
        _profile.value = _profile.value.copy(homeStationId = stationId)
    }

    fun setWork(context: Context, stationId: String?) {
        prefs(context).edit().putString(KEY_WORK, stationId).apply()
        _profile.value = _profile.value.copy(workStationId = stationId)
    }

    fun isGuest(context: Context): Boolean = prefs(context).getBoolean(KEY_GUEST, false)

    fun setGuest(context: Context, guest: Boolean) {
        prefs(context).edit().putBoolean(KEY_GUEST, guest).apply()
    }
}
