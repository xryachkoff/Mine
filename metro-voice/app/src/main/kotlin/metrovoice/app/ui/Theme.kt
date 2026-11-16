package metrovoice.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1B4DDB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE5FF),
    onPrimaryContainer = Color(0xFF001550),
    secondary = Color(0xFF55607A),
    surface = Color(0xFFFAFAFD),
    surfaceVariant = Color(0xFFE9ECF4),
    onSurfaceVariant = Color(0xFF4A5165),
    background = Color(0xFFFAFAFD),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB5C6FF),
    onPrimary = Color(0xFF002780),
    primaryContainer = Color(0xFF00389F),
    onPrimaryContainer = Color(0xFFDDE5FF),
    secondary = Color(0xFFBDC6DC),
    surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF22262F),
    onSurfaceVariant = Color(0xFFC3C7D4),
    background = Color(0xFF111318),
)

@Composable
fun MetroTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
