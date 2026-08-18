package uk.spielerbohne.petodo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Die Schriftskala.
 *
 * Keine mitgelieferte Schriftdatei — das wären zusätzliche Bibliotheken bzw. Ballast im
 * APK. Stattdessen wird die Systemschrift streng geführt: große Zahlen mit enger
 * Laufweite, kleine Beschriftungen mit weiter. Der Kontrast zwischen beiden trägt das
 * Bild, nicht die Schriftart.
 */
private val Tight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

internal val PetodoTypography = Typography().let { base ->
    base.copy(
        displayLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 64.sp,
            lineHeight = 66.sp,
            letterSpacing = (-2.5).sp,
            lineHeightStyle = Tight,
        ),
        displayMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 52.sp,
            lineHeight = 54.sp,
            letterSpacing = (-2).sp,
            lineHeightStyle = Tight,
        ),
        displaySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 38.sp,
            lineHeight = 42.sp,
            letterSpacing = (-1.2).sp,
            lineHeightStyle = Tight,
        ),
        headlineMedium = base.headlineMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.8).sp,
        ),
        headlineSmall = base.headlineSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
        ),
        titleLarge = base.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
        ),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(letterSpacing = (-0.1).sp),
        // Die kleinen Beschriftungen laufen weit — so lesen sie sich als Beschriftung
        // und nicht als kleingedruckter Fließtext.
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.9.sp),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp),
    )
}
