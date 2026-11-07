package com.example.mangainker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val InkBackground = Color(0xFF101114)
private val InkSurface = Color(0xFF191B20)
private val InkRaised = Color(0xFF24272E)
private val InkOutline = Color(0xFF363B45)
private val InkPrimaryText = Color(0xFFF4F1EA)
private val InkSecondaryText = Color(0xFFA5A39D)
private val InkAmber = Color(0xFFF0A34A)
private val InkRed = Color(0xFFE66B65)
private val InkSuccess = Color(0xFF55B987)
private val InkWarning = Color(0xFFE7B45B)

private val InkColors = darkColorScheme(
    primary = InkAmber,
    onPrimary = Color(0xFF211407),
    secondary = InkAmber,
    onSecondary = Color(0xFF211407),
    background = InkBackground,
    onBackground = InkPrimaryText,
    surface = InkSurface,
    onSurface = InkPrimaryText,
    surfaceVariant = InkRaised,
    onSurfaceVariant = InkSecondaryText,
    outline = InkOutline,
    error = InkRed,
    onError = Color.White
)

private val InkTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = InkPrimaryText),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = InkPrimaryText),
    bodyLarge = TextStyle(fontSize = 15.sp, color = InkPrimaryText),
    bodyMedium = TextStyle(fontSize = 15.sp, color = InkPrimaryText),
    bodySmall = TextStyle(fontSize = 13.sp, color = InkSecondaryText),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp)
)

object InkPalette {
    val background = InkBackground
    val surface = InkSurface
    val raised = InkRaised
    val outline = InkOutline
    val primaryText = InkPrimaryText
    val secondaryText = InkSecondaryText
    val accent = InkAmber
    val success = InkSuccess
    val error = InkRed
    val warning = InkWarning
}

@Composable
fun MangaInkerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = InkColors, typography = InkTypography, content = content)
}
