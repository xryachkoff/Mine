package metrovoice.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
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

// Type scale: 12 / 14 / 16 / 20 / 24 / 34 sp. Nothing else.

private fun MetroNetwork.ruName(id: String) = station(id).name.let { it["ru"] ?: it["en"] ?: id }
private fun Line.ruName() = (name["ru"] ?: name["en"] ?: id).removeSuffix(" линия")

/**
 * Line colour made safe for text: map colours are tuned for paper, not for white-on-colour labels.
 * Darkened until white text on it reaches 4.5:1; still well above 3:1 against both app backgrounds.
 */
private fun Line.displayColor(): Color {
    var c = when (color?.lowercase()) {
        null -> Color(0xFF1B4DDB)
        "green" -> Color(0xFF1E9E4A)
        "red" -> Color(0xFFE02424)
        else -> runCatching { Color(android.graphics.Color.parseColor(color)) }.getOrDefault(Color(0xFF1B4DDB))
    }
    while (c.luminance() > 0.183f) c = Color(c.red * 0.96f, c.green * 0.96f, c.blue * 0.96f)
    return c
}

/** Content column: full width on a phone, capped and centred on an unfolded foldable or tablet. */
@Composable
private fun ScreenColumn(scroll: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        val base = Modifier.widthIn(max = 600.dp).fillMaxHeight().padding(horizontal = 16.dp)
        Column(if (scroll) base.verticalScroll(rememberScrollState()) else base, content = content)
    }
}

@Composable
private fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
        }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
    ScreenColumn {
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            LineMotif(Modifier.size(width = 120.dp, height = 160.dp))
            Spacer(Modifier.height(24.dp))
            Text("Metro Voice", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Станции — прямо в наушники.\nРаботает в тоннелях без интернета.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            )
            Spacer(Modifier.weight(1f))

            Button(onClick = onGuest, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Начать без входа", fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onGoogle, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Войти через Google", fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onApple, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Войти через Apple", fontSize = 16.sp)
            }

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Вход нужен, чтобы профиль пережил смену телефона. Записи поездок хранятся только на устройстве.",
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Small vertical metro-line illustration: line with stations, one highlighted. Decorative. */
@Composable
private fun LineMotif(modifier: Modifier) {
    val red = Color(0xFFE02424)
    val green = Color(0xFF1E9E4A)
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        val x1 = size.width * 0.35f
        val x2 = size.width * 0.70f
        drawLine(red, Offset(x1, 0f), Offset(x1, size.height), strokeWidth = 14f, cap = StrokeCap.Round)
        drawLine(green, Offset(x2, size.height * 0.45f), Offset(x2, size.height), strokeWidth = 14f, cap = StrokeCap.Round)
        drawLine(Color.Gray, Offset(x1, size.height * 0.55f), Offset(x2, size.height * 0.55f), strokeWidth = 6f)
        listOf(0.1f, 0.32f, 0.55f, 0.78f, 0.95f).forEachIndexed { i, f ->
            val big = i == 2
            drawCircle(red, radius = if (big) 22f else 13f, center = Offset(x1, size.height * f))
            drawCircle(surface, radius = if (big) 10f else 6f, center = Offset(x1, size.height * f))
        }
        listOf(0.55f, 0.8f, 0.97f).forEach { f ->
            drawCircle(green, radius = 13f, center = Offset(x2, size.height * f))
            drawCircle(surface, radius = 6f, center = Offset(x2, size.height * f))
        }
    }
}

// ------------------------------------------------------------------------------------------
// Trip
// ------------------------------------------------------------------------------------------

/**
 * Main screen: where am I, what is next, the line. Position-changing and trip-ending actions
 * ask for confirmation: the phone is used one-handed in a shaking carriage.
 */
@Composable
fun TripScreen(
    network: MetroNetwork,
    state: UiState,
    profile: Profile,
    nowElapsedMs: Long,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onHistory: () -> Unit,
    onProfile: () -> Unit,
    onAtStation: (String) -> Unit,
    onDestination: (String?) -> Unit,
    onToggleMute: () -> Unit,
    /** Opens with the "are you at this station?" dialog (for screenshots). */
    initialConfirmStation: String? = null,
) {
    var confirmStation by remember { mutableStateOf(initialConfirmStation) }
    var confirmStop by remember { mutableStateOf(false) }
    var pickingDestination by remember { mutableStateOf(false) }

    ScreenColumn {
        TopBar("Metro Voice") {
            IconButton(onClick = onHistory) { Icon(AppIcons.History, contentDescription = "История поездок") }
            IconButton(onClick = onProfile) { Icon(Icons.Default.Person, contentDescription = "Профиль") }
        }

        if (state.running) {
            NowCard(network, state, profile.muted, nowElapsedMs, onToggleMute)
            Spacer(Modifier.height(8.dp))
        }
        if (state.missingVoices.isNotEmpty() && !profile.muted) {
            Text(
                "Нет голоса для: ${state.missingVoices.joinToString()}. Настройки → Синтез речи.",
                color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        DestinationRow(network, profile, state.destination, onDestination, onPickOther = { pickingDestination = true })
        Spacer(Modifier.height(4.dp))
        LineStrip(network, state, onTapStation = { confirmStation = it }, modifier = Modifier.weight(1f))

        Button(
            onClick = {
                when {
                    !state.running -> onStart()
                    state.demo -> onStop()
                    else -> confirmStop = true
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp),
            colors = if (state.running) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) else ButtonDefaults.buttonColors(),
        ) {
            Text(
                when {
                    state.demo -> "Остановить демо"
                    state.running -> "Остановить поездку"
                    else -> "Начать поездку"
                },
                fontSize = 16.sp,
            )
        }
    }

    confirmStation?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmStation = null },
            title = { Text("Вы на станции ${network.ruName(id)}?") },
            text = { Text("Позиция поезда будет исправлена на эту станцию.") },
            confirmButton = { Button(onClick = { onAtStation(id); confirmStation = null }) { Text("Да, я здесь") } },
            dismissButton = { TextButton(onClick = { confirmStation = null }) { Text("Отмена") } },
        )
    }
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Остановить поездку?") },
            text = { Text("Объявления прекратятся. Запись сохранится в истории поездок.") },
            confirmButton = { Button(onClick = { onStop(); confirmStop = false }) { Text("Остановить") } },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Продолжить поездку") } },
        )
    }
    if (pickingDestination) {
        StationPickerDialog(
            network, "Куда едете?",
            onPick = { onDestination(it); pickingDestination = false },
            onDismiss = { pickingDestination = false },
        )
    }
}

@Composable
private fun NowCard(network: MetroNetwork, state: UiState, muted: Boolean, nowElapsedMs: Long, onToggleMute: () -> Unit) {
    val e = state.estimate
    val atStation = e?.current?.takeIf { e.atStationProbability > 0.5 }
    val nextKnown = e?.next?.takeIf { it.probability >= 0.85 }
    val searching = e?.current == null && e?.next == null
    // A station is a calm, "primary" state; in the tunnel and while searching the card steps back.
    val (container, content) = if (atStation != null) {
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container, contentColor = content)) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp, end = 4.dp)) {
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(
                    (if (state.demo) "ДЕМО · " else "") + when {
                        searching -> "ИЩУ СТАНЦИЮ"
                        atStation != null -> "СТАНЦИЯ"
                        else -> "В ПУТИ"
                    },
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                )
                Text(
                    when {
                        atStation != null -> network.ruName(atStation.stationId)
                        nextKnown != null -> "→ ${network.ruName(nextKnown.stationId)}"
                        searching -> "Жду сигнал сети"
                        else -> "Между станциями"
                    },
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (atStation != null) {
                    Text(
                        nextKnown?.let { "Следующая: ${network.ruName(it.stationId)}" } ?: "Направление уточню на следующей станции",
                        fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!state.demo) {
                    val freshness = freshness(state, nowElapsedMs)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        freshness.text, fontSize = 12.sp,
                        color = if (freshness.stale) MaterialTheme.colorScheme.error else content.copy(alpha = 0.8f),
                    )
                }
            }
            IconButton(onClick = onToggleMute) {
                Icon(
                    if (muted) AppIcons.VolumeOff else AppIcons.VolumeUp,
                    contentDescription = if (muted) "Включить звук объявлений" else "Выключить звук объявлений",
                )
            }
        }
    }
}

private class Freshness(val text: String, val stale: Boolean)

/** Tells the user the app is alive: how old the last location fix is. Stations are ~2–3 min apart. */
private fun freshness(state: UiState, nowMs: Long): Freshness {
    fun ago(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s < 60) "$s с" else "${s / 60} мин"
    }
    val last = state.lastFixElapsedMs
    if (last == null) {
        val waited = state.startedElapsedMs?.let { nowMs - it } ?: 0
        return if (waited > 60_000) Freshness("Нет точки уже ${ago(waited)}. Нажмите свою станцию в списке.", true)
        else Freshness("Жду первую точку геолокации: ${ago(waited)}", false)
    }
    val age = nowMs - last
    val accuracy = state.lastFixAccuracyMeters?.let { " · ±$it м" }.orEmpty()
    return if (age > 240_000) Freshness("Точки нет ${ago(age)}. Ошиблось — нажмите свою станцию.", true)
    else Freshness("Точка ${ago(age)} назад$accuracy", false)
}

/** Where the user is going: gives the direction before the first station. Always visible, so it is discoverable. */
@Composable
private fun DestinationRow(
    network: MetroNetwork,
    profile: Profile,
    destination: String?,
    onDestination: (String?) -> Unit,
    onPickOther: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Куда:", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        @Composable
        fun place(id: String, label: String, icon: ImageVector) = FilterChip(
            selected = destination == id,
            onClick = { onDestination(if (destination == id) null else id) },
            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            label = { Text(label, maxLines = 1) },
        )
        profile.homeStationId?.let { place(it, "Домой", Icons.Default.Home) }
        profile.workStationId?.let { place(it, "Работа", AppIcons.Work) }
        val other = destination?.takeIf { it != profile.homeStationId && it != profile.workStationId }
        FilterChip(
            selected = other != null,
            onClick = { if (other != null) onDestination(null) else onPickOther() },
            leadingIcon = { Icon(AppIcons.Flag, contentDescription = null, modifier = Modifier.size(18.dp)) },
            label = {
                val hasPlaces = profile.homeStationId != null || profile.workStationId != null
                Text(other?.let(network::ruName) ?: if (hasPlaces) "Другая" else "Выбрать станцию", maxLines = 1)
            },
        )
    }
}

@Composable
private fun LineStrip(network: MetroNetwork, state: UiState, onTapStation: (String) -> Unit, modifier: Modifier) {
    val estimate = state.estimate.takeIf { state.running }
    val trackedLine = estimate?.current?.lineId ?: estimate?.next?.lineId
    var selected by remember { mutableStateOf(trackedLine ?: network.lines.first().id) }
    LaunchedEffect(trackedLine) { trackedLine?.let { selected = it } }

    LineSwitcher(network.lines, selected) { selected = it }

    val line = network.line(selected)
    val lineColor = line.displayColor()
    val currentId = estimate?.current?.takeIf { it.lineId == line.id && estimate.atStationProbability > 0.5 }?.stationId
    val next = estimate?.next?.takeIf { it.lineId == line.id }
    val listState = rememberLazyListState()
    val focusIndex = line.stations.indexOf(currentId ?: next?.stationId)
    LaunchedEffect(focusIndex, selected) {
        if (focusIndex >= 0) listState.animateScrollToItem((focusIndex - 2).coerceAtLeast(0))
    }
    val tappable = state.running && !state.demo

    LazyColumn(modifier.fillMaxWidth().padding(top = 4.dp), state = listState) {
        items(line.stations, key = { it }) { stationId ->
            val isCurrent = stationId == currentId
            val isNext = stationId == next?.stationId
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 48.dp)
                    .clickable(enabled = tappable, onClickLabel = "Отметить, что я на этой станции") { onTapStation(stationId) }
                    .padding(end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(32.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(lineColor))
                    Box(
                        Modifier.size(if (isCurrent) 22.dp else 14.dp).clip(CircleShape)
                            .background(if (isCurrent || isNext) lineColor else MaterialTheme.colorScheme.surface)
                            .border(3.dp, lineColor, CircleShape),
                    )
                }
                Spacer(Modifier.width(12.dp))
                if (state.destination == stationId) {
                    Icon(AppIcons.Flag, contentDescription = "Цель поездки", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    network.ruName(stationId),
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
                Spacer(Modifier.width(8.dp))
                when {
                    isCurrent -> Badge("вы здесь", lineColor)
                    isNext -> Badge(if ((next?.probability ?: 0.0) >= 0.85) "далее" else "далее?", lineColor)
                }
            }
        }
        if (tappable) {
            item(key = "hint") {
                Text(
                    "Ошиблось? Нажмите станцию, где вы сейчас.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

/** Lines as equal segments: number in the line colour (as on metro maps) plus the name. */
@Composable
private fun LineSwitcher(lines: List<Line>, selected: String, onSelect: (String) -> Unit) {
    val fits = lines.size <= 3
    Row(
        if (fits) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        lines.forEach { line ->
            val isSelected = line.id == selected
            Surface(
                onClick = { onSelect(line.id) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                border = BorderStroke(1.dp, if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant),
                modifier = (if (fits) Modifier.weight(1f) else Modifier.widthIn(max = 220.dp)).heightIn(min = 48.dp),
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Grows with the system font size instead of clipping the number.
                    Box(
                        Modifier.defaultMinSize(24.dp, 24.dp).clip(CircleShape).background(line.displayColor()).padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(line.id.take(2), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        line.ruName(), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
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
    var deleting by remember { mutableStateOf<TripInfo?>(null) }
    ScreenColumn {
        TopBar("История поездок", onBack)
        if (trips.isEmpty()) {
            Text(
                "Пока пусто. Каждая поездка записывается сюда — её можно отправить для разбора.",
                fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp),
            )
            return@ScreenColumn
        }
        Text(
            "Запись содержит показания датчиков, геолокацию и видимые соты и Wi-Fi за время поездки.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyColumn {
            items(trips, key = { it.path }) { trip ->
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(trip.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            trip.subtitle + if (trip.recording) " · идёт запись" else "",
                            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onShare(trip) }) { Icon(Icons.Default.Share, contentDescription = "Отправить запись") }
                    IconButton(onClick = { deleting = trip }, enabled = !trip.recording) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить запись")
                    }
                }
                HorizontalDivider()
            }
        }
    }
    deleting?.let { trip ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить запись?") },
            text = { Text("${trip.title} · ${trip.subtitle}") },
            confirmButton = { TextButton(onClick = { onDelete(trip); deleting = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}

// ------------------------------------------------------------------------------------------
// Profile
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
    onDemo: () -> Unit,
    onSignOut: () -> Unit,
) {
    var picking by remember { mutableStateOf<String?>(null) } // "home" | "work"

    ScreenColumn(scroll = true) {
        TopBar("Профиль", onBack)

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (account.name ?: account.email ?: "Г").take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    )
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
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Мои станции")
        Text(
            "Кнопки «Домой» и «Работа» на главном экране сразу задают направление поездки.",
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        PlaceRow(Icons.Default.Home, "Дом", profile.homeStationId?.let(network::ruName), onClick = { picking = "home" }, onClear = { onSetHome(null) })
        HorizontalDivider()
        PlaceRow(AppIcons.Work, "Работа", profile.workStationId?.let(network::ruName), onClick = { picking = "work" }, onClear = { onSetWork(null) })

        Spacer(Modifier.height(24.dp))
        SectionTitle("Озвучка")
        SwitchRow("Объявления голосом", !profile.muted) { onToggleMute() }
        SwitchRow("Русский", "ru" in profile.languages, enabled = profile.languages != listOf("ru")) { onToggleLanguage("ru") }
        SwitchRow("English", "en" in profile.languages, enabled = profile.languages != listOf("en")) { onToggleLanguage("en") }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onTestVoice, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Проверить голос") }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Проверка")
        Text(
            "Демо-поездка проигрывает выдуманный маршрут с пересадкой в ускоренном темпе: видно экран и слышно объявления, никуда не выезжая.",
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDemo, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Запустить демо-поездку") }

        if (!account.isGuest || signInAvailable) {
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (account.isGuest) "Войти в аккаунт" else "Выйти из аккаунта")
            }
        }
        Spacer(Modifier.height(16.dp))
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
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: "Выбрать станцию", fontSize = 16.sp, fontWeight = if (value != null) FontWeight.SemiBold else FontWeight.Normal)
        }
        if (value != null) TextButton(onClick = onClear) { Text("Сбросить") }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun StationPickerDialog(network: MetroNetwork, title: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                network.lines.forEach { line ->
                    item(key = "h-${line.id}") {
                        Row(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(line.displayColor()))
                            Spacer(Modifier.width(8.dp))
                            Text(line.ruName(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    items(line.stations, key = { "${line.id}-$it" }) { id ->
                        Text(
                            network.ruName(id), fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPick(id) }.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
