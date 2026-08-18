package uk.spielerbohne.petodo.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import uk.spielerbohne.petodo.ui.pet.PetRoute
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
) {
    TODAY("today", R.string.nav_today, Icons.Filled.CheckCircle),
    FOCUS("focus", R.string.nav_focus, Icons.Filled.Timer),
    PET("pet", R.string.nav_pet, Icons.Filled.Pets),
    MORE("more", R.string.nav_more, Icons.Filled.MoreHoriz),
}

@Composable
fun PetodoApp(container: AppContainer) {
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

    MainScaffold(container = container, onOpenPermissions = { showOnboarding = true })
}

@Composable
private fun MainScaffold(container: AppContainer, onOpenPermissions: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.TODAY.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.TODAY.route) {
                TodayRoute(
                    container = container,
                    onOpenTask = { taskId -> navController.navigate("task/$taskId") },
                    onSearch = { navController.navigate("browse?search=true") },
                    onOpenPet = {
                        navController.navigate(TopLevelDestination.PET.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
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
                )
            }
        }
    }
}
