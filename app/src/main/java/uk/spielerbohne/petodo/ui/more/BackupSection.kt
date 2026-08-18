package uk.spielerbohne.petodo.ui.more

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.ui.theme.GlassCard
import java.time.LocalDate

/**
 * Sichern und Wiederherstellen über den Systemdialog.
 *
 * Bewusst über SAF (`CreateDocument`/`OpenDocument`) statt über einen eigenen Ordner:
 * Die App braucht dafür keine Dateiberechtigung, und die Datei landet dort, wo der
 * Nutzer sie wiederfindet — auch wenn die App weg ist.
 */
@Composable
fun BackupSection(
    onExport: (Uri) -> Unit,
    onRestore: (Uri) -> Unit,
) {
    val fileName = stringResource(R.string.backup_file_name, LocalDate.now().toString())

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_JSON)
    ) { uri -> uri?.let(onExport) }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onRestore) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.backup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch(fileName) }) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Text(
                        text = stringResource(R.string.backup_export),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf(MIME_JSON, MIME_ANY)) }
                ) {
                    Icon(Icons.Filled.Upload, contentDescription = null)
                    Text(
                        text = stringResource(R.string.backup_import),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.backup_restore_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val MIME_JSON = "application/json"

/** Manche Dateiverwaltungen melden JSON als beliebigen Typ — sonst ist die Datei ausgegraut. */
private const val MIME_ANY = "*/*"
