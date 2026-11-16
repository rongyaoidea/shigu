package com.shigu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ShiguPalette {
    val Paper = Color(0xFFFBF8F2)
    val Canvas = Color(0xFFF1ECE2)
    val Ink = Color(0xFF23201C)
    val InkSoft = Color(0xFF6E675E)
    val InkFaint = Color(0xFFB9B0A3)
    val Well = Color(0xFFEAE4D8)
    val Highlighter = Color(0xFFFFE9A8)
    val Up = Color(0xFFD8453A)
    val UpInk = Color(0xFFB93A31)
    val Down = Color(0xFF2E7D5B)
    val DownInk = Color(0xFF256B4D)
    val Alert = Color(0xFFB3261E)
    val White = Color(0xFFFFFFFF)
}

private val Scheme = lightColorScheme(
    primary = ShiguPalette.Ink,
    onPrimary = ShiguPalette.Paper,
    primaryContainer = ShiguPalette.Ink,
    onPrimaryContainer = ShiguPalette.Paper,
    secondary = ShiguPalette.InkSoft,
    onSecondary = ShiguPalette.Paper,
    secondaryContainer = ShiguPalette.Well,
    onSecondaryContainer = ShiguPalette.Ink,
    background = ShiguPalette.Paper,
    onBackground = ShiguPalette.Ink,
    surface = ShiguPalette.Paper,
    onSurface = ShiguPalette.Ink,
    surfaceVariant = ShiguPalette.Well,
    onSurfaceVariant = ShiguPalette.InkSoft,
    outline = ShiguPalette.InkFaint,
    outlineVariant = ShiguPalette.InkFaint,
    error = ShiguPalette.Alert,
)

private val ShiguTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

@Composable
fun ShiguTheme(content: @Composable () -> Unit) {
    // 纸墨是浅色语言；不跟随系统深色。
    MaterialTheme(
        colorScheme = Scheme,
        typography = ShiguTypography,
        content = content,
    )
}

object ShiguDimens {
    val screenPadding = 16.dp
    val cardGap = 10.dp
    val cardRadius = 14.dp
    val buttonHeight = 48.dp
    val touch = 44.dp
}

object ShiguMotion {
    const val SPRING_DAMPING = 0.86f
    const val SPRING_STIFFNESS = 900f
    const val SWAP_BLUR_MS = 140
    const val QUICK_MS = 170
}
