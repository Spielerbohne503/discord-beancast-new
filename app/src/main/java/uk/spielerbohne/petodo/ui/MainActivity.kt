package uk.spielerbohne.petodo.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uk.spielerbohne.petodo.PetodoApplication
import uk.spielerbohne.petodo.data.focus.FocusAction
import uk.spielerbohne.petodo.data.debug.CrashLog
import uk.spielerbohne.petodo.data.focus.FocusService
import uk.spielerbohne.petodo.ui.debug.CrashScreen
import uk.spielerbohne.petodo.ui.theme.PetodoTheme

class MainActivity : ComponentActivity() {

    /**
     * Ob die Schnell-Eingabe beim Öffnen den Finger bekommt.
     *
     * Steht als Zustand da und nicht als einmalig gelesenes Extra: Die Activity ist
     * `singleTop`, ein zweiter Tipp auf „+ Aufgabe“ startet sie also nicht neu, sondern
     * landet in [onNewIntent].
     */
    private var quickAdd by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        quickAdd = intent?.getBooleanExtra(EXTRA_QUICK_ADD, false) == true

        val container = (application as PetodoApplication).container

        // Steckt die App in einer Absturzschleife, wird zuerst der Bericht gezeigt.
        // Sonst startet sie wieder in denselben Fehler, und man kommt nie an die
        // Information heran, die zur Behebung nötig wäre.
        val schleife = CrashLog.crashedRecently(this)
        val bericht = if (schleife) CrashLog.read(this) else null

        if (bericht == null) {
            // Hier — und nur hier — ist die App sicher im Vordergrund. Ab Android 12 ist
            // das die Bedingung dafür, dass ein Foreground Service starten darf.
            runCatching { FocusService.send(this, FocusAction.RESUME_DISPLAY) }
        }

        setContent {
            PetodoTheme {
                var zeigeBericht by remember { mutableStateOf(bericht) }

                val text = zeigeBericht
                if (text != null) {
                    CrashScreen(
                        report = text,
                        onContinue = {
                            // Den Bericht behalten, nur den Schleifenverdacht löschen.
                            CrashLog.clearLoopMarker(this@MainActivity)
                            zeigeBericht = null
                        },
                        onDismiss = {
                            CrashLog.clear(this@MainActivity)
                            zeigeBericht = null
                        },
                    )
                } else {
                    PetodoApp(
                        container = container,
                        quickAdd = quickAdd,
                        onQuickAddConsumed = { quickAdd = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_QUICK_ADD, false)) quickAdd = true
    }

    companion object {
        const val EXTRA_QUICK_ADD = "uk.spielerbohne.petodo.extra.QUICK_ADD"
    }
}
