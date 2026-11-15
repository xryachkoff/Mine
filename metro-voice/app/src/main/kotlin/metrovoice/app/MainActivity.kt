package metrovoice.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import metrovoice.core.graph.MetroNetwork
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val network = CityRepository.load(this)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Screen(network, onExport = ::shareLatestTrip)
                }
            }
        }
    }

    private fun shareLatestTrip() {
        val latest = CityRepository.tripsDir(this).listFiles()?.maxByOrNull(File::lastModified) ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.files", latest)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "Экспорт поездки"))
    }
}

private val requiredPermissions = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.READ_PHONE_STATE)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
private fun Screen(network: MetroNetwork?, onExport: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by TripBus.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true || granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            TripService.start(context)
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Text("Metro Voice", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        StatusLine(network, state)
        Spacer(Modifier.height(12.dp))

        if (network == null) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "Нет данных города. Нужен файл assets/cities/${CityRepository.DEFAULT_CITY}.json " +
                        "(собирается из выгрузки OpenStreetMap командой import-osm).",
                    Modifier.padding(16.dp),
                )
            }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.running) {
                Button(onClick = { TripService.stop(context) }) { Text("Стоп") }
            } else {
                Button(onClick = { permissionLauncher.launch(requiredPermissions) }) { Text("Начать поездку") }
            }
            OutlinedButton(onClick = onExport) { Text("Экспорт поездки") }
        }

        if (state.missingVoices.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Нет голоса синтеза речи для: ${state.missingVoices.joinToString()}. Установите в настройках Android → Синтез речи.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        state.lastSaid?.let {
            Spacer(Modifier.height(8.dp))
            Text("🔊 $it", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        LineStrip(network, state)
    }
}

@Composable
private fun StatusLine(network: MetroNetwork?, state: UiState) {
    val phase = when (state.phase) {
        "MOVING" -> "едем"
        "STOPPED" -> "стоим"
        "WALKING" -> "идём пешком"
        else -> "—"
    }
    val text = if (!state.running) "Поездка не запущена" else "Состояние: $phase · гипотез: ${state.estimate?.hypothesisCount ?: 0}"
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LineStrip(network: MetroNetwork, state: UiState) {
    val estimate = state.estimate
    val trackedLine = estimate?.current?.lineId ?: estimate?.next?.lineId
    var selected by remember { mutableStateOf(network.lines.first().id) }
    LaunchedEffect(trackedLine) { trackedLine?.let { selected = it } }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        network.lines.forEach { line ->
            FilterChip(
                selected = selected == line.id,
                onClick = { selected = line.id },
                label = { Text(line.name["ru"] ?: line.name["en"] ?: line.id, maxLines = 1) },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Нажмите на станцию, где вы сейчас: это исправит позицию и разметит запись.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))

    val line = network.line(selected)
    val lineColor = line.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: MaterialTheme.colorScheme.primary
    LazyColumn {
        itemsIndexed(line.stations) { _, stationId ->
            val station = network.station(stationId)
            val isCurrent = estimate?.current?.let { it.stationId == stationId && it.lineId == line.id } == true
            val isNext = estimate?.next?.let { it.stationId == stationId && it.lineId == line.id } == true
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable(enabled = state.running) { TripBus.commands.tryEmit(Command.AtStation(stationId)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(28.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(6.dp).fillMaxHeight().background(lineColor))
                    Box(
                        Modifier.size(if (isCurrent) 20.dp else 12.dp).clip(CircleShape)
                            .background(if (isCurrent || isNext) lineColor else MaterialTheme.colorScheme.surface),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        station.name["ru"] ?: station.name["en"] ?: stationId,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (isCurrent) 18.sp else 16.sp,
                    )
                    station.name["en"]?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                when {
                    isCurrent -> Text("сейчас ${percent(estimate?.current?.probability)}", color = lineColor)
                    isNext -> Text("далее ${percent(estimate?.next?.probability)}", color = lineColor)
                }
            }
        }
    }
}

private fun percent(p: Double?) = p?.let { "${(it * 100).toInt()}%" } ?: ""
