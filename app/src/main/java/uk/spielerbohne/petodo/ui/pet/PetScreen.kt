package uk.spielerbohne.petodo.ui.pet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.data.pet.labelRes
import uk.spielerbohne.petodo.domain.pet.HealthStage
import uk.spielerbohne.petodo.domain.pet.Level
import uk.spielerbohne.petodo.domain.pet.OverdueLoad
import uk.spielerbohne.petodo.domain.pet.RewardType
import java.time.Duration

@Composable
fun PetRoute(container: AppContainer) {
    val viewModel: PetViewModel = viewModel(factory = PetViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bubble by viewModel.bubble.collectAsStateWithLifecycle()

    PetScreen(
        state = state,
        reactionText = rememberSpeech(bubble),
        moodText = rememberSpeech(state.moodCategory),
        onCare = viewModel::care,
        onRename = viewModel::rename,
    )
}

@Composable
private fun PetScreen(
    state: PetUiState,
    reactionText: String?,
    moodText: String?,
    onCare: (RewardType) -> Unit,
    onRename: (String) -> Unit,
) {
    val pet = state.snapshot.state
    val vorgabe = stringResource(R.string.pet_default_name)
    val name = state.name.ifBlank { vorgabe }
    var renaming by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StatusPanel(
                name = name,
                stage = pet.stage,
                level = pet.level,
                xp = pet.xp,
                energy = pet.values.energy,
                satiety = pet.values.satiety,
                mood = pet.values.mood,
                speech = reactionText ?: moodText,
                onRename = { renaming = true },
            )
        }

        item {
            CareRow(
                cooldowns = state.cooldowns,
                onCare = onCare,
            )
        }

        item { LoadCard(load = state.snapshot.load, overdueCount = state.snapshot.overdueCount) }

        item {
            Text(
                text = stringResource(R.string.pet_no_death),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (renaming) {
        RenameDialog(
            initial = state.name.ifBlank { vorgabe },
            onDismiss = { renaming = false },
            onConfirm = {
                onRename(it)
                renaming = false
            },
        )
    }
}

/**
 * Die Statustafel aus Abschnitt 8.1: Name, Level, Zustand, drei Balken. Kein Sprite —
 * und trotzdem sieht man auf einen Blick, woran man ist.
 */
@Composable
private fun StatusPanel(
    name: String,
    stage: HealthStage,
    level: Int,
    xp: Int,
    energy: Double,
    satiety: Double,
    mood: Double,
    speech: String?,
    onRename: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stage.emoji(), style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(text = name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = stringResource(R.string.pet_level, level) + " · " +
                            stringResource(stage.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onRename) { Text(stringResource(R.string.pet_rename)) }
            }

            speech?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Text(
                        text = "„$it“",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            ValueBar(stringResource(R.string.pet_bar_energy), energy, stage)
            ValueBar(stringResource(R.string.pet_bar_satiety), satiety, stage)
            ValueBar(stringResource(R.string.pet_bar_mood), mood, stage)

            Column {
                LinearProgressIndicator(
                    progress = { Level.progressWithin(xp).toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.pet_level_progress,
                        Level.xpToNextLevel(xp),
                        level + 1,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ValueBar(label: String, value: Double, stage: HealthStage) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.pet_value_percent, value.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (value / 100.0).toFloat() },
            color = stage.color(),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        )
    }
}

/**
 * Füttern, Spielen, Streicheln — mit sichtbarer Sperrzeit.
 *
 * Der Knopf verschwindet nicht, wenn die Sperre läuft: Er sagt, wie lange noch. Ein
 * verschwundener Knopf sieht aus wie ein Fehler.
 */
@Composable
private fun CareRow(cooldowns: Map<RewardType, Duration>, onCare: (RewardType) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PetViewModel.CARE_ACTIONS.forEach { type ->
            val remaining = cooldowns[type] ?: Duration.ZERO
            val ready = remaining.isZero || remaining.isNegative
            val label = when (type) {
                RewardType.FEED -> R.string.pet_action_feed
                RewardType.PLAY -> R.string.pet_action_play
                else -> R.string.pet_action_pat
            }

            if (ready) {
                Button(onClick = { onCare(type) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(label), textAlign = TextAlign.Center)
                }
            } else {
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(label) + "\n" +
                            stringResource(R.string.pet_action_locked, formatCooldown(remaining)),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Warum es dem Pet gerade so geht — ohne diese Karte wirkt der Verfall willkürlich. */
@Composable
private fun LoadCard(load: Double, overdueCount: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.pet_load_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (overdueCount == 0) {
                Text(
                    text = stringResource(R.string.pet_load_none),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.pet_load_some,
                        overdueCount,
                        stringResource(
                            R.string.pet_load_factor,
                            OverdueLoad.baseMultiplier(load),
                        ),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.pet_recovery_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pet_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.pet_rename_hint)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
