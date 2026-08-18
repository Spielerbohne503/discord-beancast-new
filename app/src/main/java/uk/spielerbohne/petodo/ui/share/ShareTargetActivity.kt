package uk.spielerbohne.petodo.ui.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import uk.spielerbohne.petodo.PetodoApplication
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.domain.text.ShareCapture
import uk.spielerbohne.petodo.domain.text.SharedTask
import uk.spielerbohne.petodo.ui.theme.PetodoTheme
import java.time.LocalDate
import java.time.LocalTime

/**
 * „Teilen an PeTodo“.
 *
 * Der schnellste Weg, etwas hereinzubekommen: aus dem Browser, aus Instagram, aus einer
 * Nachricht. Ohne diesen Weg tippt man Adressen ab — und tut es nach zwei Versuchen
 * nicht mehr.
 *
 * Die Activity ist ein Dialog über der teilenden App und kehrt nach dem Sichern dorthin
 * zurück. Was Titel und was Notiz wird, entscheidet [ShareCapture] in `domain/`.
 */
class ShareTargetActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val geteilt = capture(intent)
        if (geteilt == null) {
            Toast.makeText(this, R.string.share_empty, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val container = (application as PetodoApplication).container

        setContent {
            PetodoTheme {
                ShareSheet(
                    initial = geteilt,
                    onSave = { titel, notiz, faelligAm, faelligUm, prioritaet ->
                        // Der Nutzer wartet nicht auf die Datenbank: Der Dialog schließt
                        // sofort, das Anlegen läuft im Anwendungsbereich weiter.
                        container.applicationScope.launch {
                            container.taskRepository.createTask(
                                title = titel,
                                note = notiz,
                                dueDate = faelligAm,
                                dueTime = faelligUm,
                                priority = prioritaet,
                            ).let { id -> container.nagCoordinator.syncTask(id) }
                        }
                        Toast.makeText(this, R.string.share_saved, Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onCancel = ::finish,
                )
            }
        }
    }

    private fun capture(intent: Intent?): SharedTask? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return ShareCapture.capture(
            subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
            text = intent.getStringExtra(Intent.EXTRA_TEXT),
        )
    }
}

/** Signatur des Sicherns — hier gebündelt, damit die Activity lesbar bleibt. */
internal typealias OnShareSave = (
    title: String,
    note: String?,
    dueDate: LocalDate?,
    dueTime: LocalTime?,
    priority: Int,
) -> Unit
