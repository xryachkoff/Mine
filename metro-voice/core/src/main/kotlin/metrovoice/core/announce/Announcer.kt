package metrovoice.core.announce

import metrovoice.core.graph.MetroNetwork
import metrovoice.core.tracking.Approach
import metrovoice.core.tracking.Estimate

enum class AnnouncementKind {
    /** Standing at the station. */
    STATION,
    /** About to arrive: said ahead of the stop, so there is time to get to the doors. */
    APPROACHING,
    NEXT_STATION,
    DIRECTION_UNKNOWN,
}

/**
 * One utterance. [key] identifies a pre-recorded clip (kind + station + lang) so the app can play
 * a generated audio file when it exists and fall back to system TTS with [text] otherwise.
 */
data class Announcement(
    val kind: AnnouncementKind,
    val stationId: String?,
    val language: String,
    val text: String,
) {
    val key: String get() = listOfNotNull(kind.name.lowercase(), stationId, language).joinToString("/")
}

/** Phrase templates. Russian avoids line-name declension by using "Пересадка: …". */
object Phrases {
    private val templates: Map<String, Map<String, String>> = mapOf(
        "en" to mapOf(
            "station" to "{station}.",
            "transfer" to " Change here for {lines}.",
            "approaching" to "Arriving at {station}.",
            "next" to "Next station: {station}.",
            "direction_unknown" to "Confirming direction at the next station.",
        ),
        "ru" to mapOf(
            "station" to "Станция {station}.",
            "transfer" to " Пересадка: {lines}.",
            "approaching" to "Подъезжаем к станции {station}.",
            "next" to "Следующая станция — {station}.",
            "direction_unknown" to "Направление уточню на следующей станции.",
        ),
    )

    val languages: Set<String> get() = templates.keys

    fun get(language: String, key: String): String =
        templates[language]?.get(key) ?: templates.getValue("en").getValue(key)
}

/**
 * Decides what to say and when. Core rule: a wrong announcement is worse than silence, so a phrase
 * about a station is spoken only when its probability clears [threshold].
 *
 * "Next station" is said as soon as the direction is known — together with the station announcement,
 * or at departure — and never twice for the same station. This way it does not depend on the motion
 * detector noticing the departure.
 */
class Announcer(
    private val network: MetroNetwork,
    private val languages: List<String> = listOf("ru", "en"),
    private val threshold: Double = 0.85,
    /** "Arriving at X" is said this many seconds before the estimated stop. */
    private val approachLeadSeconds: Double = 20.0,
) {
    private var lastStationSaid: String? = null
    private var lastNextSaid: String? = null
    private var directionWarningGiven = false

    fun reset() {
        lastStationSaid = null
        lastNextSaid = null
        directionWarningGiven = false
    }

    /** Call whenever the estimate may say "standing at a station": after a stop, a fix, or user input. */
    fun onStandingUpdate(estimate: Estimate): List<Announcement> {
        val current = estimate.current ?: return emptyList()
        if (current.probability < threshold || estimate.atStationProbability < threshold) return emptyList()
        val out = mutableListOf<Announcement>()

        if (current.stationId != lastStationSaid) {
            lastStationSaid = current.stationId
            lastNextSaid = null
            out += stationPhrase(AnnouncementKind.STATION, "station", current.stationId, current.lineId)
        }

        val next = nextToSay(estimate)
        if (next != null) {
            out += next
        } else if (lastNextSaid == null && estimate.next != null && !directionWarningGiven) {
            directionWarningGiven = true
            out += languages.map { lang ->
                Announcement(AnnouncementKind.DIRECTION_UNKNOWN, null, lang, Phrases.get(lang, "direction_unknown"))
            }
        }
        return out
    }

    /**
     * Call regularly while the train moves. Says "Arriving at X" once per station, shortly before the
     * estimated stop. The station is then not repeated on arrival — unless the train ends up elsewhere.
     */
    fun onApproach(approach: Approach?): List<Announcement> {
        approach ?: return emptyList()
        if (approach.probability < threshold || approach.remainingSeconds > approachLeadSeconds) return emptyList()
        if (approach.stationId == lastStationSaid) return emptyList()
        lastStationSaid = approach.stationId
        lastNextSaid = null
        return stationPhrase(AnnouncementKind.APPROACHING, "approaching", approach.stationId, approach.lineId)
    }

    private fun stationPhrase(kind: AnnouncementKind, template: String, stationId: String, lineId: String): List<Announcement> {
        val transferLines = network.transferLines(lineId, stationId)
        return languages.map { lang ->
            var text = Phrases.get(lang, template).replace("{station}", name(stationId, lang))
            if (transferLines.isNotEmpty()) {
                val lines = transferLines.joinToString(", ") { it.name[lang] ?: it.name["en"] ?: it.id }
                text += Phrases.get(lang, "transfer").replace("{lines}", lines)
            }
            Announcement(kind, stationId, lang, text)
        }
    }

    /** Call after the tracker processed a departure. */
    fun onDeparture(estimate: Estimate): List<Announcement> = nextToSay(estimate).orEmpty()

    private fun nextToSay(estimate: Estimate): List<Announcement>? {
        val next = estimate.next ?: return null
        if (next.probability < threshold || next.stationId == lastNextSaid) return null
        lastNextSaid = next.stationId
        directionWarningGiven = true
        return languages.map { lang ->
            Announcement(
                AnnouncementKind.NEXT_STATION, next.stationId, lang,
                Phrases.get(lang, "next").replace("{station}", name(next.stationId, lang)),
            )
        }
    }

    private fun name(stationId: String, lang: String): String {
        val n = network.station(stationId).name
        return n[lang] ?: n["en"] ?: stationId
    }
}
