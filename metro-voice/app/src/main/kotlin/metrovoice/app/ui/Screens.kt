package metrovoice.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import metrovoice.app.Account
import metrovoice.app.Profile
import metrovoice.app.TripInfo
import metrovoice.app.UiState
import metrovoice.core.graph.Line
import metrovoice.core.graph.MetroNetwork

private fun MetroNetwork.ruName(id: String) = station(id).name.let { it["ru"] ?: it["en"] ?: id }
// Zero-width space after hyphens: long names ("Ахметели-Варкетильская") may wrap there instead of mid-word.
private fun Line.ruName() = (name["ru"] ?: name["en"] ?: id).removeSuffix(" линия").replace("-", "-\u200B")
private fun MetroNetwork.cityName() = graph.cityName.let { it["ru"] ?: it["en"] } ?: graph.city.replaceFirstChar(Char::titlecase)

// ------------------------------------------------------------------------------------------
// Home: nothing is running. Where to, which lines exist, one button to start.
// ------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(
    network: MetroNetwork,
    profile: Profile,
    destination: String?,
    onStart: () -> Unit,
    onDemo: () -> Unit,
    onHistory: () -> Unit,
    onProfile: () -> Unit,
    onOpenLine: (String) -> Unit,
    onDestination: (String?) -> Unit,
    onSetHome: (String) -> Unit,
    onSetWork: (String) -> Unit,
) {
    val p = LocalPalette.current
    var picking by remember { mutableStateOf<String?>(null) } // "home" | "work" | "other"

    ScreenColumn(
        scroll = true,
        bottomBar = { PrimaryButton("Начать поездку", onStart, Modifier.fillMaxWidth().padding(vertical = 12.dp)) },
    ) {
        TopBar(
            leading = { CityPill(network.cityName()) },
            actions = {
                SquareIconButton(AppIcons.History, "История поездок", onHistory)
                Spacer(Modifier.width(8.dp))
                SquareIconButton(Icons.Default.Person, "Профиль", onProfile)
            },
        )

        CardSurface(Modifier.fillMaxWidth().padding(top = 8.dp), color = p.tintCream, padding = PaddingValues(0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 20.dp, top = 20.dp, bottom = 20.dp)) {
                    Text("Готов к поездке", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (profile.autoStart) "Начну сам, когда подойдёте к станции." else "Назову станцию и следующую — прямо в наушники.",
                        fontSize = 14.sp, color = p.ink.copy(alpha = 0.72f),
                    )
                    Spacer(Modifier.height(12.dp))
                    PillButton("Демо", onDemo, icon = Icons.Default.PlayArrow, filled = true)
                }
                ModeIllustration("subway", network.lines.first().displayColor(), Modifier.padding(end = 8.dp).size(width = 120.dp, height = 132.dp))
            }
        }

        SectionTitle("Куда едем")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlaceTile(
                Icons.Default.Home, "Дом", profile.homeStationId?.let(network::ruName),
                selected = destination != null && destination == profile.homeStationId,
                onClick = { profile.homeStationId?.let { onDestination(if (destination == it) null else it) } ?: run { picking = "home" } },
            )
            PlaceTile(
                AppIcons.Work, "Работа", profile.workStationId?.let(network::ruName),
                selected = destination != null && destination == profile.workStationId,
                onClick = { profile.workStationId?.let { onDestination(if (destination == it) null else it) } ?: run { picking = "work" } },
            )
            val other = destination?.takeIf { it != profile.homeStationId && it != profile.workStationId }
            PlaceTile(
                AppIcons.Flag, "Другая станция", other?.let(network::ruName),
                selected = other != null, emptyLabel = "Выбрать",
                onClick = { if (other != null) onDestination(null) else picking = "other" },
            )
        }
        Text(
            "Необязательно. С целью направление известно с первой станции.",
            fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 8.dp),
        )

        // One section per kind of transport: a second city or trams add data, not screens.
        network.lines.groupBy { it.mode }.forEach { (mode, lines) ->
            SectionTitle(modeTitle(mode))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEach { line -> LineRow(network, line) { onOpenLine(line.id) } }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    picking?.let { which ->
        StationPickerDialog(
            network,
            title = when (which) { "home" -> "Станция у дома"; "work" -> "Станция у работы"; else -> "Куда едете?" },
            onPick = { id ->
                when (which) { "home" -> onSetHome(id); "work" -> onSetWork(id) }
                onDestination(id)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun CityPill(city: String) {
    val p = LocalPalette.current
    Surface(shape = RoundedCornerShape(Radius.control), color = p.card, border = BorderStroke(1.dp, p.outline), modifier = Modifier.heightIn(min = 48.dp)) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = "Город", tint = p.ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(city, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
        }
    }
}

@Composable
private fun PlaceTile(icon: ImageVector, label: String, station: String?, selected: Boolean, onClick: () -> Unit, emptyLabel: String = "Указать") {
    val p = LocalPalette.current
    Surface(
        onClick = onClick, shape = RoundedCornerShape(Radius.tile),
        color = if (selected) p.tintBlue else p.card, contentColor = p.ink,
        border = if (selected) BorderStroke(1.5.dp, p.ink) else null,
        // Grows with the text (large system fonts) instead of breaking a station name mid-word.
        modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 148.dp, max = 240.dp).heightIn(min = 124.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(if (selected) p.card else p.canvas), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                if (selected) Icon(Icons.Default.Check, contentDescription = "Выбрано", modifier = Modifier.size(20.dp))
                else if (station == null) Icon(Icons.Default.Add, contentDescription = null, tint = p.muted, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(label, fontSize = 12.sp, color = if (selected) p.ink.copy(alpha = 0.72f) else p.muted, maxLines = 1)
            Text(
                station ?: emptyLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LineRow(network: MetroNetwork, line: Line, onClick: () -> Unit) {
    val p = LocalPalette.current
    CardSurface(Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineBadge(line, size = 36.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(line.ruName(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${stationsCount(line.stations.size)} · ${network.ruName(line.stations.first())} — ${network.ruName(line.stations.last())}",
                    fontSize = 12.sp, color = p.muted,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = p.muted)
        }
    }
}

// ------------------------------------------------------------------------------------------
// Trip: a ride is running. One big answer, the next few stations, two actions.
// ------------------------------------------------------------------------------------------

@Composable
fun TripScreen(
    network: MetroNetwork,
    state: UiState,
    profile: Profile,
    nowElapsedMs: Long,
    onStop: () -> Unit,
    onProfile: () -> Unit,
    onOpenLine: (String) -> Unit,
    onDestination: (String?) -> Unit,
    onToggleMute: () -> Unit,
) {
    val p = LocalPalette.current
    var confirmStop by remember { mutableStateOf(false) }
    var pickingDestination by remember { mutableStateOf(false) }

    val e = state.estimate
    val atStation = e?.current?.takeIf { e.atStationProbability > 0.5 }
    val next = e?.next
    val nextKnown = next?.takeIf { it.probability >= 0.85 }
    val outside = e?.outside == true
    val searching = !outside && e?.current == null && next == null
    val lineId = atStation?.lineId ?: next?.lineId
    val onTint = p.ink.copy(alpha = 0.72f) // secondary text on a tinted card: muted grey would drop below 4.5:1
    val fallbackLine = lineId ?: network.lines.first().id

    ScreenColumn(
        scroll = true,
        bottomBar = {
            val fix: @Composable (Modifier) -> Unit = { m -> PillButton("Не та станция", { onOpenLine(fallbackLine) }, m, minHeight = 56.dp) }
            val end: @Composable (Modifier) -> Unit = { m ->
                PrimaryButton(if (state.demo) "Остановить демо" else "Завершить", { if (state.demo) onStop() else confirmStop = true }, m)
            }
            // Side by side normally; stacked when the system font is large, so no label is cut.
            if (LocalDensity.current.fontScale > 1.2f) {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    end(Modifier.fillMaxWidth())
                    if (!state.demo) fix(Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!state.demo) fix(Modifier.weight(1f))
                    end(Modifier.weight(1f))
                }
            }
        },
    ) {
        TopBar(
            title = if (state.demo) "Демо-поездка" else "Поездка",
            actions = {
                SquareIconButton(
                    if (profile.muted) AppIcons.VolumeOff else AppIcons.VolumeUp,
                    if (profile.muted) "Включить звук объявлений" else "Выключить звук объявлений",
                    onToggleMute,
                )
                Spacer(Modifier.width(8.dp))
                SquareIconButton(Icons.Default.Person, "Профиль", onProfile)
            },
        )

        // The answer. Blue when standing at a station, plain in the tunnel, warm when it needs a look.
        CardSurface(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            color = when {
                atStation != null -> p.tintBlue
                searching || outside -> p.tintCream
                else -> p.card
            },
        ) {
            lineId?.let { id ->
                val line = network.line(id)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineBadge(line, size = 24.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(line.ruName(), fontSize = 14.sp, color = onTint)
                }
                Spacer(Modifier.height(12.dp))
            }
            Text(
                when {
                    outside -> "ВНЕ МЕТРО"
                    searching -> "ИЩУ СТАНЦИЮ"
                    atStation != null -> "СТАНЦИЯ"
                    nextKnown != null -> "ЕДЕМ К СТАНЦИИ"
                    else -> "В ПУТИ"
                },
                fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = onTint,
            )
            Text(
                when {
                    outside -> "Вы вышли из метро"
                    atStation != null -> network.ruName(atStation.stationId)
                    nextKnown != null -> network.ruName(nextKnown.stationId)
                    searching -> "Жду сигнал сети"
                    else -> "Между станциями"
                },
                fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            when {
                outside -> Text("Поездка завершится сама через пару минут.", fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                atStation != null -> {
                    Spacer(Modifier.height(12.dp))
                    if (nextKnown != null) {
                        Text("Следующая", fontSize = 12.sp, color = onTint)
                        Text(network.ruName(nextKnown.stationId), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text("Направление уточню на следующей станции.", fontSize = 14.sp, color = onTint)
                    }
                }
            }
        }

        if (!state.demo && !outside) {
            val f = freshness(state, nowElapsedMs)
            Text(f.text, fontSize = 12.sp, color = if (f.stale) p.danger else p.muted, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        }
        if (state.missingVoices.isNotEmpty() && !profile.muted) {
            Text(
                "Нет голоса для: ${state.missingVoices.joinToString()}. Настройки телефона → Синтез речи.",
                fontSize = 12.sp, color = p.danger, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }

        DestinationCard(network, state, atStation?.stationId, lineId, onChange = { pickingDestination = true })

        if (lineId != null) {
            val line = network.line(lineId)
            CardSurface(Modifier.fillMaxWidth().padding(top = 12.dp), padding = PaddingValues(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
                RouteWindow(network, line, atStation?.stationId, next?.stationId, nextKnown != null, state.destination)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onOpenLine(line.id) }) { Text("Вся линия") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Завершить поездку?") },
            text = { Text("Объявления прекратятся. Запись сохранится в истории поездок.") },
            confirmButton = { TextButton(onClick = { onStop(); confirmStop = false }) { Text("Завершить", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Продолжить поездку") } },
        )
    }
    if (pickingDestination) {
        StationPickerDialog(
            network, "Куда едете?",
            shortcuts = listOfNotNull(
                profile.homeStationId?.let { it to "Домой · ${network.ruName(it)}" },
                profile.workStationId?.let { it to "На работу · ${network.ruName(it)}" },
            ),
            onClear = if (state.destination != null) ({ onDestination(null); pickingDestination = false }) else null,
            onPick = { onDestination(it); pickingDestination = false },
            onDismiss = { pickingDestination = false },
        )
    }
}

@Composable
private fun DestinationCard(network: MetroNetwork, state: UiState, currentId: String?, lineId: String?, onChange: () -> Unit) {
    val p = LocalPalette.current
    val destination = state.destination
    CardSurface(Modifier.fillMaxWidth().padding(top = 12.dp), onClick = onChange, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(p.canvas), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Flag, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Куда", fontSize = 12.sp, color = p.muted)
                Text(destination?.let(network::ruName) ?: "Не выбрано", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Stations left, when the destination is on the line we are on.
            val left = if (destination != null && currentId != null && lineId != null) {
                val stations = network.line(lineId).stations
                val a = stations.indexOf(currentId)
                val b = stations.indexOf(destination)
                if (a >= 0 && b >= 0 && a != b) kotlin.math.abs(a - b) else null
            } else null
            Text(
                when {
                    left != null -> "ещё ${stationsCount(left)}"
                    destination != null && destination == currentId -> "вы на месте"
                    else -> "Изменить"
                },
                fontSize = 14.sp, color = p.muted,
            )
        }
    }
}

/** The stretch of the line that matters now: one station back, the current one, three ahead — in travel order. */
@Composable
private fun RouteWindow(network: MetroNetwork, line: Line, currentId: String?, nextId: String?, nextSure: Boolean, destination: String?) {
    val stations = line.stations
    val cur = stations.indexOf(currentId)
    val nxt = stations.indexOf(nextId)
    var direction by remember(line.id) { mutableIntStateOf(1) }
    if (cur >= 0 && nxt >= 0 && nxt != cur) direction = if (nxt > cur) 1 else -1
    val focus = if (cur >= 0) cur else nxt
    if (focus < 0) return
    val window = (-1..3).map { focus + it * direction }.filter { it in stations.indices }
    val color = line.displayColor()
    window.forEach { i ->
        StationRow(
            name = network.ruName(stations[i]), color = color,
            isCurrent = i == cur, isNext = i == nxt, nextSure = nextSure, isDestination = stations[i] == destination,
        )
    }
}

@Composable
private fun StationRow(name: String, color: Color, isCurrent: Boolean, isNext: Boolean, nextSure: Boolean, isDestination: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(32.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(color))
            Box(
                Modifier.size(if (isCurrent) 22.dp else 14.dp).clip(CircleShape)
                    .background(if (isCurrent || isNext) color else p.card)
                    .border(3.dp, color, CircleShape),
            )
        }
        Spacer(Modifier.width(12.dp))
        if (isDestination) {
            Icon(AppIcons.Flag, contentDescription = "Цель поездки", modifier = Modifier.size(18.dp), tint = p.ink)
            Spacer(Modifier.width(6.dp))
        }
        val tag: (@Composable () -> Unit)? = when {
            isCurrent -> ({ Tag("вы здесь", color) })
            isNext -> ({ Tag(if (nextSure) "далее" else "далее?", color) })
            else -> null
        }
        val weight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
        // With a large system font the tag goes under the name: side by side it would break names mid-word.
        if (LocalDensity.current.fontScale > 1.2f) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(name, fontWeight = weight, fontSize = 16.sp)
                tag?.invoke()
            }
        } else {
            Text(name, fontWeight = weight, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
            Spacer(Modifier.width(8.dp))
            tag?.invoke()
        }
    }
}

private class Freshness(val text: String, val stale: Boolean)

/** Tells the rider the app is alive: how old the last location fix is. Stations are ~1–3 min apart. */
private fun freshness(state: UiState, nowMs: Long): Freshness {
    fun ago(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s < 60) "$s с" else "${s / 60} мин"
    }
    val last = state.lastFixElapsedMs
    if (last == null) {
        val waited = state.startedElapsedMs?.let { nowMs - it } ?: 0
        return if (waited > 60_000) Freshness("Нет точки уже ${ago(waited)}. Нажмите «Не та станция» и укажите свою.", true)
        else Freshness("Жду первую точку геолокации: ${ago(waited)}", false)
    }
    val age = nowMs - last
    val accuracy = state.lastFixAccuracyMeters?.let { " · ±$it м" }.orEmpty()
    return if (age > 240_000) Freshness("Точки нет ${ago(age)}. Если ошиблось — «Не та станция».", true)
    else Freshness("Точка ${ago(age)} назад$accuracy", false)
}

// ------------------------------------------------------------------------------------------
// Line: every station of one line. Browsing when idle, correcting the position during a ride.
// ------------------------------------------------------------------------------------------

@Composable
fun LineScreen(
    network: MetroNetwork,
    state: UiState,
    initialLineId: String,
    onBack: () -> Unit,
    onAtStation: (String) -> Unit,
    /** Opens with the "are you at this station?" dialog (for screenshots). */
    initialConfirmStation: String? = null,
) {
    val p = LocalPalette.current
    var selected by remember { mutableStateOf(initialLineId) }
    var confirmStation by remember { mutableStateOf(initialConfirmStation) }
    val line = network.line(selected)
    val color = line.displayColor()
    val estimate = state.estimate.takeIf { state.running }
    val currentId = estimate?.current?.takeIf { it.lineId == line.id && estimate.atStationProbability > 0.5 }?.stationId
    val next = estimate?.next?.takeIf { it.lineId == line.id }
    val tappable = state.running && !state.demo
    val listState = rememberLazyListState()
    val focusIndex = line.stations.indexOf(currentId ?: next?.stationId)
    LaunchedEffect(focusIndex, selected) {
        if (focusIndex >= 0) listState.animateScrollToItem((focusIndex - 2).coerceAtLeast(0))
    }

    ScreenColumn {
        TopBar(if (tappable) "Где вы сейчас?" else line.ruName(), onBack)
        if (network.lines.size > 1) LineSwitcher(network.lines, selected) { selected = it }
        Text(
            if (tappable) "Нажмите станцию, на которой вы стоите."
            else "${network.ruName(line.stations.first())} — ${network.ruName(line.stations.last())} · ${stationsCount(line.stations.size)}",
            fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        CardSurface(Modifier.fillMaxWidth().weight(1f).padding(bottom = 16.dp), padding = PaddingValues(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
            LazyColumn(state = listState) {
                items(line.stations, key = { it }) { id ->
                    StationRow(
                        name = network.ruName(id), color = color,
                        isCurrent = id == currentId, isNext = id == next?.stationId,
                        nextSure = (next?.probability ?: 0.0) >= 0.85, isDestination = id == state.destination,
                        modifier = Modifier.clickable(enabled = tappable, onClickLabel = "Отметить, что я на этой станции") { confirmStation = id },
                    )
                }
            }
        }
    }

    confirmStation?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmStation = null },
            title = { Text("Вы на станции ${network.ruName(id)}?") },
            text = { Text("Позиция поезда будет исправлена на эту станцию.") },
            confirmButton = { TextButton(onClick = { onAtStation(id); confirmStation = null }) { Text("Да, я здесь", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmStation = null }) { Text("Отмена") } },
        )
    }
}

/** One chip per line with its full name; the row scrolls, so it works for two lines and for twenty. */
@Composable
private fun LineSwitcher(lines: List<Line>, selected: String, onSelect: (String) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { line ->
            val isSelected = line.id == selected
            Surface(
                onClick = { onSelect(line.id) },
                shape = RoundedCornerShape(Radius.control),
                color = if (isSelected) p.card else Color.Transparent,
                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) p.ink else p.outline),
                contentColor = p.ink,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LineBadge(line, size = 24.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(line.ruName(), fontSize = 14.sp, maxLines = 1, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// History
// ------------------------------------------------------------------------------------------

@Composable
fun HistoryScreen(
    trips: List<TripInfo>,
    onBack: () -> Unit,
    onShare: (TripInfo) -> Unit,
    onDelete: (TripInfo) -> Unit,
) {
    val p = LocalPalette.current
    var deleting by remember { mutableStateOf<TripInfo?>(null) }
    ScreenColumn {
        TopBar("История поездок", onBack)
        if (trips.isEmpty()) {
            CardSurface(Modifier.fillMaxWidth().padding(top = 8.dp), color = p.tintCream) {
                Text("Пока пусто", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Каждая поездка записывается сюда — её можно отправить для разбора.", fontSize = 14.sp, color = p.ink.copy(alpha = 0.72f))
            }
            return@ScreenColumn
        }
        Text(
            "Запись содержит показания датчиков, геолокацию и видимые соты и Wi-Fi за время поездки.",
            fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(trips, key = { it.path }) { trip ->
                CardSurface(Modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(trip.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(trip.subtitle + if (trip.recording) " · идёт запись" else "", fontSize = 12.sp, color = p.muted)
                        }
                        IconButton(onClick = { onShare(trip) }) { Icon(Icons.Default.Share, contentDescription = "Отправить запись") }
                        IconButton(onClick = { deleting = trip }, enabled = !trip.recording) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить запись")
                        }
                    }
                }
            }
        }
    }
    deleting?.let { trip ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить запись?") },
            text = { Text("${trip.title} · ${trip.subtitle}") },
            confirmButton = { TextButton(onClick = { onDelete(trip); deleting = null }) { Text("Удалить", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}

// ------------------------------------------------------------------------------------------
// Profile: places, voice, automation. Each group is its own card.
// ------------------------------------------------------------------------------------------

@Composable
fun ProfileScreen(
    network: MetroNetwork,
    account: Account,
    profile: Profile,
    /** False when this build has no sign-in backend: the sign-in entry is hidden instead of failing. */
    signInAvailable: Boolean,
    onBack: () -> Unit,
    onSetHome: (String?) -> Unit,
    onSetWork: (String?) -> Unit,
    onToggleMute: () -> Unit,
    onToggleLanguage: (String) -> Unit,
    onTestVoice: () -> Unit,
    onToggleAutoStart: () -> Unit,
    onDemo: () -> Unit,
    onSignOut: () -> Unit,
) {
    val p = LocalPalette.current
    var picking by remember { mutableStateOf<String?>(null) } // "home" | "work"

    ScreenColumn(scroll = true) {
        TopBar("Профиль", onBack)

        CardSurface(Modifier.fillMaxWidth().padding(top = 8.dp), color = p.tintBlue) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(p.ink), contentAlignment = Alignment.Center) {
                    Text((account.name ?: account.email ?: "Г").take(1).uppercase(), color = p.onInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(if (account.isGuest) "Гость" else account.name ?: "Без имени", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (account.provider) {
                            "google.com" -> "Google · ${account.email ?: ""}"
                            "apple.com" -> "Apple · ${account.email ?: "скрытый email"}"
                            else -> "Данные хранятся только на этом телефоне"
                        },
                        fontSize = 14.sp, color = p.ink.copy(alpha = 0.72f),
                    )
                }
            }
        }

        SectionTitle("Мои места")
        CardSurface(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            PlaceRow(Icons.Default.Home, "Дом", profile.homeStationId?.let(network::ruName), onClick = { picking = "home" }, onClear = { onSetHome(null) })
            HorizontalDivider(color = p.outline)
            PlaceRow(AppIcons.Work, "Работа", profile.workStationId?.let(network::ruName), onClick = { picking = "work" }, onClear = { onSetWork(null) })
        }

        SectionTitle("Озвучка")
        CardSurface(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            SwitchRow("Объявления голосом", !profile.muted) { onToggleMute() }
            HorizontalDivider(color = p.outline)
            SwitchRow("Русский", "ru" in profile.languages, enabled = profile.languages != listOf("ru")) { onToggleLanguage("ru") }
            HorizontalDivider(color = p.outline)
            SwitchRow("English", "en" in profile.languages, enabled = profile.languages != listOf("en")) { onToggleLanguage("en") }
        }
        PillButton("Проверить голос", onTestVoice, Modifier.padding(top = 12.dp), icon = AppIcons.VolumeUp)

        SectionTitle("Автоматика")
        CardSurface(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            SwitchRow("Начинать поездку у станции", profile.autoStart) { onToggleAutoStart() }
            Text(
                "Запускается сама, когда вы подходите к станции, и молчит, пока вы не поедете. Нужен доступ к геолокации " +
                    "«Разрешать всегда». Завершается поездка сама в любом случае — через пару минут после выхода из метро.",
                fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        SectionTitle("Проверка")
        CardSurface(Modifier.fillMaxWidth(), color = p.tintCream) {
            Text("Демо-поездка", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Выдуманный маршрут с пересадкой в ускоренном темпе: видно экран и слышно объявления, никуда не выезжая.",
                fontSize = 14.sp, color = p.ink.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(12.dp))
            PillButton("Запустить", onDemo, icon = Icons.Default.PlayArrow, filled = true)
        }

        if (!account.isGuest || signInAvailable) {
            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp)) {
                Text(if (account.isGuest) "Войти в аккаунт" else "Выйти из аккаунта")
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    picking?.let { which ->
        StationPickerDialog(
            network = network,
            title = if (which == "home") "Станция у дома" else "Станция у работы",
            onPick = { id ->
                if (which == "home") onSetHome(id) else onSetWork(id)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun PlaceRow(icon: ImageVector, label: String, value: String?, onClick: () -> Unit, onClear: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(p.canvas), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = p.muted)
            Text(value ?: "Выбрать станцию", fontSize = 16.sp, fontWeight = if (value != null) FontWeight.SemiBold else FontWeight.Medium)
        }
        if (value != null) TextButton(onClick = onClear) { Text("Сбросить") }
        else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = p.muted)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * Station list grouped by line. [shortcuts] (id to label) go on top — home, work; [onClear] adds "no destination".
 */
@Composable
fun StationPickerDialog(
    network: MetroNetwork,
    title: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    shortcuts: List<Pair<String, String>> = emptyList(),
    onClear: (() -> Unit)? = null,
) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(shortcuts, key = { "s-${it.first}" }) { (id, label) ->
                    Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPick(id) }.padding(vertical = 12.dp))
                }
                if (onClear != null) item(key = "clear") {
                    Text("Без цели", fontSize = 16.sp, color = p.muted, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClear).padding(vertical = 12.dp))
                }
                network.lines.forEach { line ->
                    item(key = "h-${line.id}") {
                        Row(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            LineBadge(line, size = 22.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(line.ruName(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink)
                        }
                    }
                    items(line.stations, key = { "${line.id}-$it" }) { id ->
                        Text(
                            network.ruName(id), fontSize = 16.sp, color = p.ink,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPick(id) }.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ------------------------------------------------------------------------------------------
// Welcome / sign-in (shown only when a sign-in backend is configured)
// ------------------------------------------------------------------------------------------

@Composable
fun WelcomeScreen(
    busy: Boolean,
    message: String?,
    onGoogle: () -> Unit,
    onApple: () -> Unit,
    onGuest: () -> Unit,
) {
    val p = LocalPalette.current
    ScreenColumn {
        Column(Modifier.fillMaxSize().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            CardSurface(color = p.tintCream, padding = PaddingValues(24.dp)) {
                ModeIllustration("subway", Color(0xFFEB0000), Modifier.size(width = 150.dp, height = 165.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("Metro Voice", fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Станции — прямо в наушники.\nРаботает в тоннелях без интернета.",
                textAlign = TextAlign.Center, color = p.muted, fontSize = 16.sp,
            )
            Spacer(Modifier.weight(1f))
            PrimaryButton("Начать без входа", onGuest, Modifier.fillMaxWidth(), enabled = !busy)
            Spacer(Modifier.height(12.dp))
            PillButton("Войти через Google", onGoogle, Modifier.fillMaxWidth(), minHeight = 56.dp)
            Spacer(Modifier.height(12.dp))
            PillButton("Войти через Apple", onApple, Modifier.fillMaxWidth(), minHeight = 56.dp)
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = p.danger, textAlign = TextAlign.Center, fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Вход нужен, чтобы профиль пережил смену телефона. Записи поездок хранятся только на устройстве.",
                textAlign = TextAlign.Center, fontSize = 12.sp, color = p.muted,
            )
        }
    }
}
