package com.banketlux.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Sve Material uloge su eksplicitno zadate da nijedna podrazumevana (ljubičasta) boja ne procuri.
private val BanketColorScheme = darkColorScheme(
    primary = BanketGold,
    onPrimary = BanketCharcoal,
    primaryContainer = BanketSurfaceHigh,
    onPrimaryContainer = BanketInk,
    inversePrimary = BanketGoldDark,
    secondary = BanketGoldDark,
    onSecondary = BanketCharcoal,
    secondaryContainer = BanketGoldContainer,
    onSecondaryContainer = BanketInk,
    tertiary = BanketGold,
    onTertiary = BanketCharcoal,
    tertiaryContainer = BanketSurfaceHigh,
    onTertiaryContainer = BanketInk,
    background = BanketBackground,
    onBackground = BanketInk,
    surface = BanketBackground,
    onSurface = BanketInk,
    surfaceVariant = BanketSurfaceHigh,
    onSurfaceVariant = BanketTextMuted,
    surfaceTint = BanketGold,
    inverseSurface = BanketInk,
    inverseOnSurface = BanketBackground,
    surfaceBright = BanketSurfaceHighest,
    surfaceDim = BanketBackground,
    surfaceContainerLowest = BanketSurfaceLowest,
    surfaceContainerLow = BanketSurfaceLow,
    surfaceContainer = BanketSurfaceContainer,
    surfaceContainerHigh = BanketSurfaceHigh,
    surfaceContainerHighest = BanketSurfaceHighest,
    outline = BanketOutline,
    outlineVariant = BanketOutlineVariant,
    error = BanketError,
    onError = BanketCharcoal,
    errorContainer = BanketErrorContainer,
    onErrorContainer = BanketOnErrorContainer,
    scrim = Color.Black
)

/**
 * Tema je UVEK tamna, po dizajnu: brend je tamnoplava + zlato, pa nema svetle šeme,
 * ne prati sistemsku temu (isSystemInDarkTheme) i ne koristi dinamičku boju
 * (dynamicDarkColorScheme). Zato i Activity traži tamne sistemske trake.
 */
@Composable
fun BanketLuxTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBanketExtendedColors provides BanketDarkExtendedColors) {
        MaterialTheme(
            colorScheme = BanketColorScheme,
            typography = BanketTypography,
            shapes = BanketShapes,
            content = content
        )
    }
}
