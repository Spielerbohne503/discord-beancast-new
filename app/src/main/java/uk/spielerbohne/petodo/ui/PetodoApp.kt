package uk.spielerbohne.petodo.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import uk.spielerbohne.petodo.ui.theme.Motion
import uk.spielerbohne.petodo.ui.theme.Palette
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.di.AppContainer
import androidx.navigation.NavType
import androidx.navigation.navArgument
import uk.spielerbohne.petodo.domain.filter.TaskScope
import uk.spielerbohne.petodo.ui.browse.BrowseRoute
import uk.spielerbohne.petodo.ui.detail.TaskDetailRoute
import uk.spielerbohne.petodo.ui.focus.FocusRoute
import uk.spielerbohne.petodo.ui.more.MoreRoute
import uk.spielerbohne.petodo.ui.onboarding.OnboardingScreen
import uk.spielerbohne.petodo.ui.habits.HabitsRoute
import uk.spielerbohne.petodo.ui.pet.PetRoute
import uk.spielerbohne.petodo.ui.stats.StatsRoute
import uk.spielerbohne.petodo.ui.today.TodayRoute

/**
 * Untere Leiste mit vier Einträgen. Sie steht seit Phase 1 unverändert da — die Einträge
 * für Fokus und Pet waren erst Platzhalter, die Navigation musste dafür nicht umgebaut
 * werden.
 */
private enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    /** Jeder Bereich hat seine Farbe — dieselbe wie auf seinen Karten. */
    val accent: Color,
) {
    TODAY("today", R.string.nav_today, Icons.Filled.CheckCircle, Palette.Sky),
    FOCUS("focus", R.string.nav_focus, Icons.Filled.Timer, Palette.Indigo),
    PET("pet", R.string.nav_pet, Icons.Filled.Pets, Palette.Magenta),
    MORE("more", R.string.nav_more, Icons.Filled.MoreHoriz, Palette.ChalkMuted),
}

@Composable
fun PetodoApp(
    container: AppContainer,
    /** Aus der Statuszeile: Das Eingabefeld soll gleich den Finger bekommen. */
    quickAdd: Boolean = false,
    onQuickAddConsumed: () -> Unit = {},
) {
    val onboardingCompleted by container.settingsRepository.onboardingCompleted
        .collectAsStateWithLifecycle(initialValue = true)
    val scope = rememberCoroutineScope()

    // Beim ersten Start erklären, wovon es abhängt, ob eine Erinnerung ankommt.
    var showOnboarding by remember { mutableStateOf(false) }
    LaunchedEffect(onboardingCompleted) {
        if (!onboardingCompleted) showOnboarding = true
    }

    if (showOnboarding) {
        OnboardingScreen(
            onFinished = {
                showOnboarding = false
                scope.launch { container.settingsRepository.setOnboardingCompleted(true) }
            }
        )
        return
    }

    MainScaffold(
        container = container,
        onOpenPermissions = { showOnboarding = true },
        quickAdd = quickAdd,
        onQuickAddConsumed = onQuickAddConsumed,
    )
}

@Composable
private fun MainScaffold(
    container: AppContainer,
    onOpenPermissions: () -> Unit,
    quickAdd: Boolean,
    onQuickAddConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomBar(
                current = currentDestination,
                onSelect = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.TODAY.route,
            modifier = Modifier.padding(innerPadding),
            // Die vier Bereiche liegen nebeneinander, nicht hintereinander: Sie blenden
            // ineinander über, statt sich seitlich zu schieben. Ein Schiebe-Übergang
            // zwischen gleichrangigen Bildschirmen behauptet eine Richtung, die es nicht
            // gibt — die Detailseite unten schiebt dagegen sehr wohl.
            enterTransition = { fadeIn(Motion.standard()) },
            exitTransition = { fadeOut(Motion.quick()) },
            popEnterTransition = { fadeIn(Motion.standard()) },
            popExitTransition = { fadeOut(Motion.quick()) },
        ) {
            composable(TopLevelDestination.TODAY.route) {
                TodayRoute(
                    container = container,
                    quickAdd = quickAdd,
                    onQuickAddConsumed = onQuickAddConsumed,
                    onOpenTask = { taskId -> navController.navigate("task/$taskId") },
                    onSearch = { navController.navigate("browse?search=true") },
                    onOpenPet = {
                        navController.navigate(TopLevelDestination.PET.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenHabits = { navController.navigate("habits") },
                )
            }
            composable(
                route = "browse?listId={listId}&search={search}",
                arguments = listOf(
                    navArgument("listId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("search") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val listId = entry.arguments?.getString("listId")
                BrowseRoute(
                    container = container,
                    initialScope = listId?.let(TaskScope::InList) ?: TaskScope.AllOpen,
                    startInSearch = entry.arguments?.getString("search") == "true",
                    onOpenTask = { taskId -> navController.navigate("task/$taskId") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "task/{taskId}",
                arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
                enterTransition = {
                    slideInHorizontally(Motion.standard()) { breite -> breite / 4 } +
                        fadeIn(Motion.standard())
                },
                popExitTransition = {
                    slideOutHorizontally(Motion.standard()) { breite -> breite / 4 } +
                        fadeOut(Motion.quick())
                },
            ) { entry ->
                TaskDetailRoute(
                    container = container,
                    taskId = entry.arguments?.getString("taskId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(TopLevelDestination.FOCUS.route) {
                FocusRoute(container = container)
            }
            composable(TopLevelDestination.PET.route) {
                PetRoute(container = container)
            }
            composable(TopLevelDestination.MORE.route) {
                MoreRoute(
                    container = container,
                    onOpenPermissions = onOpenPermissions,
                    onOpenList = { listId -> navController.navigate("browse?listId=$listId") },
                    onOpenStats = { navController.navigate("stats") },
                    onOpenHabits = { navController.navigate("habits") },
                )
            }
            composable(
                route = "habits",
                enterTransition = {
                    slideInHorizontally(Motion.standard()) { breite -> breite / 4 } +
                        fadeIn(Motion.standard())
                },
                popExitTransition = {
                    slideOutHorizontally(Motion.standard()) { breite -> breite / 4 } +
                        fadeOut(Motion.quick())
                },
            ) {
                HabitsRoute(container = container, onBack = { navController.popBackStack() })
            }
            composable(
                route = "stats",
                enterTransition = {
                    slideInHorizontally(Motion.standard()) { breite -> breite / 4 } +
                        fadeIn(Motion.standard())
                },
                popExitTransition = {
                    slideOutHorizontally(Motion.standard()) { breite -> breite / 4 } +
                        fadeOut(Motion.quick())
                },
            ) {
                StatsRoute(container = container, onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Die untere Leiste schwebt über dem Inhalt statt ihn abzuschneiden.
 *
 * Der ausgewählte Eintrag bekommt eine Kapsel in seiner Bereichsfarbe — so weiß man
 * ohne Hinsehen, wo man ist, und die Farbe stimmt mit den Karten darunter überein.
 */
@Composable
private fun BottomBar(current: NavDestination?, onSelect: (TopLevelDestination) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    MaterialTheme.shapes.extraLarge,
                )
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val selected = current?.hierarchy?.any { it.route == destination.route } == true
                BottomBarItem(
                    destination = destination,
                    selected = selected,
                    onClick = { onSelect(destination) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = destination.accent
    val background by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = Motion.standard(),
        label = "navHintergrund",
    )
    val content by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = Motion.standard(),
        label = "navInhalt",
    )

    // Das Zeichen wächst beim Wechsel kurz an. Ohne diese Bewegung ist ein Wechsel der
    // Leiste nur ein Farbwechsel und geht im Augenwinkel unter.
    val groesse by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = Motion.bouncy(),
        label = "navZeichen",
    )

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = groesse
                    scaleY = groesse
                },
        )
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}
