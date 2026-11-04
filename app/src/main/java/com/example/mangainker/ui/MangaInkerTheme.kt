package com.example.mangainker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val InkBackground = Color(0xFF0E0E10)
private val InkSurface = Color(0xFF17171A)
private val InkRaised = Color(0xFF202024)
private val InkOutline = Color(0xFF2E2E34)
private val InkPrimaryText = Color(0xFFF2F2F4)
private val InkSecondaryText = Color(0xFF9A9AA3)
private val InkRed = Color(0xFFE0463A)
private val InkSuccess = Color(0xFF3DBE78)
private val InkWarning = Color(0xFFE5A93B)

private val InkColors = darkColorScheme(
    primary = InkRed,
    onPrimary = Color.White,
    secondary = InkRed,
    onSecondary = Color.White,
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
    val accent = InkRed
    val success = InkSuccess
    val warning = InkWarning
}

@Composable
fun MangaInkerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = InkColors, typography = InkTypography, content = content)
}
