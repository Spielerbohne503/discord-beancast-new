package uk.spielerbohne.petodo.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Die Farben der App an einer Stelle.
 *
 * Zwei Regeln, die das Bild zusammenhalten:
 *
 *  1. **Die Fläche ist fast schwarz, die Farbe steckt in den Karten.** Ein dunkler Grund
 *     ohne Eigenfarbe lässt Verläufe leuchten; ein grauer Grund zieht sie herunter.
 *  2. **Jeder Bereich hat genau einen Verlauf.** Pet ist Magenta, Fokus ist Indigo,
 *     Überfälliges ist Bernstein. Wer eine vierte Farbe einführt, nimmt den drei
 *     vorhandenen ihre Bedeutung.
 */
object Palette {

    // ------------------------------------------------------------------- Grund und Karten

    /** Der Grund. Nicht reines Schwarz — ein Hauch Blau nimmt dem Bild die Härte. */
    val Ink = Color(0xFF06060A)
    val InkElevated = Color(0xFF0E0E15)
    val InkCard = Color(0xFF13131C)
    val InkBorder = Color(0xFF23232F)

    val Chalk = Color(0xFFF4F4F8)
    val ChalkMuted = Color(0xFF9A9AAE)

    // ------------------------------------------------------------------------ Pet: Magenta

    val Magenta = Color(0xFFFF3D8A)
    val MagentaDeep = Color(0xFFB026FF)
    val Violet = Color(0xFF6F2DFF)

    // ---------------------------------------------------------------------- Fokus: Indigo

    val Indigo = Color(0xFF4C5BFF)
    val IndigoDeep = Color(0xFF1B1F63)
    val Sky = Color(0xFF35D0F0)

    // ----------------------------------------------------------------- Überfällig: Bernstein

    val Amber = Color(0xFFFF8A3D)
    val Ember = Color(0xFFE0264F)

    /** Erledigtes und Serien — der einzige Akzent, der nicht aus einem Verlauf kommt. */
    val Lime = Color(0xFFC8FF52)

    // ------------------------------------------------------------------------- Helles Thema

    val Paper = Color(0xFFF6F5FA)
    val PaperCard = Color(0xFFFFFFFF)
    val PaperBorder = Color(0xFFE2E1EC)
    val PaperInk = Color(0xFF14131B)
    val PaperMuted = Color(0xFF5B5A6B)
}
