package uk.spielerbohne.petodo.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonTest {

    @Test
    fun zeitstempel_ueberstehen_das_schreiben_und_lesen_exakt() {
        // Der eigentliche Grund für den eigenen Codec: Über einen Double würde diese
        // Millisekunden-Zahl ihre letzten Stellen verlieren.
        val millis = 1_787_654_321_987L
        val text = Json.write(JsonValue.obj("at" to JsonValue.of(millis)))
        val gelesen = Json.parse(text) as JsonValue.Object

        assertEquals(millis, gelesen.long("at"))
    }

    @Test
    fun sonderzeichen_im_text_ueberleben_die_runde() {
        val original = "Zeile1\nZeile2\t\"Anführung\" \\ Backslash / Schrägstrich · Umlaute äöüß"
        val text = Json.write(JsonValue.obj("titel" to JsonValue.of(original)))
        val gelesen = Json.parse(text) as JsonValue.Object

        assertEquals(original, gelesen.string("titel"))
    }

    @Test
    fun steuerzeichen_werden_als_escape_geschrieben() {
        val text = Json.write(JsonValue.of("ab"))
        assertEquals("\"a\\u0001b\"", text)
        assertEquals("ab", (Json.parse(text) as JsonValue.Text).value)
    }

    @Test
    fun null_bleibt_null_und_nicht_der_text_null() {
        val text = Json.write(JsonValue.obj("note" to JsonValue.of(null as String?)))
        val gelesen = Json.parse(text) as JsonValue.Object

        assertNull(gelesen.string("note"))
        assertEquals("""{"note":null}""", text)
    }

    @Test
    fun verschachtelte_strukturen_bleiben_erhalten() {
        val original = JsonValue.obj(
            "version" to JsonValue.of(1),
            "tasks" to JsonValue.Array(
                listOf(
                    JsonValue.obj(
                        "id" to JsonValue.of("a"),
                        "done" to JsonValue.of(true),
                        "tags" to JsonValue.Array(listOf(JsonValue.of("x"), JsonValue.of("y"))),
                    ),
                    JsonValue.obj("id" to JsonValue.of("b"), "done" to JsonValue.of(false)),
                )
            ),
        )
        val gelesen = Json.parse(Json.write(original, indent = 2)) as JsonValue.Object

        assertEquals(1, gelesen.int("version"))
        assertEquals(2, gelesen.objects("tasks").size)
        assertEquals("a", gelesen.objects("tasks").first().string("id"))
        assertEquals(true, gelesen.objects("tasks").first().boolean("done"))
        assertEquals(2, gelesen.objects("tasks").first().array("tags").size)
    }

    @Test
    fun leere_strukturen_funktionieren() {
        assertEquals("[]", Json.write(JsonValue.Array(emptyList())))
        assertEquals("{}", Json.write(JsonValue.Object(emptyMap())))
        assertEquals(0, (Json.parse("[]") as JsonValue.Array).items.size)
        assertEquals(0, (Json.parse("{}") as JsonValue.Object).entries.size)
    }

    @Test
    fun einrueckung_aendert_den_inhalt_nicht() {
        val original = JsonValue.obj("a" to JsonValue.of(1), "b" to JsonValue.of("x"))
        assertEquals(Json.parse(Json.write(original)), Json.parse(Json.write(original, indent = 4)))
    }

    @Test
    fun kaputtes_json_ergibt_null_statt_einer_ausnahme() {
        // Eine beschädigte Datei darf die Wiederherstellung scheitern lassen, nicht die App.
        listOf(
            "",
            "{",
            "{\"a\":}",
            "[1, 2",
            "{\"a\" 1}",
            "nicht json",
            "{\"a\":1} überflüssig",
        ).forEach { text ->
            assertNull("'$text' hätte null ergeben müssen", Json.parse(text))
        }
    }

    @Test
    fun fliesskommazahlen_bleiben_lesbar() {
        val text = Json.write(JsonValue.obj("energie" to JsonValue.of(73.5f)))
        assertEquals(73.5, (Json.parse(text) as JsonValue.Object).double("energie")!!, 0.0001)
    }

    @Test
    fun negative_zahlen_und_exponenten_werden_gelesen() {
        val gelesen = Json.parse("""{"a":-12,"b":1.5e3}""") as JsonValue.Object
        assertEquals(-12L, gelesen.long("a"))
        assertEquals(1500.0, gelesen.double("b")!!, 0.0001)
    }

    @Test
    fun wahrheitswerte_aus_alten_sicherungen_werden_verstanden() {
        val gelesen = Json.parse("""{"neu":true,"alt":1,"aus":0}""") as JsonValue.Object
        assertEquals(true, gelesen.boolean("neu"))
        assertEquals(true, gelesen.boolean("alt"))
        assertEquals(false, gelesen.boolean("aus"))
    }
}
