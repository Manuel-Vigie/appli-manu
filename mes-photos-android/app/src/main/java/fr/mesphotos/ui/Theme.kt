package fr.mesphotos.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF14705A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4E8D8),
    onPrimaryContainer = Color(0xFF05281F),
    secondary = Color(0xFF9A6612),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCEBC8),
    onSecondaryContainer = Color(0xFF3A2600),
    background = Color(0xFFE8EFEB),
    onBackground = Color(0xFF1B2723),
    surface = Color(0xFFF8FBF9),
    onSurface = Color(0xFF1B2723),
    surfaceVariant = Color(0xFFE4E9E4),
    onSurfaceVariant = Color(0xFF4A5A54),
    outline = Color(0xFF8A9A93),
    outlineVariant = Color(0xFFD5DDD8),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFBE0DA),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD2B1),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF0F4F40),
    onPrimaryContainer = Color(0xFFC3F2E0),
    secondary = Color(0xFFEBC46E),
    onSecondary = Color(0xFF3A2600),
    secondaryContainer = Color(0xFF4B3A10),
    onSecondaryContainer = Color(0xFFFCEBC8),
    background = Color(0xFF0F1613),
    onBackground = Color(0xFFE3EAE6),
    surface = Color(0xFF18211D),
    onSurface = Color(0xFFE3EAE6),
    surfaceVariant = Color(0xFF2A3630),
    onSurfaceVariant = Color(0xFFB5C3BC),
    outline = Color(0xFF7F8F88),
    outlineVariant = Color(0xFF34423C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF410E0B),
    errorContainer = Color(0xFF5C1A15),
    onErrorContainer = Color(0xFFFFDAD5),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/** Les deux couleurs du bandeau en haut de chaque écran (dégradé vert profond vers vert clair). */
@Composable
fun heroColors(): List<Color> =
    if (isSystemInDarkTheme()) listOf(Color(0xFF0B3D31), Color(0xFF1E7058))
    else listOf(Color(0xFF0E5C49), Color(0xFF2E9A7A))

/** Clair ou sombre selon le réglage du téléphone. */
@Composable
fun MesPhotosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
