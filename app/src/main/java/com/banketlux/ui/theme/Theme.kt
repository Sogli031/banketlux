package com.banketlux.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Sve Material uloge su eksplicitno zadate da nijedna podrazumevana (ljubičasta) boja ne procuri.
private val BanketColorScheme = darkColorScheme(
    primary = BanketGold,
    onPrimary = BanketCharcoal,
    primaryContainer = BanketSurfaceRaised,
    onPrimaryContainer = BanketInk,
    inversePrimary = BanketGoldDark,
    secondary = BanketGoldDark,
    onSecondary = BanketInk,
    secondaryContainer = BanketSurfaceMuted,
    onSecondaryContainer = BanketInk,
    tertiary = BanketGold,
    onTertiary = BanketCharcoal,
    tertiaryContainer = BanketSurfaceMuted,
    onTertiaryContainer = BanketInk,
    background = BanketBackground,
    onBackground = BanketInk,
    surface = BanketBackground,
    onSurface = BanketInk,
    surfaceVariant = BanketSurfaceMuted,
    onSurfaceVariant = BanketTextMuted,
    surfaceTint = BanketGold,
    inverseSurface = BanketInk,
    inverseOnSurface = BanketBackground,
    surfaceBright = BanketSurfaceRaised,
    surfaceDim = BanketBackground,
    surfaceContainerLowest = BanketBackground,
    surfaceContainerLow = BanketSurface,
    surfaceContainer = BanketSurface,
    surfaceContainerHigh = BanketSurfaceMuted,
    surfaceContainerHighest = BanketSurfaceRaised,
    outline = BanketOutline,
    outlineVariant = BanketOutlineVariant,
    error = BanketError,
    onError = BanketCharcoal,
    errorContainer = BanketErrorContainer,
    onErrorContainer = BanketOnErrorContainer,
    scrim = Color.Black
)

@Composable
fun BanketLuxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BanketColorScheme,
        typography = BanketTypography,
        shapes = BanketShapes,
        content = content
    )
}
