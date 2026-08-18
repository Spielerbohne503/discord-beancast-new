package uk.spielerbohne.petodo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Dunkel gedacht, hell mitgeliefert.
 *
 * **Kein Dynamic Color.** Die Systemfarben eines Geräts würden die drei Verläufe
 * überschreiben, an denen man die Bereiche erkennt — dann sieht die App auf jedem Handy
 * anders und auf keinem gut aus. Wer eine eigene Bildsprache hat, gibt sie nicht ab.
 */
private val DarkColors = darkColorScheme(
    primary = Palette.Magenta,
    onPrimary = Palette.Chalk,
    primaryContainer = Palette.MagentaDeep,
    onPrimaryContainer = Palette.Chalk,

    secondary = Palette.Indigo,
    onSecondary = Palette.Chalk,
    secondaryContainer = Palette.IndigoDeep,
    onSecondaryContainer = Palette.Chalk,

    tertiary = Palette.Lime,
    onTertiary = Palette.Ink,
    tertiaryContainer = Palette.InkCard,
    onTertiaryContainer = Palette.Lime,

    background = Palette.Ink,
    onBackground = Palette.Chalk,
    surface = Palette.Ink,
    onSurface = Palette.Chalk,
    surfaceVariant = Palette.InkCard,
    onSurfaceVariant = Palette.ChalkMuted,
    surfaceContainer = Palette.InkElevated,
    surfaceContainerHigh = Palette.InkCard,
    surfaceContainerHighest = Palette.InkCard,
    surfaceContainerLow = Palette.InkElevated,
    surfaceContainerLowest = Palette.Ink,

    outline = Palette.InkBorder,
    outlineVariant = Palette.InkBorder,

    error = Palette.Ember,
    onError = Palette.Chalk,
    errorContainer = Palette.Ember,
    onErrorContainer = Palette.Chalk,
)

private val LightColors = lightColorScheme(
    primary = Palette.MagentaDeep,
    onPrimary = Palette.Chalk,
    primaryContainer = Palette.Magenta,
    onPrimaryContainer = Palette.Chalk,

    secondary = Palette.Indigo,
    onSecondary = Palette.Chalk,
    secondaryContainer = Palette.Indigo,
    onSecondaryContainer = Palette.Chalk,

    tertiary = Palette.PaperInk,
    onTertiary = Palette.Chalk,

    background = Palette.Paper,
    onBackground = Palette.PaperInk,
    surface = Palette.Paper,
    onSurface = Palette.PaperInk,
    surfaceVariant = Palette.PaperCard,
    onSurfaceVariant = Palette.PaperMuted,
    surfaceContainer = Palette.PaperCard,
    surfaceContainerHigh = Palette.PaperCard,
    surfaceContainerHighest = Palette.PaperCard,
    surfaceContainerLow = Palette.PaperCard,
    surfaceContainerLowest = Palette.PaperCard,

    outline = Palette.PaperBorder,
    outlineVariant = Palette.PaperBorder,

    error = Palette.Ember,
    onError = Palette.Chalk,
)

/**
 * Große Radien. Der Unterschied zwischen 12 dp und 28 dp ist der Unterschied zwischen
 * „Formular“ und „Gerät“ — und diese App ist ein Gerät.
 */
private val PetodoShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun PetodoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PetodoTypography,
        shapes = PetodoShapes,
        content = content,
    )
}
