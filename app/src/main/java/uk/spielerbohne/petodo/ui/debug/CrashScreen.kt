package uk.spielerbohne.petodo.ui.debug

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.ui.theme.GlassCard

/**
 * Der Bildschirm, den man sieht, wenn die App sich nicht öffnen lässt.
 *
 * Er hängt an nichts: kein Container, keine Datenbank, keine Einstellungen. Genau
 * deshalb kann er auch dann noch erscheinen, wenn alles andere scheitert — und genau
 * dafür ist er da.
 */
@Composable
fun CrashScreen(report: String, onContinue: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val betreff = stringResource(R.string.crash_title)
    val teilenLabel = stringResource(R.string.crash_share)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.crash_safe_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.crash_safe_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = report,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(14.dp)
                    .horizontalScroll(rememberScrollState()),
            )
        }

        Button(
            onClick = {
                val teilen = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, betreff)
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                runCatching { context.startActivity(Intent.createChooser(teilen, teilenLabel)) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.crash_share)) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onContinue) { Text(stringResource(R.string.crash_safe_continue)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.crash_clear)) }
        }
    }
}
