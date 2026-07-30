package de.baseline.nutrition.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA7E8C1),
    onPrimary = Color(0xFF073821),
    background = Color(0xFF0B0F0D),
    onBackground = Color(0xFFE4EAE6),
    surface = Color(0xFF111714),
    onSurface = Color(0xFFE4EAE6),
    surfaceVariant = Color(0xFF202923),
    onSurfaceVariant = Color(0xFFBFC9C2),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B43),
    onPrimary = Color.White,
    background = Color(0xFFF5FAF6),
    onBackground = Color(0xFF151A17),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151A17),
    surfaceVariant = Color(0xFFE2EAE4),
    onSurfaceVariant = Color(0xFF414942),
)

object BaselineSpacing {
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val extraLarge = 32.dp
}

object BaselineShapes {
    val glass = RoundedCornerShape(28.dp)
    val input = RoundedCornerShape(18.dp)
}

@Composable
fun BaselineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}

