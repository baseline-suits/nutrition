package de.baseline.nutrition.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB77AEB),
    onPrimary = Color(0xFF21102E),
    primaryContainer = Color(0xFF342044),
    onPrimaryContainer = Color(0xFFE9D2FF),
    secondary = Color(0xFFF1C96A),
    onSecondary = Color(0xFF2B2105),
    background = Color(0xFF0D0D12),
    onBackground = Color(0xFFF2EFF5),
    surface = Color(0xFF17171F),
    onSurface = Color(0xFFF2EFF5),
    surfaceVariant = Color(0xFF23232D),
    onSurfaceVariant = Color(0xFFBDB8C5),
    outline = Color(0xFF3A3743),
    outlineVariant = Color(0xFF292731),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF4A1E22),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7641A5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEDBFF),
    onPrimaryContainer = Color(0xFF2A0A42),
    secondary = Color(0xFF735C00),
    background = Color(0xFFF8F5FA),
    onBackground = Color(0xFF1C1920),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1920),
    surfaceVariant = Color(0xFFF0EAF4),
    onSurfaceVariant = Color(0xFF625B66),
    outline = Color(0xFF817681),
    outlineVariant = Color(0xFFD8CEDA),
)

private val BaselineTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Normal),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

object BaselineSpacing {
    val tiny = 4.dp
    val small = 8.dp
    val compact = 12.dp
    val medium = 16.dp
    val screen = 20.dp
    val large = 24.dp
    val extraLarge = 32.dp
}

object BaselineShapes {
    val card = RoundedCornerShape(20.dp)
    val compactCard = RoundedCornerShape(16.dp)
    val input = RoundedCornerShape(14.dp)
    val button = RoundedCornerShape(18.dp)
    val pill = RoundedCornerShape(50)
    val glass = card
}

@Composable
fun BaselineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = BaselineTypography,
        content = content,
    )
}
