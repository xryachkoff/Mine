package metrovoice.core.announce

import metrovoice.core.graph.MetroNetwork
import metrovoice.core.tracking.Estimate

enum class AnnouncementKind { STATION, NEXT_STATION, DIRECTION_UNKNOWN }

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
            "next" to "Next station: {station}.",
            "direction_unknown" to "Confirming direction at the next station.",
        ),
        "ru" to mapOf(
            "station" to "Станция {station}.",
            "transfer" to " Пересадка: {lines}.",
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
 */
class Announcer(
    private val network: MetroNetwork,
    private val languages: List<String> = listOf("ru", "en"),
    private val threshold: Double = 0.85,
) {
    private var lastStationSaid: String? = null
    private var lastNextSaid: String? = null
    private var directionWarningGiven = false

    fun reset() {
        lastStationSaid = null
        lastNextSaid = null
        directionWarningGiven = false
    }

    /** Call after the tracker processed a stop (or a fix while standing). */
    fun onStandingUpdate(estimate: Estimate): List<Announcement> {
        val current = estimate.current ?: return emptyList()
        if (current.probability < threshold || estimate.atStationProbability < threshold) return emptyList()
        if (current.stationId == lastStationSaid) return emptyList()
        lastStationSaid = current.stationId
        lastNextSaid = null

        val transferLines = network.transferLines(current.lineId, current.stationId)
        val out = languages.map { lang ->
            var text = Phrases.get(lang, "station").replace("{station}", name(current.stationId, lang))
            if (transferLines.isNotEmpty()) {
                val lines = transferLines.joinToString(", ") { it.name[lang] ?: it.name["en"] ?: it.id }
                text += Phrases.get(lang, "transfer").replace("{lines}", lines)
            }
            Announcement(AnnouncementKind.STATION, current.stationId, lang, text)
        }.toMutableList()

        val next = estimate.next
        val directionKnown = next != null && next.probability >= threshold
        if (!directionKnown && !directionWarningGiven) {
            directionWarningGiven = true
            out += languages.map { lang ->
                Announcement(AnnouncementKind.DIRECTION_UNKNOWN, null, lang, Phrases.get(lang, "direction_unknown"))
            }
        }
        return out
    }

    /** Call after the tracker processed a departure. */
    fun onDeparture(estimate: Estimate): List<Announcement> {
        lastStationSaid = null
        val next = estimate.next ?: return emptyList()
        if (next.probability < threshold || next.stationId == lastNextSaid) return emptyList()
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
