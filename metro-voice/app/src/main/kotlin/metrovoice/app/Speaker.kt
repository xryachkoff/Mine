package metrovoice.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import metrovoice.core.announce.Announcement
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Speaks announcements over the user's music: requests transient focus with ducking, so the
 * music gets quieter instead of pausing, and releases focus after the last utterance.
 * v1 uses system TTS; pre-generated clips (Announcement.key) come later.
 */
class Speaker(context: Context, private val onMissingVoice: (String) -> Unit) : TextToSpeech.OnInitListener {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .build()

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private val pending = mutableListOf<Announcement>()
    private val inFlight = AtomicInteger(0)
    private var counter = 0

    override fun onInit(status: Int) {
        synchronized(this) {
            ready = status == TextToSpeech.SUCCESS
            if (!ready) return
            tts.setAudioAttributes(attrs)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = finished()
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finished()
            })
            val queued = pending.toList()
            pending.clear()
            speakNow(queued)
        }
    }

    fun speak(list: List<Announcement>) = synchronized(this) {
        if (!ready) pending += list else speakNow(list)
    }

    private fun speakNow(list: List<Announcement>) {
        val playable = list.filter { a ->
            val ok = tts.isLanguageAvailable(Locale.forLanguageTag(a.language)) >= TextToSpeech.LANG_AVAILABLE
            if (!ok) onMissingVoice(a.language)
            ok
        }
        if (playable.isEmpty()) return
        audio.requestAudioFocus(focus)
        for (a in playable) {
            tts.language = Locale.forLanguageTag(a.language)
            inFlight.incrementAndGet()
            tts.speak(a.text, TextToSpeech.QUEUE_ADD, null, "mv-${counter++}")
        }
    }

    private fun finished() {
        if (inFlight.decrementAndGet() <= 0) {
            inFlight.set(0)
            audio.abandonAudioFocusRequest(focus)
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
        audio.abandonAudioFocusRequest(focus)
    }
}
