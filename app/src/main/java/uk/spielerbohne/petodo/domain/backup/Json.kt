package uk.spielerbohne.petodo.domain.backup

/**
 * Ein kleiner JSON-Leser und -Schreiber.
 *
 * Warum von Hand statt einer Bibliothek: Der Projektplan lässt keine weiteren
 * Abhängigkeiten zu, und `org.json` aus dem Android-Framework wäre in `domain/` verboten
 * und im JVM-Unit-Test nur eine Attrappe. Diese Fassung ist reines Kotlin und damit
 * vollständig testbar — genau das, was das Sicherungsformat braucht.
 *
 * Zahlen werden als Rohtext gehalten, damit Millisekunden-Zeitstempel nicht über einen
 * `Double` laufen und dabei Genauigkeit verlieren.
 */
sealed interface JsonValue {

    data class Text(val value: String) : JsonValue

    /** Zahl als Literal, damit `Long` exakt bleibt. */
    data class Number(val literal: String) : JsonValue {
        fun asLong(): Long? = literal.toLongOrNull()
        fun asDouble(): Double? = literal.toDoubleOrNull()
    }

    data class Bool(val value: Boolean) : JsonValue

    data object Null : JsonValue

    data class Array(val items: List<JsonValue>) : JsonValue

    data class Object(val entries: Map<String, JsonValue>) : JsonValue {
        operator fun get(key: String): JsonValue? = entries[key]?.takeIf { it != Null }

        fun string(key: String): String? = (this[key] as? Text)?.value
        fun long(key: String): Long? = (this[key] as? Number)?.asLong()
        fun int(key: String): Int? = long(key)?.toInt()
        fun double(key: String): Double? = (this[key] as? Number)?.asDouble()
        fun boolean(key: String): Boolean? = when (val value = this[key]) {
            is Bool -> value.value
            // Ältere Sicherungen könnten 0/1 geschrieben haben.
            is Number -> value.asLong()?.let { it != 0L }
            else -> null
        }

        fun array(key: String): List<JsonValue> = (this[key] as? Array)?.items.orEmpty()
        fun objects(key: String): List<Object> = array(key).filterIsInstance<Object>()
    }

    companion object {
        fun of(value: String?): JsonValue = value?.let(::Text) ?: Null
        fun of(value: Long?): JsonValue = value?.let { Number(it.toString()) } ?: Null
        fun of(value: Int?): JsonValue = value?.let { Number(it.toString()) } ?: Null
        fun of(value: Float?): JsonValue = value?.let { Number(it.toString()) } ?: Null
        fun of(value: Boolean): JsonValue = Bool(value)
        fun obj(vararg pairs: Pair<String, JsonValue>): Object = Object(pairs.toMap())
    }
}

object Json {

    // ------------------------------------------------------------------------ Schreiben

    fun write(value: JsonValue, indent: Int = 0): String = buildString { append(value, indent, 0) }

    private fun StringBuilder.append(value: JsonValue, indent: Int, level: Int) {
        when (value) {
            is JsonValue.Null -> append("null")
            is JsonValue.Bool -> append(if (value.value) "true" else "false")
            is JsonValue.Number -> append(value.literal)
            is JsonValue.Text -> appendEscaped(value.value)

            is JsonValue.Array -> {
                if (value.items.isEmpty()) {
                    append("[]")
                    return
                }
                append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    newLine(indent, level + 1)
                    append(item, indent, level + 1)
                }
                newLine(indent, level)
                append(']')
            }

            is JsonValue.Object -> {
                if (value.entries.isEmpty()) {
                    append("{}")
                    return
                }
                append('{')
                var first = true
                value.entries.forEach { (key, entry) ->
                    if (!first) append(',')
                    first = false
                    newLine(indent, level + 1)
                    appendEscaped(key)
                    append(':')
                    if (indent > 0) append(' ')
                    append(entry, indent, level + 1)
                }
                newLine(indent, level)
                append('}')
            }
        }
    }

    private fun StringBuilder.newLine(indent: Int, level: Int) {
        if (indent <= 0) return
        append('\n')
        repeat(indent * level) { append(' ') }
    }

    private fun StringBuilder.appendEscaped(text: String) {
        append('"')
        text.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '' -> append("\\f")
                else -> if (character < ' ') {
                    append("\\u").append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    // --------------------------------------------------------------------------- Lesen

    /** Gibt `null` zurück, wenn der Text kein gültiges JSON ist — statt zu werfen. */
    fun parse(text: String): JsonValue? = runCatching {
        val reader = Reader(text)
        val value = reader.readValue()
        reader.skipWhitespace()
        if (!reader.atEnd()) error("Zeichen nach dem Ende: ${reader.position}")
        value
    }.getOrNull()

    private class Reader(private val text: String) {
        var position = 0

        fun atEnd(): Boolean = position >= text.length

        fun skipWhitespace() {
            while (position < text.length && text[position].isWhitespace()) position++
        }

        fun readValue(): JsonValue {
            skipWhitespace()
            check(position < text.length) { "Unerwartetes Ende" }
            return when (val character = text[position]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> JsonValue.Text(readString())
                't', 'f' -> readBoolean()
                'n' -> readNull()
                else -> if (character == '-' || character.isDigit()) readNumber() else error("Unerwartet: $character")
            }
        }

        private fun readObject(): JsonValue.Object {
            expect('{')
            val entries = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (peek() == '}') {
                position++
                return JsonValue.Object(entries)
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                entries[key] = readValue()
                skipWhitespace()
                when (val character = next()) {
                    ',' -> Unit
                    '}' -> return JsonValue.Object(entries)
                    else -> error("Erwartet , oder }, gefunden $character")
                }
            }
        }

        private fun readArray(): JsonValue.Array {
            expect('[')
            val items = mutableListOf<JsonValue>()
            skipWhitespace()
            if (peek() == ']') {
                position++
                return JsonValue.Array(items)
            }
            while (true) {
                items += readValue()
                skipWhitespace()
                when (val character = next()) {
                    ',' -> Unit
                    ']' -> return JsonValue.Array(items)
                    else -> error("Erwartet , oder ], gefunden $character")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                when (val character = next()) {
                    '"' -> return builder.toString()
                    '\\' -> when (val escaped = next()) {
                        '"' -> builder.append('"')
                        '\\' -> builder.append('\\')
                        '/' -> builder.append('/')
                        'n' -> builder.append('\n')
                        'r' -> builder.append('\r')
                        't' -> builder.append('\t')
                        'b' -> builder.append('\b')
                        'f' -> builder.append('')
                        'u' -> {
                            val code = text.substring(position, position + 4).toInt(16)
                            position += 4
                            builder.append(code.toChar())
                        }
                        else -> error("Unbekannte Escape-Folge \\$escaped")
                    }
                    else -> builder.append(character)
                }
            }
        }

        private fun readNumber(): JsonValue.Number {
            val start = position
            if (peek() == '-') position++
            while (position < text.length && (text[position].isDigit() || text[position] in ".eE+-")) position++
            return JsonValue.Number(text.substring(start, position))
        }

        private fun readBoolean(): JsonValue.Bool = when {
            text.startsWith("true", position) -> {
                position += 4
                JsonValue.Bool(true)
            }
            text.startsWith("false", position) -> {
                position += 5
                JsonValue.Bool(false)
            }
            else -> error("Ungültiger Wahrheitswert")
        }

        private fun readNull(): JsonValue {
            check(text.startsWith("null", position)) { "Ungültiges null" }
            position += 4
            return JsonValue.Null
        }

        private fun peek(): Char = text[position]

        private fun next(): Char {
            check(position < text.length) { "Unerwartetes Ende" }
            return text[position++]
        }

        private fun expect(character: Char) {
            check(next() == character) { "Erwartet $character an Position ${position - 1}" }
        }
    }
}
