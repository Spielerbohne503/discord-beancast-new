package uk.spielerbohne.petodo.domain.text

/** Was aus einem geteilten Inhalt wird. */
data class SharedTask(val title: String, val note: String? = null)

/**
 * „Teilen an PeTodo“ — aus dem, was eine andere App schickt, wird eine Aufgabe.
 *
 * Android schickt beim Teilen zwei Felder: einen Betreff und einen Text. Was darin steht,
 * ist von App zu App verschieden — mal nur eine Adresse, mal Titel plus Adresse, mal
 * mehrere Absätze. Hier steht die Entscheidung, was davon Titel und was Notiz wird;
 * die Activity führt sie nur aus.
 *
 * Der schöne Fall: Betreff **und** eine Adresse ergeben `[Betreff](Adresse)`. In der Liste
 * steht dann der Titel des Videos und nicht dreißig Zeichen Kennung, und angetippt geht
 * er trotzdem auf.
 */
object ShareCapture {

    fun capture(subject: String?, text: String?): SharedTask? {
        val betreff = subject?.clean()
        val inhalt = text?.trim()?.takeIf { it.isNotEmpty() }

        if (betreff == null && inhalt == null) return null
        if (inhalt == null) return SharedTask(title = betreff!!)

        // Eine einzelne Adresse: Der Betreff ist dann ihre Beschriftung.
        alleinstehendeAdresse(inhalt)?.let { adresse ->
            return if (betreff != null && betreff != adresse) {
                SharedTask(title = "[$betreff]($adresse)")
            } else {
                SharedTask(title = adresse)
            }
        }

        // Freitext mit Betreff: Der Betreff ist der Titel, der Rest die Notiz.
        if (betreff != null) return SharedTask(title = betreff, note = inhalt)

        // Freitext ohne Betreff: Die erste Zeile trägt, der Rest wandert in die Notiz.
        val zeilen = inhalt.lines()
        val erste = zeilen.firstOrNull { it.isNotBlank() }?.clean() ?: return null
        val rest = zeilen
            .dropWhile { it.isBlank() }
            .drop(1)
            .joinToString("\n")
            .trim()
            .takeIf { it.isNotEmpty() }

        return SharedTask(title = erste, note = rest)
    }

    /** Gibt die Adresse zurück, wenn der Text aus nichts anderem besteht. */
    private fun alleinstehendeAdresse(text: String): String? {
        val stuecke = MarkdownLinks.parse(text)
        val einziges = stuecke.singleOrNull() as? TextSegment.Link ?: return null
        return einziges.url
    }

    /** Ein Titel ist einzeilig — geteilte Betreffs sind das nicht immer. */
    private fun String.clean(): String? =
        replace(Regex("""\s+"""), " ").trim().takeIf { it.isNotEmpty() }
}
