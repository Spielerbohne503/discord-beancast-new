package uk.spielerbohne.petodo.domain.text

/**
 * Ein Stück Text: entweder gewöhnlich oder ein Verweis.
 *
 * Die Zerlegung passiert in `domain/`, damit sie ohne Emulator prüfbar ist. Wie ein
 * Verweis aussieht — Farbe, Unterstreichung — entscheidet die Oberfläche.
 */
sealed interface TextSegment {

    data class Plain(val text: String) : TextSegment

    /** [label] ist, was dasteht; [url] ist, wohin es geht. */
    data class Link(val label: String, val url: String) : TextSegment
}

/**
 * Verweise in Titeln und Notizen — dieselbe Schreibweise wie in Markdown-Dateien.
 *
 * Erkannt werden `[Text](https://…)` und nackte `http(s)://…`-Adressen. Mehr nicht:
 * Kein Fettdruck, keine Überschriften, keine Listen. Eine Aufgabenverwaltung, die
 * heimlich zum Markdown-Editor wird, kann am Ende beides halb.
 */
object MarkdownLinks {

    /**
     * `[Text](Adresse)` — Beschriftung ohne Zeilenumbruch, Adresse ohne Leerzeichen.
     *
     * Die Adresse darf **eine Ebene Klammern** enthalten. Ohne diese Ausnahme endet ein
     * Verweis auf `…/Kotlin_(Programmiersprache)` mitten im Wort, weil die erste
     * schließende Klammer als Ende des Verweises gelesen wird — und der abgeschnittene
     * Link führt ins Leere.
     */
    private val MARKDOWN = Regex("""\[([^\]\n]*)\]\(\s*((?:[^()\s]|\([^()\s]*\))+)\s*\)""")

    /** Nackte Adresse. Nur http(s): Alles andere fängt zu viele falsche Treffer. */
    private val BARE = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    /**
     * Zeichen, die am Ende einer nackten Adresse fast immer Satzzeichen sind.
     *
     * „Schau auf https://example.org.“ — der Punkt gehört zum Satz, nicht zur Adresse.
     */
    private const val TRAILING = ".,;:!?»\"'"

    /** Zerlegt [text] in Stücke. Ohne Verweis kommt genau ein [TextSegment.Plain] zurück. */
    fun parse(text: String): List<TextSegment> {
        if (text.isEmpty()) return listOf(TextSegment.Plain(""))

        val segments = mutableListOf<TextSegment>()
        var index = 0

        while (index < text.length) {
            val markdown = MARKDOWN.find(text, index)
            val bare = nextBareUrl(text, index)

            // Der frühere von beiden gewinnt; bei Gleichstand die Markdown-Schreibweise,
            // sonst würde die Adresse in ihrer eigenen Klammer noch einmal gefunden.
            val treffer = when {
                markdown == null && bare == null -> null
                markdown == null -> bare
                bare == null -> markdown
                markdown.range.first <= bare.range.first -> markdown
                else -> bare
            } ?: break

            if (treffer.range.first > index) {
                segments += TextSegment.Plain(text.substring(index, treffer.range.first))
            }

            segments += if (treffer === markdown) {
                val label = treffer.groupValues[1].trim()
                val url = treffer.groupValues[2]
                TextSegment.Link(label = label.ifEmpty { shorten(url) }, url = url)
            } else {
                val url = treffer.value
                TextSegment.Link(label = shorten(url), url = url)
            }

            index = treffer.range.last + 1
        }

        if (index < text.length) segments += TextSegment.Plain(text.substring(index))
        return if (segments.isEmpty()) listOf(TextSegment.Plain(text)) else segments
    }

    /**
     * Nächste nackte Adresse ab [from], um anhängende Satzzeichen gekürzt.
     *
     * Eine Adresse, die in einer Markdown-Klammer steckt, wird übergangen — sie gehört
     * dort hin und wird von [MARKDOWN] mitgenommen.
     */
    private fun nextBareUrl(text: String, from: Int): MatchResult? {
        var treffer = BARE.find(text, from)
        while (treffer != null) {
            val davor = text.getOrNull(treffer.range.first - 1)
            if (davor == '(' && text.lastIndexOf('[', treffer.range.first) >= 0) {
                treffer = treffer.next()
                continue
            }
            return trimTrailing(treffer)
        }
        return null
    }

    private fun trimTrailing(treffer: MatchResult): MatchResult {
        var wert = treffer.value
        var ende = treffer.range.last

        while (wert.isNotEmpty() && (wert.last() in TRAILING || (wert.last() == ')' && !wert.contains('(')))) {
            wert = wert.dropLast(1)
            ende--
        }
        if (wert == treffer.value) return treffer

        return object : MatchResult {
            override val value = wert
            override val range = treffer.range.first..ende
            override val groupValues = listOf(wert)
            override val groups get() = treffer.groups
            override val destructured get() = treffer.destructured
            override fun next() = treffer.next()
        }
    }

    /** Alle Verweise in Reihenfolge ihres Auftretens. */
    fun links(text: String): List<TextSegment.Link> =
        parse(text).filterIsInstance<TextSegment.Link>()

    fun hasLink(text: String): Boolean = links(text).isNotEmpty()

    /**
     * Der Text, wie er in einer Liste stehen soll.
     *
     * Aus `[Reel](https://…)` wird „Reel“, aus einer nackten Adresse wird
     * „instagram.com/reel/…“. Eine Aufgabenzeile, die drei Zeilen Adresse breit ist,
     * verdrängt alles andere vom Bildschirm — genau das soll sie nicht.
     */
    fun plainText(text: String): String = buildString {
        parse(text).forEach { segment ->
            when (segment) {
                is TextSegment.Plain -> append(segment.text)
                is TextSegment.Link -> append(segment.label)
            }
        }
    }.trim()

    /**
     * Kurzform einer Adresse: Rechnername ohne `www.`, dazu höchstens ein Pfadstück.
     *
     * Ohne `java.net.URI` — der gehört nicht zu dem, was `domain/` benutzen darf, und
     * für diesen Zweck reicht Zerlegen an Schrägstrichen.
     */
    fun shorten(url: String): String {
        val ohneSchema = url.substringAfter("://", url)
        val host = ohneSchema.substringBefore('/').removePrefix("www.").ifEmpty { return url }
        val pfad = ohneSchema.substringAfter('/', "")
            .substringBefore('?')
            .substringBefore('#')
            .trim('/')

        if (pfad.isEmpty()) return host

        val erstes = pfad.substringBefore('/')
        val zuLang = erstes.length > MAX_SEGMENT
        val gekuerzt = if (zuLang) erstes.take(MAX_SEGMENT) else erstes

        return when {
            // Es folgen weitere Pfadstücke: Der Schrägstrich vor den Punkten sagt genau das.
            pfad.contains('/') -> "$host/$gekuerzt/…"
            // Nur dieses eine Stück, aber abgeschnitten oder mit Anhängseln.
            zuLang || ohneSchema.contains('?') || ohneSchema.contains('#') -> "$host/$gekuerzt…"
            else -> "$host/$gekuerzt"
        }
    }

    private const val MAX_SEGMENT = 18
}
