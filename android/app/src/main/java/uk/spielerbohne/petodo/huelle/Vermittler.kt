package uk.spielerbohne.petodo.huelle

import android.content.res.AssetManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.IOException

/**
 * Liefert die Webseite aus dem Paket aus.
 *
 * Warum nicht einfach `file:///android_asset/index.html`? Weil eine `file://`-Adresse in
 * modernen WebViews **kein sicherer Ursprung** ist — und ohne sicheren Ursprung gibt es
 * kein IndexedDB. Die ganze Datenhaltung der App hinge daran.
 *
 * Also wird eine `https`-Adresse geladen und **jede** Anfrage darauf hier beantwortet.
 * Ins Netz geht dabei nichts: Die App hat keine Netzwerkerlaubnis, und der WebView hat
 * zusätzlich `blockNetworkLoads`. Was hier nicht beantwortet wird, kommt nicht.
 */
class Vermittler(private val assets: AssetManager) {

    fun beantworten(anfrage: WebResourceRequest): WebResourceResponse? {
        val adresse = anfrage.url
        if (adresse.scheme != "https" || adresse.host != HOST) return null
        if (anfrage.method != "GET") return leer()

        val pfad = pfadAus(adresse.path.orEmpty()) ?: return leer()

        return try {
            WebResourceResponse(typVon(pfad), "utf-8", assets.open(pfad)).apply {
                // Die Dateien liegen im Paket; nach einem Update sollen die neuen gelten.
                responseHeaders = mapOf("Cache-Control" to "no-store")
            }
        } catch (fehler: IOException) {
            leer()
        }
    }

    /**
     * Aus dem Pfad der Adresse wird ein Pfad im Paket.
     *
     * `..` fliegt raus, bevor irgendetwas geöffnet wird. Der Ursprung ist zwar nur diese
     * App selbst — aber ein Ausbruch aus dem Asset-Ordner wäre trotzdem ein Loch, und
     * Löcher, die „ohnehin niemand erreicht“, sind die, die man später erreicht.
     */
    private fun pfadAus(roh: String): String? {
        val ohneSchraege = roh.trimStart('/')
        val fertig = if (ohneSchraege.isEmpty() || ohneSchraege == WURZEL) "$WURZEL/index.html" else ohneSchraege

        if (!fertig.startsWith("$WURZEL/")) return null
        if (fertig.split('/').any { it == ".." }) return null
        return fertig
    }

    private fun leer() = WebResourceResponse("text/plain", "utf-8", 404, "Nicht gefunden", emptyMap(), null)

    private fun typVon(pfad: String): String = when (pfad.substringAfterLast('.', "")) {
        "html" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "webmanifest" -> "application/manifest+json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "woff2" -> "font/woff2"
        else -> "application/octet-stream"
    }

    companion object {
        /**
         * Für genau diesen Zweck vorgesehen und im Netz nicht auflösbar.
         *
         * Der Ursprung muss über die Zeit **gleich bleiben**: Er ist der Schlüssel, unter
         * dem IndexedDB liegt. Wer ihn ändert, hat die Daten aller Nutzer weggeworfen.
         */
        const val HOST = "appassets.androidplatform.net"
        const val WURZEL = "web"
        const val START = "https://$HOST/$WURZEL/index.html"
    }
}
