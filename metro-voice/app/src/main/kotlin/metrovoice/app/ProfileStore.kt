package metrovoice.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Profile(
    val homeStationId: String? = null,
    val workStationId: String? = null,
    /** Announcements are tracked and shown but not spoken. */
    val muted: Boolean = false,
    /** Languages spoken, in order. Never empty. */
    val languages: List<String> = ALL_LANGUAGES,
) {
    companion object {
        val ALL_LANGUAGES = listOf("ru", "en")
    }
}

/** Local profile and settings (on the phone only). Cloud sync can come later on top of the signed-in account. */
object ProfileStore {
    private const val PREFS = "profile"
    private const val KEY_HOME = "home"
    private const val KEY_WORK = "work"
    private const val KEY_GUEST = "guest"
    private const val KEY_MUTED = "muted"
    private const val KEY_LANGUAGES = "languages"

    private val _profile = MutableStateFlow(Profile())
    val profile: StateFlow<Profile> = _profile

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(context: Context) {
        val p = prefs(context)
        val languages = p.getString(KEY_LANGUAGES, null)?.split(',')?.filter { it in Profile.ALL_LANGUAGES }
        _profile.value = Profile(
            homeStationId = p.getString(KEY_HOME, null),
            workStationId = p.getString(KEY_WORK, null),
            muted = p.getBoolean(KEY_MUTED, false),
            languages = languages?.takeIf { it.isNotEmpty() } ?: Profile.ALL_LANGUAGES,
        )
    }

    fun setHome(context: Context, stationId: String?) {
        prefs(context).edit().putString(KEY_HOME, stationId).apply()
        _profile.value = _profile.value.copy(homeStationId = stationId)
    }

    fun setWork(context: Context, stationId: String?) {
        prefs(context).edit().putString(KEY_WORK, stationId).apply()
        _profile.value = _profile.value.copy(workStationId = stationId)
    }

    fun setMuted(context: Context, muted: Boolean) {
        prefs(context).edit().putBoolean(KEY_MUTED, muted).apply()
        _profile.value = _profile.value.copy(muted = muted)
    }

    /** Turns one language on or off; the last remaining language cannot be turned off. */
    fun toggleLanguage(context: Context, language: String) {
        val current = _profile.value.languages
        val next = Profile.ALL_LANGUAGES.filter { if (it == language) it !in current else it in current }
        if (next.isEmpty()) return
        prefs(context).edit().putString(KEY_LANGUAGES, next.joinToString(",")).apply()
        _profile.value = _profile.value.copy(languages = next)
    }

    fun isGuest(context: Context): Boolean = prefs(context).getBoolean(KEY_GUEST, false)

    fun setGuest(context: Context, guest: Boolean) {
        prefs(context).edit().putBoolean(KEY_GUEST, guest).apply()
    }
}
