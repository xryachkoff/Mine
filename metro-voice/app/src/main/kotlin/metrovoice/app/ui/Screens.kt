package metrovoice.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
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
private fun Line.ruName() = name["ru"] ?: name["en"] ?: id
private fun percent(p: Double?) = p?.let { "${(it * 100).toInt()}%" } ?: ""

@Composable
private fun Line.composeColor(): Color =
    color?.let { c ->
        when (c.lowercase()) {
            "green" -> Color(0xFF1E9E4A)
            "red" -> Color(0xFFE02424)
            else -> runCatching { Color(android.graphics.Color.parseColor(c)) }.getOrNull()
        }
    } ?: MaterialTheme.colorScheme.primary

// ------------------------------------------------------------------------------------------
// Welcome / sign-in
// ------------------------------------------------------------------------------------------

@Composable
fun WelcomeScreen(
    busy: Boolean,
    message: String?,
    onGoogle: () -> Unit,
    onApple: () -> Unit,
    onGuest: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        LineMotif(Modifier.size(width = 120.dp, height = 160.dp))
        Spacer(Modifier.height(24.dp))
        Text("Metro Voice", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Станции — прямо в наушники.\nРаботает в тоннелях без интернета.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 17.sp,
        )
        Spacer(Modifier.weight(1f))

        OutlinedButton(
            onClick = onGoogle, enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("G", fontWeight = FontWeight.Bold, color = Color(0xFF4285F4), fontSize = 18.sp)
            Spacer(Modifier.width(12.dp))
            Text("Войти через Google", fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onApple, enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
        ) {
            Text("Войти через Apple", fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onGuest, enabled = !busy) { Text("Продолжить без входа") }

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

/** Small vertical metro-line illustration: line with stations, one highlighted. */
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

/** Main screen: what matters during a ride — where am I, what is next, the line. Nothing else. */
@Composable
fun TripScreen(
    network: MetroNetwork,
    state: UiState,
    profile: Profile,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onHistory: () -> Unit,
    onProfile: () -> Unit,
    onAtStation: (String) -> Unit,
    onDestination: (String?) -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Metro Voice", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onHistory) { Icon(Icons.Default.DateRange, contentDescription = "История поездок") }
            IconButton(onClick = onProfile) { Icon(Icons.Default.Person, contentDescription = "Профиль") }
        }

        if (state.running) {
            NowCard(network, state)
            Spacer(Modifier.height(8.dp))
        }
        if (state.missingVoices.isNotEmpty()) {
            Text(
                "Нет голоса для: ${state.missingVoices.joinToString()}. Настройки → Синтез речи.",
                color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        LineStrip(network, state, profile, onAtStation, onDestination, Modifier.weight(1f))

        Button(
            onClick = if (state.running) onStop else onStart,
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
}

@Composable
private fun NowCard(network: MetroNetwork, state: UiState) {
    val e = state.estimate
    val atStation = e?.current?.takeIf { e.atStationProbability > 0.5 }
    val nextKnown = e?.next?.takeIf { it.probability >= 0.85 }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            val label = when {
                e?.current == null && e?.next == null -> "ИЩУ СТАНЦИЮ"
                atStation != null -> "СТАНЦИЯ"
                else -> "В ПУТИ"
            }
            Text(
                (if (state.demo) "ДЕМО · " else "") + label,
                fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                when {
                    atStation != null -> network.ruName(atStation.stationId)
                    nextKnown != null -> "→ ${network.ruName(nextKnown.stationId)}"
                    e?.current == null && e?.next == null -> "Жду сигнал сети"
                    else -> "Между станциями"
                },
                fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (atStation != null) {
                Text(
                    nextKnown?.let { "Следующая: ${network.ruName(it.stationId)}" } ?: "Направление уточню на следующей станции",
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LineStrip(
    network: MetroNetwork,
    state: UiState,
    profile: Profile,
    onAtStation: (String) -> Unit,
    onDestination: (String?) -> Unit,
    modifier: Modifier,
) {
    val estimate = state.estimate.takeIf { state.running }
    val trackedLine = estimate?.current?.lineId ?: estimate?.next?.lineId
    var selected by remember { mutableStateOf(trackedLine ?: network.lines.first().id) }
    LaunchedEffect(trackedLine) { trackedLine?.let { selected = it } }

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        profile.homeStationId?.let { id ->
            FilterChip(
                selected = state.destination == id,
                onClick = { onDestination(if (state.destination == id) null else id) },
                label = { Text("🏠 Домой", maxLines = 1) },
            )
        }
        profile.workStationId?.let { id ->
            FilterChip(
                selected = state.destination == id,
                onClick = { onDestination(if (state.destination == id) null else id) },
                label = { Text("💼 На работу", maxLines = 1) },
            )
        }
        network.lines.forEach { line ->
            FilterChip(
                selected = selected == line.id,
                onClick = { selected = line.id },
                leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(line.composeColor())) },
                label = { Text(line.ruName().removeSuffix(" линия"), maxLines = 1) },
            )
        }
    }

    val line = network.line(selected)
    val lineColor = line.composeColor()
    val currentId = estimate?.current?.takeIf { it.lineId == line.id && estimate.atStationProbability > 0.5 }?.stationId
    val next = estimate?.next?.takeIf { it.lineId == line.id }
    val listState = rememberLazyListState()
    val focusIndex = line.stations.indexOf(currentId ?: next?.stationId)
    LaunchedEffect(focusIndex, selected) {
        if (focusIndex >= 0) listState.animateScrollToItem((focusIndex - 2).coerceAtLeast(0))
    }

    LazyColumn(modifier.fillMaxWidth(), state = listState) {
        items(line.stations, key = { it }) { stationId ->
            val isCurrent = stationId == currentId
            val isNext = stationId == next?.stationId
            Row(
                Modifier.fillMaxWidth().height(48.dp).clickable(enabled = state.running && !state.demo) { onAtStation(stationId) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(32.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(6.dp).fillMaxHeight().background(lineColor))
                    Box(
                        Modifier.size(if (isCurrent) 22.dp else 14.dp).clip(CircleShape)
                            .background(if (isCurrent || isNext) lineColor else MaterialTheme.colorScheme.surface)
                            .border(3.dp, lineColor, CircleShape),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    (if (state.destination == stationId) "🏁 " else "") + network.ruName(stationId),
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when {
                    isCurrent -> Badge("вы здесь", lineColor)
                    isNext -> Badge(if ((next?.probability ?: 0.0) >= 0.85) "далее" else "далее?", lineColor)
                }
            }
        }
        if (state.running && !state.demo) {
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

@Composable
private fun Badge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f)) {
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
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
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text("История поездок", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (trips.isEmpty()) {
            Text(
                "Пока пусто. Каждая поездка записывается сюда — её можно отправить для разбора.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp),
            )
            return@Column
        }
        Text(
            "Запись содержит показания датчиков, геолокацию и видимые соты и Wi-Fi за время поездки.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyColumn {
            items(trips, key = { it.path }) { trip ->
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(trip.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            trip.subtitle + if (trip.recording) " · идёт запись" else "",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onShare(trip) }) { Icon(Icons.Default.Share, contentDescription = "Отправить") }
                    IconButton(onClick = { deleting = trip }, enabled = !trip.recording) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить")
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
    onBack: () -> Unit,
    onSetHome: (String?) -> Unit,
    onSetWork: (String?) -> Unit,
    onSignOut: () -> Unit,
    onDemo: () -> Unit,
) {
    var picking by remember { mutableStateOf<String?>(null) } // "home" | "work"

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text("Профиль", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (account.name ?: account.email ?: "Г").take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(if (account.isGuest) "Гость" else account.name ?: "Без имени", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (account.provider) {
                            "google.com" -> "Google · ${account.email ?: ""}"
                            "apple.com" -> "Apple · ${account.email ?: "скрытый email"}"
                            else -> "Данные только на этом телефоне"
                        },
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Мои станции", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(
            "Кнопки «Домой» и «На работу» на главном экране сразу задают направление поездки.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        PlaceRow("🏠", "Дом", profile.homeStationId?.let(network::ruName), onClick = { picking = "home" }, onClear = { onSetHome(null) })
        HorizontalDivider()
        PlaceRow("💼", "Работа", profile.workStationId?.let(network::ruName), onClick = { picking = "work" }, onClear = { onSetWork(null) })

        Spacer(Modifier.height(24.dp))
        Text("Озвучка", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Русский, затем английский · системный синтез речи", fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp))

        Spacer(Modifier.height(24.dp))
        Text("Проверка", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(
            "Демо-поездка проигрывает выдуманный маршрут с пересадкой в ускоренном темпе: видно экран и слышно объявления, никуда не выезжая.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDemo, modifier = Modifier.fillMaxWidth()) { Text("Запустить демо-поездку") }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text(if (account.isGuest) "Войти в аккаунт" else "Выйти из аккаунта")
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
private fun PlaceRow(icon: String, label: String, value: String?, onClick: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 24.sp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: "Не указано — выбрать станцию", fontSize = 17.sp, fontWeight = if (value != null) FontWeight.SemiBold else FontWeight.Normal)
        }
        if (value != null) TextButton(onClick = onClear) { Text("Сбросить") }
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
                        Text(
                            line.ruName(), fontWeight = FontWeight.Bold, color = line.composeColor(),
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(line.stations, key = { "${line.id}-$it" }) { id ->
                        Text(
                            network.ruName(id), fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
