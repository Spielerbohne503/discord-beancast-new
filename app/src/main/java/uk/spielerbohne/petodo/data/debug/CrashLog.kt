package uk.spielerbohne.petodo.data.debug

import android.content.Context
import android.util.Log
import uk.spielerbohne.petodo.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Das Absturzprotokoll.
 *
 * Eine App, die man sich selbst installiert, hat niemanden, der ihre Abstürze bekommt.
 * Ohne diese Datei bleibt nach einem Absturz nur die Meldung „App wird wiederholt
 * beendet“ — und die sagt nichts darüber, was schiefging.
 *
 * Geschrieben wird in den privaten Speicher der App; nichts verlässt das Gerät von
 * selbst. Beim nächsten Start entscheidet [crashedRecently], ob die App in einer
 * Absturzschleife steckt und statt ihrer Oberfläche den Bericht zeigen sollte.
 */
object CrashLog {

    private const val FILE_NAME = "letzter-absturz.txt"
    private const val TIME_FILE = "letzter-absturz-zeit.txt"
    private const val TAG = "CrashLog"

    /**
     * Innerhalb dieser Zeitspanne nach einem Absturz gilt der nächste Start als
     * Wiederanlauf einer Schleife — dann zeigt die App den Bericht statt sich erneut in
     * denselben Fehler zu stürzen.
     */
    const val LOOP_WINDOW_MILLIS = 90_000L

    /**
     * Hängt sich vor den Standardbehandler von Android.
     *
     * Der vorherige Behandler wird danach **immer** aufgerufen: Das Schreiben ersetzt den
     * Absturz nicht, es hält ihn nur fest. Eine App, die ihre eigenen Abstürze
     * verschluckt, ist schlimmer als eine, die abstürzt.
     */
    fun install(context: Context) {
        val vorheriger = Thread.getDefaultUncaughtExceptionHandler()
        val appContext = context.applicationContext

        Thread.setDefaultUncaughtExceptionHandler { thread, fehler ->
            runCatching { write(appContext, thread, fehler) }
                .onFailure { Log.e(TAG, "Absturz konnte nicht festgehalten werden", it) }
            vorheriger?.uncaughtException(thread, fehler)
        }
    }

    /** Auch für Fehler, die nicht den Prozess beenden — etwa aus dem Hintergrund. */
    fun record(context: Context, quelle: String, fehler: Throwable) {
        runCatching { write(context.applicationContext, Thread.currentThread(), fehler, quelle) }
    }

    private fun write(context: Context, thread: Thread, fehler: Throwable, quelle: String? = null) {
        val spur = StringWriter().also { fehler.printStackTrace(PrintWriter(it)) }.toString()

        file(context).writeText(
            buildString {
                appendLine("PeTodo ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Android ${android.os.Build.VERSION.SDK_INT} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("Zeitpunkt: ${Instant.now()}")
                appendLine("Faden: ${thread.name}")
                quelle?.let { appendLine("Stelle: $it") }
                appendLine()
                append(spur)
            }
        )
        timeFile(context).writeText(System.currentTimeMillis().toString())
    }

    /** Der letzte festgehaltene Absturz — oder `null`, wenn es keinen gab. */
    fun read(context: Context): String? =
        file(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    /**
     * Ob der letzte Absturz gerade eben war.
     *
     * Genau das unterscheidet „ist mal abgestürzt“ von „stürzt beim Starten ab“: Im
     * zweiten Fall darf die App gar nicht erst versuchen, normal hochzufahren.
     */
    fun crashedRecently(context: Context, withinMillis: Long = LOOP_WINDOW_MILLIS): Boolean {
        val zeit = runCatching { timeFile(context).takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() }
            .getOrNull() ?: return false
        val abstand = System.currentTimeMillis() - zeit
        return abstand in 0..withinMillis
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
        runCatching { timeFile(context).delete() }
    }

    /** Nur den Schleifen-Verdacht zurücksetzen, den Bericht behalten. */
    fun clearLoopMarker(context: Context) {
        runCatching { timeFile(context).delete() }
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun timeFile(context: Context) = File(context.filesDir, TIME_FILE)
}
