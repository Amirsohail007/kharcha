package com.amir.expense.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.amir.expense.R

/** Brand indigo. The hero card keeps it in both modes so the headline number always sits on the same color. */
val HeroIndigo = Color(0xFF3B3BCF)
val HeroOver = Color(0xFFD93A3F)

private val LightColors = lightColorScheme(
    primary = HeroIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E4FB),
    onPrimaryContainer = Color(0xFF1C1C6E),
    secondaryContainer = Color(0xFFE4E4FB),
    onSecondaryContainer = Color(0xFF1C1C6E),
    tertiaryContainer = Color(0xFFFFF1DE),
    onTertiaryContainer = Color(0xFF7A3E00),
    background = Color(0xFFF4F5FA),
    onBackground = Color(0xFF14162B),
    surface = Color(0xFFF4F5FA),
    onSurface = Color(0xFF14162B),
    surfaceVariant = Color(0xFFECEEF5),
    onSurfaceVariant = Color(0xFF5D6178),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFECEEF5),
    outline = Color(0xFFC9CCDA),
    outlineVariant = Color(0xFFE6E8F0),
    error = Color(0xFFD93A3F),
    errorContainer = Color(0xFFFDE7E8),
    onErrorContainer = Color(0xFF8C1D20),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA3A3FF),
    onPrimary = Color(0xFF15155A),
    primaryContainer = Color(0xFF2B2B7A),
    onPrimaryContainer = Color(0xFFE1E1FF),
    secondaryContainer = Color(0xFF2B2B7A),
    onSecondaryContainer = Color(0xFFE1E1FF),
    tertiaryContainer = Color(0xFF3A2A12),
    onTertiaryContainer = Color(0xFFFFCF8A),
    background = Color(0xFF0E0F1A),
    onBackground = Color(0xFFECEDF7),
    surface = Color(0xFF0E0F1A),
    onSurface = Color(0xFFECEDF7),
    surfaceVariant = Color(0xFF23253A),
    onSurfaceVariant = Color(0xFFA3A7C2),
    surfaceContainerLowest = Color(0xFF181929),
    surfaceContainerLow = Color(0xFF181929),
    surfaceContainer = Color(0xFF181929),
    surfaceContainerHigh = Color(0xFF1F2033),
    surfaceContainerHighest = Color(0xFF26283E),
    outline = Color(0xFF3B3E58),
    outlineVariant = Color(0xFF2A2C42),
    error = Color(0xFFFF6B6E),
    errorContainer = Color(0xFF4A1D20),
    onErrorContainer = Color(0xFFFFD9DA),
)

private fun jakarta(weight: Int) =
    Font(R.font.plus_jakarta_sans, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

private val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

private val AppTypography = Typography().run {
    fun TextStyle.j(weight: Int? = null) = copy(fontFamily = Jakarta, fontWeight = weight?.let(::FontWeight) ?: fontWeight)
    Typography(
        displayLarge = displayLarge.j(700), displayMedium = displayMedium.j(700), displaySmall = displaySmall.j(700),
        headlineLarge = headlineLarge.j(700), headlineMedium = headlineMedium.j(700), headlineSmall = headlineSmall.j(700),
        titleLarge = titleLarge.j(700).copy(fontSize = 24.sp), titleMedium = titleMedium.j(600), titleSmall = titleSmall.j(600),
        bodyLarge = bodyLarge.j(), bodyMedium = bodyMedium.j(), bodySmall = bodySmall.j(),
        labelLarge = labelLarge.j(600), labelMedium = labelMedium.j(600), labelSmall = labelSmall.j(500),
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

/** Money coming in. */
val successColor: Color
    @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xFF4ADE80) else Color(0xFF15803D)
