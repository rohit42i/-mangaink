package com.example.mangainker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val InkBackground = Color(0xFF0E0F12)
private val InkSurface = Color(0xFF17181D)
private val InkRaised = Color(0xFF20232A)
private val InkOutline = Color(0xFF303740)
private val InkPrimaryText = Color(0xFFF2F2F4)
private val InkSecondaryText = Color(0xFF9AA4AE)
private val InkCyan = Color(0xFF4FD1C5)
private val InkRed = Color(0xFFF2555A)
private val InkSuccess = Color(0xFF3DBE78)
private val InkWarning = Color(0xFFF5B849)

private val InkColors = darkColorScheme(
    primary = InkCyan,
    onPrimary = Color(0xFF071311),
    secondary = InkCyan,
    onSecondary = Color(0xFF071311),
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
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, color = InkPrimaryText),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = InkPrimaryText),
    bodyLarge = TextStyle(fontSize = 15.sp, color = InkPrimaryText),
    bodyMedium = TextStyle(fontSize = 15.sp, color = InkPrimaryText),
    bodySmall = TextStyle(fontSize = 13.sp, color = InkSecondaryText),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
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
    val accent = InkCyan
    val success = InkSuccess
    val error = InkRed
    val warning = InkWarning
}

@Composable
fun MangaInkerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = InkColors, typography = InkTypography, content = content)
}
