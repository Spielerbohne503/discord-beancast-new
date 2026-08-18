package uk.spielerbohne.petodo.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import uk.spielerbohne.petodo.domain.text.MarkdownLinks
import uk.spielerbohne.petodo.domain.text.TextSegment
import uk.spielerbohne.petodo.ui.theme.Palette

/**
 * Text mit anklickbaren Verweisen — dieselbe Schreibweise wie in Markdown-Dateien.
 *
 * Die Zerlegung macht [MarkdownLinks] in `domain/`; hier wird nur gemalt und der
 * Fingertipp an Android weitergereicht. `LinkAnnotation` erledigt dabei auch die
 * Bedienungshilfen — ein selbstgebauter `clickable`-Bereich täte das nicht.
 */
@Composable
fun LinkedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    linkColor: Color = Palette.Sky,
    maxLines: Int = Int.MAX_VALUE,
    strikeThrough: Boolean = false,
) {
    val annotated = rememberLinkedString(text, linkColor, strikeThrough)

    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textDecoration = if (strikeThrough) TextDecoration.LineThrough else null,
    )
}

@Composable
fun rememberLinkedString(
    text: String,
    linkColor: Color = Palette.Sky,
    strikeThrough: Boolean = false,
): AnnotatedString {
    val pressedColor = MaterialTheme.colorScheme.onSurface

    return remember(text, linkColor, pressedColor, strikeThrough) {
        val styles = TextLinkStyles(
            style = SpanStyle(
                color = linkColor,
                textDecoration = if (strikeThrough) TextDecoration.LineThrough else TextDecoration.Underline,
            ),
            pressedStyle = SpanStyle(color = pressedColor),
        )
        buildAnnotatedString {
            MarkdownLinks.parse(text).forEach { segment ->
                when (segment) {
                    is TextSegment.Plain -> append(segment.text)
                    is TextSegment.Link -> withLink(
                        LinkAnnotation.Url(url = segment.url, styles = styles)
                    ) {
                        append(segment.label)
                    }
                }
            }
        }
    }
}
