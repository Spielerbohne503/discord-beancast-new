package uk.spielerbohne.petodo.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import uk.spielerbohne.petodo.R

/**
 * Einmaliges Onboarding: erklärt die drei Berechtigungen, bevor es sie anfordert, und
 * weist bei den bekannten Herstellern auf deren eigene App-Verwaltung hin.
 *
 * Bewusst überspringbar. Wer hier zum Erlauben gezwungen wird, erlaubt beim nächsten
 * Systemdialog aus Reflex gar nichts mehr.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(Permissions.current(context)) }

    // Nach der Rückkehr aus den Systemeinstellungen neu prüfen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state = Permissions.current(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { state = Permissions.current(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.onboarding_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PermissionCard(
            titleRes = R.string.onboarding_notifications_title,
            bodyRes = R.string.onboarding_notifications_body,
            granted = state.notifications,
            onGrant = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(Permissions.appSettingsIntent(context))
                }
            },
        )

        PermissionCard(
            titleRes = R.string.onboarding_exact_alarms_title,
            bodyRes = R.string.onboarding_exact_alarms_body,
            granted = state.exactAlarms,
            onGrant = {
                val intent = Permissions.exactAlarmSettingsIntent(context)
                    ?: Permissions.appSettingsIntent(context)
                runCatching { context.startActivity(intent) }
            },
        )

        PermissionCard(
            titleRes = R.string.onboarding_battery_title,
            bodyRes = R.string.onboarding_battery_body,
            granted = state.batteryExemption,
            onGrant = {
                runCatching { context.startActivity(Permissions.batteryExemptionIntent(context)) }
                    .onFailure { context.startActivity(Permissions.appSettingsIntent(context)) }
            },
        )

        if (Permissions.needsManufacturerHint()) {
            ManufacturerHint()
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onFinished) { Text(stringResource(R.string.onboarding_skip)) }
            Button(onClick = onFinished, modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.onboarding_done))
            }
        }
    }
}

@Composable
private fun PermissionCard(
    titleRes: Int,
    bodyRes: Int,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (granted) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (granted) {
                Text(
                    text = stringResource(R.string.onboarding_granted),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Button(onClick = onGrant) { Text(stringResource(R.string.onboarding_grant)) }
            }
        }
    }
}

@Composable
private fun ManufacturerHint() {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = stringResource(R.string.onboarding_manufacturer_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = stringResource(R.string.onboarding_manufacturer_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { context.startActivity(Permissions.appSettingsIntent(context)) }) {
                Text(stringResource(R.string.onboarding_open_settings))
            }
        }
    }
}
