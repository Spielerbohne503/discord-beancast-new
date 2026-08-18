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
 * Eine App, die man sich selbst installiert, hat keinen Absturzbericht, den irgendjemand
 * bekommt. Ohne diese Datei bleibt nach einem Absturz nur die Meldung „App wird
 * wiederholt beendet“ — und die sagt nichts darüber, was schiefging.
 *
 * Geschrieben wird in den privaten Speicher der App, nichts verlässt das Gerät von
 * selbst. Angezeigt wird das Protokoll unter „Mehr“, von dort lässt es sich teilen.
 */
object CrashLog {

    private const val FILE_NAME = "letzter-absturz.txt"
    private const val TAG = "CrashLog"

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

    private fun write(context: Context, thread: Thread, fehler: Throwable) {
        val spur = StringWriter().also { fehler.printStackTrace(PrintWriter(it)) }.toString()

        file(context).writeText(
            buildString {
                appendLine("PeTodo ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Android ${android.os.Build.VERSION.SDK_INT} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("Zeitpunkt: ${Instant.now()}")
                appendLine("Faden: ${thread.name}")
                appendLine()
                append(spur)
            }
        )
    }

    /** Der letzte festgehaltene Absturz — oder `null`, wenn es keinen gab. */
    fun read(context: Context): String? =
        file(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
}
