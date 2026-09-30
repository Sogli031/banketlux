package com.banketlux.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.banketlux.R

// Fontovi se nalaze u res/font/ (vidi README u handoff paketu).
val BanketSans = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold)
)

/** Monospace za datume i iznose — cifre iste širine, lakše poređenje. */
val BanketMono = FontFamily(
    Font(R.font.geistmono_regular, FontWeight.Normal),
    Font(R.font.geistmono_medium, FontWeight.Medium)
)

/** Zlatni naslov sekcije: sitna verzalna slova sa razmakom. */
val BanketLabelCaps = TextStyle(
    fontFamily = BanketSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 1.3.sp
)

val BanketTypography = Typography(
    displaySmall = TextStyle(fontFamily = BanketSans, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = BanketSans, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = BanketSans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = BanketSans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = BanketSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = BanketSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = BanketSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = BanketSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = BanketSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = BanketSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = BanketSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
    // Iznosi: monospace, kao i do sada, samo drugim pismom.
    headlineLarge = TextStyle(fontFamily = BanketMono, fontWeight = FontWeight.Medium, fontSize = 34.sp, lineHeight = 40.sp),
    headlineSmall = TextStyle(fontFamily = BanketMono, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 36.sp)
)
