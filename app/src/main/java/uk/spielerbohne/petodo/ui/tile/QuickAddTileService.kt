package uk.spielerbohne.petodo.ui.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import uk.spielerbohne.petodo.ui.MainActivity

/**
 * Die Kachel in den Schnelleinstellungen: Aufgabe erfassen, ohne die App zu suchen
 * (Projektplan, v2.1).
 *
 * Der Weg dorthin ist der kürzeste, den Android kennt — zweimal wischen, einmal tippen.
 * Gedacht für den Moment, in dem einem etwas einfällt, während man gerade etwas anderes
 * tut; genau dann öffnet man keine App.
 */
class QuickAddTileService : TileService() {

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_QUICK_ADD, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapseCompat(intent)
        } else {
            startActivityAndCollapseLegacy(intent)
        }
    }

    /**
     * Der Weg unterhalb von Android 14.
     *
     * Die Methode ist als veraltet gemeldet, aber ihre Nachfolgerin gibt es dort noch
     * nicht — und `minSdk` ist 26. Die Warnung wird deshalb an genau dieser Stelle
     * abgeschaltet und nirgends sonst.
     */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseLegacy(intent: Intent) {
        startActivityAndCollapse(intent)
    }

    /**
     * Ab Android 14 nimmt die Methode keinen Intent mehr, sondern einen Entwurf — ein
     * Intent führt dort zu einer Ausnahme statt zu einer geöffneten App.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun startActivityAndCollapseCompat(intent: Intent) {
        startActivityAndCollapse(
            PendingIntent.getActivity(
                this,
                REQUEST_TILE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
    }

    private companion object {
        const val REQUEST_TILE = 9101
    }
}
