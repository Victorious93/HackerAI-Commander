package ai.hackerai.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Matches res/values/colors.xml (ic_launcher_background / brand_primary).
val Navy = Color(0xFF0B1021)
val Brand = Color(0xFF4F7CFF)
private val OnBrand = Color(0xFFFFFFFF)
private val Surface = Color(0xFF12182B)
private val OnSurface = Color(0xFFE8ECF8)
private val OnSurfaceVariant = Color(0xFFA8B0C8)
private val Error = Color(0xFFFFB4AB)
private val ErrorContainer = Color(0xFF93000A)
private val OnErrorContainer = Color(0xFFFFDAD6)

private val DarkColors = darkColorScheme(
    primary = Brand,
    onPrimary = OnBrand,
    primaryContainer = Color(0xFF1A274D),
    onPrimaryContainer = Color(0xFFD6E0FF),
    secondary = Brand.copy(alpha = 0.85f),
    onSecondary = OnBrand,
    background = Navy,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
)

@Composable
fun HackerAITheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
