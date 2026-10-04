package com.banketlux.ui.theme

import androidx.compose.ui.graphics.Color

// Sirova paleta je internal: ekrani i komponente čitaju boje isključivo preko
// MaterialTheme.colorScheme i MaterialTheme.extendedColors, nikad direktno odavde.

// Tamna paleta: tamnoplava pozadina, diskretno zlato kao jedini akcenat.
internal val BanketCharcoal = Color(0xFF171717)
internal val BanketGold = Color(0xFFD7B16A)
internal val BanketGoldDark = Color(0xFF9F7B35)
internal val BanketGoldContainer = Color(0xFF3A3220)    // indikator aktivne stavke, izabran red

// Tonske stepenice površina; svaka je primetno svetlija od prethodne.
internal val BanketBackground = Color(0xFF0B161D)       // pozadina ekrana (surface)
internal val BanketSurfaceLowest = Color(0xFF081219)
internal val BanketSurfaceLow = Color(0xFF12222C)       // kartice
internal val BanketSurfaceContainer = Color(0xFF172B37) // donja navigacija, meniji
internal val BanketSurfaceHigh = Color(0xFF1C3342)      // dijalozi, čipovi, hero kartica
internal val BanketSurfaceHighest = Color(0xFF22394A)

// outline je granica kontrole (>= 3:1 na svim površinama); outlineVariant je samo dekor.
internal val BanketOutline = Color(0xFF71848E)
internal val BanketOutlineVariant = Color(0xFF2E4452)

internal val BanketInk = Color(0xFFF4EFE5)              // glavni tekst
internal val BanketTextMuted = Color(0xFFB3AB9E)        // sekundarni tekst i ikonice

internal val BanketError = Color(0xFFE8856B)
internal val BanketErrorContainer = Color(0xFF3A231C)
internal val BanketOnErrorContainer = Color(0xFFFFD6CC)

// Proširene (ne-Material) uloge: info, uspeh, upozorenje — vidi ExtendedColors.kt.
internal val BanketWarning = Color(0xFFE3A25C)
internal val BanketWarningContainer = Color(0xFF3D2A12)
internal val BanketOnWarningContainer = Color(0xFFF3C98B)
internal val BanketSuccess = Color(0xFF8FD19E)
internal val BanketSuccessContainer = Color(0xFF1C3524)
internal val BanketInfo = Color(0xFF8FBBD9)
internal val BanketInfoContainer = Color(0xFF1A2E3C)
