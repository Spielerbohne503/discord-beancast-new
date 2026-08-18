package uk.spielerbohne.petodo.ui.pet

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import uk.spielerbohne.petodo.ui.theme.Motion
import uk.spielerbohne.petodo.ui.theme.animationsEnabled
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.data.pet.labelRes
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.pet.HealthStage
import uk.spielerbohne.petodo.domain.pet.Level
import uk.spielerbohne.petodo.domain.pet.OverdueLoad
import uk.spielerbohne.petodo.domain.pet.PetValues
import uk.spielerbohne.petodo.domain.pet.RewardType
import uk.spielerbohne.petodo.ui.theme.Brand
import uk.spielerbohne.petodo.ui.theme.CircleIconButton
import uk.spielerbohne.petodo.ui.theme.GlassCard
import uk.spielerbohne.petodo.ui.theme.GradientCard
import uk.spielerbohne.petodo.ui.theme.Palette
import uk.spielerbohne.petodo.ui.theme.ScreenGlow
import uk.spielerbohne.petodo.ui.theme.SectionLabel
import uk.spielerbohne.petodo.ui.theme.ValueTrack
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

    Box(Modifier.fillMaxSize()) {
        // Der Schein hinter der Karte trägt die Zustandsfarbe — ein krankes Pet färbt
        // den ganzen Bildschirm, nicht nur sein Kärtchen.
        ScreenGlow(
            colors = pet.stage.gradient(),
            alpha = 0.28f,
            breathing = true,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("kopf") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.pet_title),
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.weight(1f),
                    )
                    CircleIconButton(
                        icon = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.pet_rename),
                        onClick = { renaming = true },
                    )
                }
            }

            item("tafel") {
                StatusPanel(
                    name = name,
                    stage = pet.stage,
                    level = pet.level,
                    xp = pet.xp,
                    values = pet.values,
                    speech = reactionText ?: moodText,
                )
            }

            item("pflege") {
                CareRow(cooldowns = state.cooldowns, onCare = onCare)
            }

            item("last") {
                LoadCard(load = state.snapshot.load, overdueCount = state.snapshot.overdueCount)
            }

            item("hinweis") {
                Text(
                    text = stringResource(R.string.pet_no_death),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
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
 * Die Statustafel aus Abschnitt 8.1: Name, Level, Zustand, drei Balken.
 *
 * Kein Sprite — und trotzdem sieht man auf einen Blick, woran man ist: Die Karte selbst
 * ist die Anzeige. Ihr Verlauf wechselt mit der Krankheitsstufe, also erkennt man den
 * Zustand, bevor man ein Wort gelesen hat.
 */
@Composable
private fun StatusPanel(
    name: String,
    stage: HealthStage,
    level: Int,
    xp: Int,
    values: PetValues,
    speech: String?,
) {
    val puls = rememberGainPulse(values.average)

    GradientCard(
        colors = stage.gradient(),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                // Kaum zu sehen, deutlich zu spüren: zwei Prozent Größe reichen völlig.
                val wachstum = 1f + 0.02f * puls
                scaleX = wachstum
                scaleY = wachstum
            },
    ) {
        // Der Lichtblitz liegt über dem Verlauf, nicht darunter — sonst schluckt ihn
        // die eigene Farbe der Karte.
        Box(
            Modifier
                .matchParentSize()
                .background(Color.White.copy(alpha = 0.20f * puls))
        )

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BobbingPet(stage)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Palette.Chalk,
                    )
                    Text(
                        text = stringResource(stage.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Chalk.copy(alpha = 0.75f),
                    )
                }
                LevelChip(level)
            }

            // Der eine große Wert: der Schnitt der drei Balken. Er ist es, der über die
            // Stufe entscheidet — also steht er groß da und nicht im Kleingedruckten.
            Column {
                Text(
                    text = stringResource(R.string.pet_condition).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Chalk.copy(alpha = 0.7f),
                )
                // Die Zahl zählt hoch, statt zu springen: Man sieht, um wie viel es
                // besser geworden ist, nicht nur, dass es anders ist.
                val schnitt by animateIntAsState(
                    targetValue = values.average.toInt(),
                    animationSpec = Motion.slow(),
                    label = "zustandszahl",
                )
                Text(
                    text = stringResource(R.string.pet_value_percent, schnitt),
                    style = MaterialTheme.typography.displayLarge,
                    color = Palette.Chalk,
                )
            }

            // Die Blase wird eingeblendet, nicht eingesetzt — ein Gedanke kommt auf,
            // er ist nicht plötzlich schon immer da gewesen.
            AnimatedContent(
                targetState = speech,
                transitionSpec = {
                    (fadeIn(Motion.standard()) +
                        slideInVertically(Motion.standard()) { hoehe -> hoehe / 3 })
                        .togetherWith(fadeOut(Motion.quick()))
                },
                label = "sprechblase",
            ) { satz ->
                if (satz == null) {
                    Spacer(Modifier.height(0.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(Color.Black.copy(alpha = 0.28f))
                            .fillMaxWidth(),
                    ) {
                        Text(
                            text = "„$satz“",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Palette.Chalk,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ValueRow(stringResource(R.string.pet_bar_energy), values.energy)
                ValueRow(stringResource(R.string.pet_bar_satiety), values.satiety)
                ValueRow(stringResource(R.string.pet_bar_mood), values.mood)
            }

            LevelProgress(xp = xp, level = level)
        }
    }
}

@Composable
private fun LevelChip(level: Int) {
    // Ein Levelaufstieg ist selten und soll auffallen — er ist der einzige Moment, in
    // dem die App kurz stolz sein darf.
    val aufstieg = rememberGainPulse(level.toDouble())

    Box(
        modifier = Modifier
            .graphicsLayer {
                val wachstum = 1f + 0.18f * aufstieg
                scaleX = wachstum
                scaleY = wachstum
            }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        AnimatedContent(
            targetState = level,
            transitionSpec = {
                (slideInVertically(Motion.standard()) { hoehe -> hoehe } + fadeIn(Motion.standard()))
                    .togetherWith(
                        slideOutVertically(Motion.standard()) { hoehe -> -hoehe } + fadeOut(Motion.quick())
                    )
            },
            label = "level",
        ) { stufe ->
            Text(
                text = stringResource(R.string.pet_level, stufe),
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Chalk,
            )
        }
    }
}

/** Ein Balken auf farbigem Grund: weiße Spur, weißer Faden — sonst wird es bunt auf bunt. */
@Composable
private fun ValueRow(label: String, value: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Chalk.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.pet_value_percent, value.toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Chalk,
            )
        }
        ValueTrack(
            fraction = (value / 100.0).toFloat(),
            colors = listOf(Palette.Chalk, Palette.Chalk.copy(alpha = 0.85f)),
            height = 6.dp,
            trackColor = Color.Black.copy(alpha = 0.25f),
        )
    }
}

@Composable
private fun LevelProgress(xp: Int, level: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ValueTrack(
            fraction = Level.progressWithin(xp).toFloat(),
            colors = listOf(Palette.Chalk.copy(alpha = 0.9f), Palette.Chalk.copy(alpha = 0.5f)),
            height = 3.dp,
            trackColor = Color.Black.copy(alpha = 0.25f),
        )
        Text(
            text = stringResource(R.string.pet_level_progress, Level.xpToNextLevel(xp), level + 1),
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Chalk.copy(alpha = 0.7f),
        )
    }
}

/**
 * Das Pet wippt.
 *
 * v1 zeigt kein einziges Sprite (Projektplan, Abschnitt 8.1) — ohne diese kleine
 * Bewegung ist der Begleiter ein Zeichen auf einer Karte. Der Takt hängt an der Stufe:
 * Ein gesundes Pet wippt zügig, ein elendes kaum noch. Das erzählt den Zustand ein
 * zweites Mal, ohne ein weiteres Wort zu brauchen.
 */
@Composable
private fun BobbingPet(stage: HealthStage) {
    val takt = when (stage) {
        HealthStage.HEALTHY -> 1_900
        HealthStage.WEAKENED -> 2_600
        HealthStage.SICK -> 3_400
        HealthStage.MISERABLE -> 4_600
    }
    val weite = when (stage) {
        HealthStage.HEALTHY -> 5f
        HealthStage.WEAKENED -> 3.5f
        HealthStage.SICK -> 2f
        HealthStage.MISERABLE -> 1f
    }

    val hoehe = if (animationsEnabled()) {
        val schwingung = rememberInfiniteTransition(label = "wippen")
        val wert by schwingung.animateFloat(
            initialValue = weite,
            targetValue = -weite,
            animationSpec = infiniteRepeatable(
                animation = tween(takt, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "wippenHoehe",
        )
        wert
    } else {
        0f
    }

    Text(
        text = stage.emoji(),
        style = MaterialTheme.typography.displaySmall,
        modifier = Modifier.graphicsLayer { translationY = hoehe },
    )
}

/**
 * Füttern, Spielen, Streicheln — drei gleich große Kacheln.
 *
 * Der Knopf verschwindet nicht, wenn die Sperrzeit läuft: Er wird still und sagt, wie
 * lange noch. Ein verschwundener Knopf sieht aus wie ein Fehler.
 */
@Composable
private fun CareRow(cooldowns: Map<RewardType, Duration>, onCare: (RewardType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(
            text = stringResource(R.string.pet_care_title),
            accent = Palette.Magenta,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PetViewModel.CARE_ACTIONS.forEach { type ->
                CareTile(
                    type = type,
                    remaining = cooldowns[type] ?: Duration.ZERO,
                    onClick = { onCare(type) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CareTile(
    type: RewardType,
    remaining: Duration,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bereit = remaining.isZero || remaining.isNegative
    val label = when (type) {
        RewardType.FEED -> R.string.pet_action_feed
        RewardType.PLAY -> R.string.pet_action_play
        else -> R.string.pet_action_pat
    }
    val icon: ImageVector = when (type) {
        RewardType.FEED -> Icons.Filled.Restaurant
        RewardType.PLAY -> Icons.Filled.SportsEsports
        else -> Icons.Filled.Favorite
    }

    val interaktion = remember { MutableInteractionSource() }
    val gedrueckt by interaktion.collectIsPressedAsState()
    val groesse by animateFloatAsState(
        targetValue = if (gedrueckt) 0.95f else 1f,
        animationSpec = Motion.quick(),
        label = "kachelDruck",
    )

    GlassCard(
        modifier = modifier.graphicsLayer {
            scaleX = groesse
            scaleY = groesse
        },
        shape = MaterialTheme.shapes.large,
        onClick = if (bereit) onClick else null,
        interactionSource = interaktion,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (bereit) Palette.Magenta else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                color = if (bereit) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
            AnimatedContent(
                targetState = if (bereit) "" else stringResource(R.string.pet_action_locked, formatCooldown(remaining)),
                transitionSpec = { fadeIn(Motion.standard()).togetherWith(fadeOut(Motion.quick())) },
                label = "sperrzeit",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Warum es dem Pet gerade so geht — ohne diese Karte wirkt der Verfall willkürlich. */
@Composable
private fun LoadCard(load: Double, overdueCount: Int) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(
                text = stringResource(R.string.pet_load_title),
                accent = if (overdueCount == 0) Palette.Lime else Palette.Amber,
            )

            if (overdueCount == 0) {
                Text(
                    text = stringResource(R.string.pet_load_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.pet_load_some,
                        overdueCount,
                        stringResource(R.string.pet_load_factor, OverdueLoad.baseMultiplier(load)),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Die Last als Balken: Sie läuft von 0 bis zum Deckel, und man sieht,
                // wie weit es noch bis "so schnell wie es überhaupt geht" ist.
                ValueTrack(
                    fraction = (load / 10.0).toFloat(),
                    colors = Brand.Overdue,
                    height = 6.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
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
