package dev.jellyschedule.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

object JellyColors {
    val Background = Color(0xFF0B0F17)
    val Surface = Color(0xFF151B28)
    val SurfaceVariant = Color(0xFF1F2737)
    val Primary = Color(0xFF8B6CFF)
    val PrimaryDark = Color(0xFF5B3FE0)
    val OnPrimary = Color(0xFFFFFFFF)
    val Text = Color(0xFFF2F4F8)
    val Muted = Color(0xFFA5ADBF)
    val Live = Color(0xFFE5484D)
    val Movie = Color(0xFF3B82F6)
    val ReRun = Color(0xFF64748B)
    val Ok = Color(0xFF22C55E)
    val Warn = Color(0xFFF59E0B)
    val Rec = Color(0xFFEF4444)
    val Border = Color(0x33FFFFFF)
}

/** Text sizes for a 10-foot UI: body text is at least 18 sp at 1080p. */
private val JellyTypography = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold, lineHeight = 72.sp),
    displayMedium = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold, lineHeight = 56.sp),
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 48.sp),
    headlineLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = 38.sp),
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleMedium = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodySmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    labelMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    labelSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
)

@Composable
fun JellyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = JellyColors.Primary,
            onPrimary = JellyColors.OnPrimary,
            primaryContainer = JellyColors.PrimaryDark,
            onPrimaryContainer = JellyColors.OnPrimary,
            secondary = JellyColors.Movie,
            onSecondary = JellyColors.OnPrimary,
            background = JellyColors.Background,
            onBackground = JellyColors.Text,
            surface = JellyColors.Surface,
            onSurface = JellyColors.Text,
            surfaceVariant = JellyColors.SurfaceVariant,
            onSurfaceVariant = JellyColors.Muted,
            error = JellyColors.Live,
            onError = JellyColors.OnPrimary,
            border = JellyColors.Border,
        ),
        typography = JellyTypography,
        content = content,
    )
}
