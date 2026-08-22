package uk.spielerbohne.petodo.huelle

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.app.Activity

/**
 * Die ganze App.
 *
 * Ein WebView mit der mitgelieferten Webseite, eine Brücke und ein Wecker. Kein eigener
 * Bildschirm, keine eigene Datenhaltung, keine eigene Fachlogik — was hier stünde, stünde
 * zweimal.
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var vermittler: Vermittler
    private val hauptfaden = Handler(Looper.getMainLooper())

    override fun onCreate(zustand: Bundle?) {
        super.onCreate(zustand)

        Meldungen.kanaeleAnlegen(this)
        vermittler = Vermittler(assets)

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            // Der Grund der Seite ist fast schwarz; ein weißes Aufblitzen beim Start
            // sähe aus, als wäre etwas kaputt.
            setBackgroundColor(0xFF06070D.toInt())

            settings.javaScriptEnabled = true
            // IndexedDB braucht nur JavaScript und einen sicheren Ursprung — den liefert
            // der Vermittler. `databaseEnabled` galt der alten WebSQL-Schnittstelle und
            // ist längst ohne Wirkung.
            settings.domStorageEnabled = true

            // Nichts geht ins Netz. Die App hat dafür nicht einmal die Erlaubnis; das hier
            // ist der zweite Riegel, damit auch ein Versehen im HTML folgenlos bleibt.
            settings.blockNetworkLoads = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportMultipleWindows(false)
            settings.mediaPlaybackRequiresUserGesture = true

            // Die Seite bringt ihre eigene Anordnung mit und rechnet in CSS-Pixeln.
            settings.useWideViewPort = false
            settings.loadWithOverviewMode = false
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            isVerticalScrollBarEnabled = true
            overScrollMode = WebView.OVER_SCROLL_NEVER

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    ansicht: WebView,
                    anfrage: WebResourceRequest,
                ): WebResourceResponse? = vermittler.beantworten(anfrage)

                /**
                 * Ein Verweis nach draußen gehört in den Browser, nicht in diese Hülle.
                 *
                 * In der Aufgabenverwaltung stehen Verweise auf Videos und Wikipedia. Im
                 * WebView geöffnet wären sie in einer App gefangen, die keine Adresszeile,
                 * keinen Zurück-Knopf zum Browser und keine Netzwerkerlaubnis hat.
                 */
                override fun shouldOverrideUrlLoading(
                    ansicht: WebView,
                    anfrage: WebResourceRequest,
                ): Boolean {
                    val adresse = anfrage.url
                    if (adresse.host == Vermittler.HOST) return false

                    nachDraussen(adresse)
                    return true
                }
            }

            addJavascriptInterface(Bruecke(Ablage(this@MainActivity), umgebung()), Bruecke.NAME)
        }

        setContentView(webView)

        // Nach einem Drehen des Geräts steht der Zustand im WebView; neu laden würde die
        // halb getippte Zeile verwerfen.
        if (zustand == null) webView.loadUrl(Vermittler.START) else webView.restoreState(zustand)
    }

    override fun onSaveInstanceState(zustand: Bundle) {
        super.onSaveInstanceState(zustand)
        webView.saveState(zustand)
    }

    /**
     * Der Zurück-Knopf geht durch die Ansichten der Seite zurück.
     *
     * Die Seite führt ihren Verlauf im Hash; ohne diese Weiterleitung schlösse der
     * Zurück-Knopf beim ersten Druck die ganze App.
     */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    private fun nachDraussen(adresse: Uri) {
        val absicht = Intent(Intent.ACTION_VIEW, adresse).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Ohne passende App passiert nichts — das ist besser als ein Absturz.
        runCatching { startActivity(absicht) }
    }

    private fun umgebung() = object : Bruecke.Umgebung {

        override fun weckerNeuStellen(rufe: List<Weckruf>) {
            Wecker.neuStellen(this@MainActivity, rufe)
        }

        override fun meldungenErlaubt(): Boolean =
            getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true

        /**
         * Die Erlaubnis wird erst erfragt, wenn der Nutzer die Erinnerungen einschaltet —
         * nie beim ersten Start. Ein Dialog, den man nicht erwartet hat, wird weggeklickt,
         * und danach ist er für immer weg.
         *
         * Der Aufruf kommt aus dem JavaScript-Faden; angefragt werden darf nur im
         * Hauptfaden.
         */
        override fun erlaubnisAnfragen() {
            hauptfaden.post {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), ANFRAGE_MELDUNGEN)
                } else {
                    // Vor Android 13 gibt es keine Abfrage; abgeschaltet wird in den
                    // Systemeinstellungen, also führt der Weg auch dorthin zurück.
                    einstellungenOeffnen()
                }
            }
        }

        override fun genaueWeckerErlaubt(): Boolean = Wecker.genauErlaubt(this@MainActivity)

        override fun fassung(): String = BuildConfig.VERSION_NAME
    }

    private fun einstellungenOeffnen() {
        val absicht = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        runCatching { startActivity(absicht) }
    }

    companion object {
        private const val ANFRAGE_MELDUNGEN = 1
    }
}
