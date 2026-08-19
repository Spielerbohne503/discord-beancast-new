package uk.spielerbohne.petodo.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.stats.DayCount
import uk.spielerbohne.petodo.domain.stats.Stats
import uk.spielerbohne.petodo.ui.theme.Brand
import uk.spielerbohne.petodo.ui.theme.GlassCard
import uk.spielerbohne.petodo.ui.theme.GradientCard
import uk.spielerbohne.petodo.ui.theme.Motion
import uk.spielerbohne.petodo.ui.theme.Palette
import uk.spielerbohne.petodo.ui.theme.ScreenGlow
import uk.spielerbohne.petodo.ui.theme.SectionLabel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun StatsRoute(container: AppContainer, onBack: () -> Unit) {
    val viewModel: StatsViewModel = viewModel(factory = StatsViewModel.factory(container))
    val stats by viewModel.state.collectAsStateWithLifecycle()

    StatsScreen(stats = stats, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsScreen(stats: Stats, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            ScreenGlow(colors = Brand.Cool, alpha = 0.18f, modifier = Modifier.align(Alignment.TopCenter))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("serie") { StreakCard(stats) }
                item("diagramm") { HistoryCard(stats) }
                item("summen") { TotalsRow(stats) }

                if (stats.totalCompleted == 0) {
                    item("leer") {
                        Text(
                            text = stringResource(R.string.stats_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Die Serie.
 *
 * Sie steht oben und groß, weil sie das Einzige ist, was über einen einzelnen Tag hinaus
 * etwas erzählt — und weil sie der Grund ist, morgen wieder aufzumachen.
 */
@Composable
private fun StreakCard(stats: Stats) {
    GradientCard(colors = Brand.Pet, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.stats_streak_title).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Chalk.copy(alpha = 0.75f),
            )
            Text(
                text = if (stats.streak == 0) {
                    stringResource(R.string.stats_streak_none)
                } else {
                    pluralStringResource(R.plurals.stats_streak_days, stats.streak, stats.streak)
                },
                style = MaterialTheme.typography.displayMedium,
                color = Palette.Chalk,
            )
            if (stats.longestStreak > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.stats_longest,
                        stats.longestStreak,
                        stats.longestStreak,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Chalk.copy(alpha = 0.8f),
                )
            }
            Text(
                text = stringResource(R.string.stats_streak_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Chalk.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Vierzehn Balken, einer je Tag — die leeren gehören dazu. */
@Composable
private fun HistoryCard(stats: Stats) {
    val hoechster = stats.days.maxOfOrNull { it.count } ?: 0

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel(text = stringResource(R.string.stats_two_weeks), accent = Palette.Sky)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                stats.days.forEach { tag ->
                    DayBar(day = tag, max = hoechster, modifier = Modifier.weight(1f))
                }
            }

            Text(
                text = stringResource(R.string.stats_period_total, stats.periodCompleted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            stats.busiestDay?.let { bester ->
                Text(
                    text = stringResource(
                        R.string.stats_busiest,
                        bester.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)),
                        bester.count,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DayBar(day: DayCount, max: Int, modifier: Modifier = Modifier) {
    // Ein leerer Tag behält einen Stummel: Sonst sieht die Lücke aus wie ein Fehler
    // im Diagramm statt wie ein Tag, an dem nichts war.
    val anteil = if (max <= 0) 0f else day.count.toFloat() / max.toFloat()
    val hoehe by animateFloatAsState(
        targetValue = MIN_ANTEIL + anteil * (1f - MIN_ANTEIL),
        animationSpec = Motion.slow(),
        label = "tagesbalken",
    )

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(hoehe)
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                    .background(
                        if (day.count == 0) {
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                )
                            )
                        } else {
                            Brush.verticalGradient(Brand.Cool)
                        }
                    )
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TotalsRow(stats: Stats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TotalTile(
            label = stringResource(R.string.stats_total_title),
            value = stats.totalCompleted,
            accent = Palette.Lime,
            modifier = Modifier.weight(1f),
        )
        TotalTile(
            label = stringResource(R.string.stats_focus_title),
            value = stats.focusRounds,
            accent = Palette.Indigo,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TotalTile(label: String, value: Int, accent: Color, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Ein leerer Tag ist trotzdem ein Tag — er behält diesen Anteil der Höhe. */
private const val MIN_ANTEIL = 0.04f
