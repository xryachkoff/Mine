package metrovoice.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import metrovoice.app.ui.MetroTheme
import metrovoice.app.ui.ProfileScreen
import metrovoice.app.ui.TripScreen
import metrovoice.app.ui.WelcomeScreen
import metrovoice.core.graph.MetroNetwork
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ProfileStore.init(this)
        AuthManager.init(this)
        val network = CityRepository.load(this)
        setContent {
            MetroTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (network == null) {
                        Text("Нет данных города (assets/cities/${CityRepository.DEFAULT_CITY}.json).", Modifier.padding(24.dp))
                    } else {
                        App(network)
                    }
                }
            }
        }
    }

    @Composable
    private fun App(network: MetroNetwork) {
        val account by AuthManager.account.collectAsStateWithLifecycle()
        val profile by ProfileStore.profile.collectAsStateWithLifecycle()
        val state by TripBus.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        var busy by rememberSaveable { mutableStateOf(false) }
        var message by rememberSaveable { mutableStateOf<String?>(null) }
        var screen by rememberSaveable { mutableStateOf("trip") }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true || granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
                TripService.start(this)
            }
        }

        fun signIn(block: suspend () -> SignInResult) = scope.launch {
            busy = true
            message = when (val r = block()) {
                SignInResult.Success, SignInResult.Cancelled -> null
                is SignInResult.NotConfigured -> "Вход ещё не настроен: нужен ${r.what}. Пока можно продолжить без входа."
                is SignInResult.Failed -> "Не удалось войти: ${r.message}"
            }
            busy = false
        }

        val acc = account
        when {
            acc == null -> WelcomeScreen(
                busy = busy,
                message = message,
                onGoogle = { signIn { AuthManager.signInWithGoogle(this) } },
                onApple = { signIn { AuthManager.signInWithApple(this) } },
                onGuest = { AuthManager.continueAsGuest(this) },
            )
            screen == "profile" -> {
                BackHandler { screen = "trip" }
                ProfileScreen(
                    network = network,
                    account = acc,
                    profile = profile,
                    onBack = { screen = "trip" },
                    onSetHome = { ProfileStore.setHome(this, it) },
                    onSetWork = { ProfileStore.setWork(this, it) },
                    onSignOut = {
                        AuthManager.signOut(this)
                        screen = "trip"
                    },
                )
            }
            else -> TripScreen(
                network = network,
                state = state,
                profile = profile,
                onStart = { permissionLauncher.launch(requiredPermissions) },
                onStop = { TripService.stop(this) },
                onExport = ::shareLatestTrip,
                onProfile = { screen = "profile" },
                onAtStation = { TripBus.commands.tryEmit(Command.AtStation(it)) },
                onDestination = { id ->
                    TripBus.update { it.copy(destination = id) }
                    TripBus.commands.tryEmit(Command.Destination(id))
                },
            )
        }
    }

    private fun shareLatestTrip() {
        val latest = CityRepository.tripsDir(this).listFiles()?.maxByOrNull(File::lastModified) ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.files", latest)
        val send = Intent(Intent.ACTION_SEND)
            .setType(if (latest.name.endsWith(".gz")) "application/gzip" else "application/json")
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
