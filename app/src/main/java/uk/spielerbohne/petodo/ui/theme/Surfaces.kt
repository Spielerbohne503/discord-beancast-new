package uk.spielerbohne.petodo.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Die Bausteine, aus denen jeder Bildschirm besteht.
 *
 * Sie stehen hier und nicht in den einzelnen Screens, damit eine Karte überall gleich
 * aussieht. Sobald eine Fläche irgendwo „nur ein bisschen anders“ gebaut wird, zerfällt
 * das Bild in Einzelteile.
 */

/** Die drei Bereichsverläufe. Mehr gibt es nicht — siehe [Palette]. */
object Brand {

    /** Pet: Magenta über Violett. Die Farbe, an der man den Begleiter erkennt. */
    val Pet = listOf(Palette.Magenta, Palette.MagentaDeep, Palette.Violet)

    /** Fokus: tiefes Indigo mit einem kalten Rand. Ruhig, damit man daneben arbeiten kann. */
    val Focus = listOf(Palette.Indigo, Palette.IndigoDeep)

    /** Überfälliges: Bernstein nach Rot. Dringlich, ohne zu schreien. */
    val Overdue = listOf(Palette.Amber, Palette.Ember)

    /** Ein kalter Verlauf für Zahlen und Fortschritt. */
    val Cool = listOf(Palette.Sky, Palette.Indigo)

    fun brush(colors: List<Color>): Brush =
        Brush.linearGradient(colors = colors, start = Offset.Zero, end = Offset.Infinite)
}

/**
 * Die farbige Karte: Verlauf, große Rundung, ein heller Lichtsaum an der Oberkante.
 *
 * Der Saum ist der Unterschied zwischen „farbiges Rechteck“ und „Fläche mit Licht“. Er
 * kostet einen Verlauf mehr und trägt das halbe Bild.
 */
@Composable
fun GradientCard(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brand.brush(animateGradient(colors)))
            .background(
                Brush.verticalGradient(
                    // Oben ein Hauch Licht, unten ein Hauch Tiefe.
                    colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, Color.Black.copy(alpha = 0.22f)),
                )
            )
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        content = content,
    )
}

/**
 * Die stille Karte: fast schwarz, ein Millimeter Rand.
 *
 * Ohne den Rand verschwimmt sie auf dunklem Grund mit allem anderen; mit einer Erhöhung
 * (`tonalElevation`) würde sie grau und nähme dem Verlauf daneben die Leuchtkraft.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    onClick: (() -> Unit)? = null,
    /** Hereingereicht, wenn der Aufrufer auf das Drücken reagieren will. */
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape)
            .let { basis ->
                if (onClick == null) {
                    basis
                } else {
                    basis.clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick,
                    )
                }
            },
        content = content,
    )
}

/**
 * Abschnittsüberschrift: klein, versal, weit laufend, daneben die Anzahl.
 *
 * Gesperrte Versalien lesen sich als Beschriftung und drängeln sich nicht vor die
 * Aufgaben darunter — anders als eine fette Zwischenüberschrift.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 12.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        trailing?.let { Row(verticalAlignment = Alignment.CenterVertically, content = it) }
    }
}

/** Die Anzahl neben einer Überschrift — als Plakette, nicht als lose Ziffer. */
@Composable
fun CountBadge(count: Int, accent: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
        )
    }
}

/** Runder Knopf auf dunklem Grund — die Grundform aller Nebenhandlungen. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
    }
}

/**
 * Ein Wertebalken.
 *
 * Bewusst kein `LinearProgressIndicator`: Der bringt eine eigene Höhe, eine eigene
 * Rundung und eine eigene Spurfarbe mit, und drei davon nebeneinander sehen aus wie drei
 * Ladebalken. Hier ist es eine Spur mit einem leuchtenden Faden darin.
 */
@Composable
fun ValueTrack(
    fraction: Float,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = Color.White.copy(alpha = 0.14f),
    /**
     * Ob der Balken zu seinem neuen Stand wächst.
     *
     * Das ist die wichtigste Bewegung der App: Man hakt etwas ab und *sieht*, wie das
     * Pet davon etwas hat. Springt der Balken, ist der Zusammenhang weg — dann hat sich
     * eben eine Zahl geändert.
     */
    animate: Boolean = true,
) {
    val ziel = fraction.coerceIn(0f, 1f)
    val breite by animateFloatAsState(
        targetValue = ziel,
        animationSpec = if (animate) Motion.slow() else snap(),
        label = "balken",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(breite)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Brush.horizontalGradient(colors))
        )
    }
}

/**
 * Der Lichtschein hinter dem Inhalt.
 *
 * Ein einzelner weicher Kreis in der Bereichsfarbe, ganz hinten. Er ersetzt das, was in
 * der Vorlage ein unscharfes Foto leistet: Der schwarze Grund wirkt nicht mehr leer.
 */
@Composable
fun ScreenGlow(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    alpha: Float = 0.30f,
    /**
     * Ob der Schein langsam heller und dunkler wird.
     *
     * Nur für den Pet-Bildschirm gedacht. Eine dauerhafte Animation hält die Bilderfolge
     * am Laufen und kostet Strom — auf der Heute-Liste, die minutenlang offen liegt, wäre
     * das verschwendet. Beim Pet ist es das Einzige, was es überhaupt lebendig macht:
     * v1 hat kein einziges Sprite.
     */
    breathing: Boolean = false,
) {
    val atmen = if (breathing && animationsEnabled()) {
        val takt = rememberInfiniteTransition(label = "atem")
        val wert by takt.animateFloat(
            initialValue = 0.75f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(BREATH_MILLIS, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "atemStaerke",
        )
        wert
    } else {
        1f
    }

    val tint = colors.first().copy(alpha = (alpha * atmen).coerceIn(0f, 1f))
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(tint, Color.Transparent),
                    center = Offset(x = 220f, y = 40f),
                    radius = 900f,
                )
            )
    )
}

/** Ein Streifen, der eine Karte oben abschließt — für Listenfarben und Prioritäten. */
@Composable
fun AccentBar(color: Color, modifier: Modifier = Modifier, width: Dp = 3.dp) {
    Box(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp))
            .background(color)
    )
}

/**
 * Ein Verlauf, der seine Farbe nicht springt, sondern wechselt.
 *
 * Der Anlass ist das Pet: Wenn es von „gesund“ nach „geschwächt“ rutscht, wäre ein
 * harter Farbwechsel ein Fehler-Blitz. So sieht man dem Bild an, dass etwas gekippt ist,
 * ohne erschreckt zu werden.
 *
 * Genau drei Farben werden bewegt, nicht die ganze Liste: Ein Verlauf mit zwei Farben und
 * einer mit dreien sind unterschiedlich lang, und wechselnd viele Animationen in einer
 * Komposition sind kein gültiger Compose-Code.
 */
@Composable
private fun animateGradient(colors: List<Color>): List<Color> {
    val (anfang, mitte, ende) = colors.asThree()

    val a by animateColorAsState(anfang, Motion.slow(), label = "verlaufAnfang")
    val b by animateColorAsState(mitte, Motion.slow(), label = "verlaufMitte")
    val c by animateColorAsState(ende, Motion.slow(), label = "verlaufEnde")

    return listOf(a, b, c)
}

/** Zwei Farben bekommen ihre Mitte gerechnet, mehr als drei werden auf drei gedünnt. */
private fun List<Color>.asThree(): Triple<Color, Color, Color> = when {
    isEmpty() -> Triple(Color.Transparent, Color.Transparent, Color.Transparent)
    size == 1 -> Triple(first(), first(), first())
    size == 2 -> Triple(first(), lerp(first(), last(), 0.5f), last())
    else -> Triple(first(), this[size / 2], last())
}

/** Ein voller Atemzug. Langsam genug, dass man ihn nicht als Animation liest. */
private const val BREATH_MILLIS = 5_200
