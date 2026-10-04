package com.banketlux.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Par boja za semantički ton: pozadina i tekst/ikonica koji stoje na njoj. */
@Immutable
data class ToneColors(val container: Color, val onContainer: Color)

/**
 * Uloge koje Material šema nema (info, uspeh, upozorenje). Greška ostaje u
 * colorScheme.error / errorContainer. Čitaju se preko [MaterialTheme.extendedColors].
 */
@Immutable
class BanketExtendedColors(
    val info: ToneColors,
    val success: ToneColors,
    val warning: ToneColors
)

internal val BanketDarkExtendedColors = BanketExtendedColors(
    info = ToneColors(BanketInfoContainer, BanketInfo),
    success = ToneColors(BanketSuccessContainer, BanketSuccess),
    warning = ToneColors(BanketWarningContainer, BanketOnWarningContainer)
)

internal val LocalBanketExtendedColors = staticCompositionLocalOf { BanketDarkExtendedColors }

val MaterialTheme.extendedColors: BanketExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalBanketExtendedColors.current
