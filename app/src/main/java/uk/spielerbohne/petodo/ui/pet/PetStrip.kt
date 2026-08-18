package uk.spielerbohne.petodo.ui.pet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.data.pet.labelRes
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.ui.theme.GradientCard
import uk.spielerbohne.petodo.ui.theme.Palette
import uk.spielerbohne.petodo.ui.theme.ValueTrack

/**
 * Der Pet-Streifen über der Heute-Liste.
 *
 * Er ist der Grund, warum das Pet nicht zur Nebensache wird: Man sieht beim Abhaken,
 * wem man damit hilft. Dieselbe Verlaufskarte wie auf der Statustafel, nur flach —
 * eine zweite Bildsprache für dasselbe Ding wäre eine verpasste Gelegenheit.
 */
@Composable
fun PetStrip(container: AppContainer, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: PetViewModel = viewModel(
        factory = PetViewModel.factory(container),
        key = "pet-strip",
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pet = state.snapshot.state
    val name = state.name.ifBlank { stringResource(R.string.pet_default_name) }

    // Derselbe Puls wie auf der Statustafel: Man hakt eine Aufgabe ab und sieht hier,
    // direkt über der Liste, dass es angekommen ist.
    val puls = rememberGainPulse(pet.values.average)

    GradientCard(
        colors = pet.stage.gradient(),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val wachstum = 1f + 0.02f * puls
                scaleX = wachstum
                scaleY = wachstum
            },
        shape = MaterialTheme.shapes.large,
        onClick = onOpen,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(Color.White.copy(alpha = 0.22f * puls))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = pet.stage.emoji(), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.Chalk,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.28f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.pet_level, pet.level),
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Chalk,
                        )
                    }
                }
                Text(
                    text = stringResource(pet.stage.labelRes) + " · " +
                        stringResource(R.string.pet_value_percent, pet.values.average.toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Chalk.copy(alpha = 0.8f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ThinBar(pet.values.energy, Modifier.weight(1f))
                    ThinBar(pet.values.satiety, Modifier.weight(1f))
                    ThinBar(pet.values.mood, Modifier.weight(1f))
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.pet_title),
                tint = Palette.Chalk.copy(alpha = 0.8f),
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(18.dp),
            )
        }
    }
}

@Composable
private fun ThinBar(value: Double, modifier: Modifier = Modifier) {
    ValueTrack(
        fraction = (value / 100.0).toFloat(),
        colors = listOf(Palette.Chalk, Palette.Chalk.copy(alpha = 0.8f)),
        modifier = modifier,
        height = 4.dp,
        trackColor = Color.Black.copy(alpha = 0.25f),
    )
}
