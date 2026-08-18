package uk.spielerbohne.petodo.ui.pet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.data.pet.labelRes
import uk.spielerbohne.petodo.di.AppContainer

/**
 * Der Pet-Streifen über der Heute-Liste.
 *
 * Er ist der Grund, warum das Pet nicht zur Nebensache wird: Man sieht beim Abhaken,
 * wem man damit hilft. Ein Fingertipp führt zur Statustafel.
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

    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = pet.stage.emoji(), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = name + " · " + stringResource(pet.stage.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.pet_level, pet.level),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ThinBar(pet.values.energy, Modifier.weight(1f))
                    ThinBar(pet.values.satiety, Modifier.weight(1f))
                    ThinBar(pet.values.mood, Modifier.weight(1f))
                }
            }

            androidx.compose.material3.TextButton(onClick = onOpen) {
                Text(stringResource(R.string.pet_title))
            }
        }
    }
}

@Composable
private fun ThinBar(value: Double, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { (value / 100.0).toFloat() },
        modifier = modifier.height(4.dp),
    )
}
