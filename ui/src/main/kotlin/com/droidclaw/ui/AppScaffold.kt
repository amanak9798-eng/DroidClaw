package com.droidclaw.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.droidclaw.ui.screens.*
import com.droidclaw.ui.theme.*
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.droidclaw.core.AppConfig

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Home",   Icons.Outlined.Dashboard)
    object Tasks     : Screen("tasks",     "Tasks",  Icons.Outlined.Assignment)
    object Tools     : Screen("tools",     "Tools",  Icons.Outlined.Build)
    object Chat      : Screen("chat",      "Chat",   Icons.Outlined.Terminal)
    object Memory    : Screen("memory",    "Memory", Icons.Outlined.Memory)
    object Settings  : Screen("settings",  "Config", Icons.Outlined.Settings)
    object Onboarding : Screen("onboarding", "Setup", Icons.Outlined.Dashboard)
    
    class TaskDetail(val taskId: String) : Screen("task_detail/{taskId}", "Task Detail", Icons.Outlined.Assignment) {
        companion object {
            const val ROUTE = "task_detail/{taskId}"
        }
    }
}

private val PrimaryNavItems = listOf(
    Screen.Chat,
    Screen.Dashboard,
    Screen.Settings
)

@Composable
fun AppScaffold(
    onFirstContentComposed: (() -> Unit)? = null,
    onRouteChanged: ((String) -> Unit)? = null,
    onNavItemClicked: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(AppConfig.PREFS_MAIN, Context.MODE_PRIVATE)
    val isSetup = prefs.getBoolean(AppConfig.PREFS_KEY_ONBOARDING_COMPLETE, false)
    
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    LaunchedEffect(Unit) {
        onFirstContentComposed?.invoke()
    }
    LaunchedEffect(currentRoute) {
        currentRoute?.let { onRouteChanged?.invoke(it) }
    }

    Scaffold(
        bottomBar = { 
            if (currentRoute != Screen.Onboarding.route) {
                BottomNavigationBar(navController, onNavItemClicked) 
            }
        },
        containerColor = BackgroundDark,
        contentColor = TextPrimary
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (isSetup) Screen.Chat.route else Screen.Onboarding.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Onboarding.route) { 
                OnboardingScreen(onComplete = {
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }) 
            }
            composable(Screen.Dashboard.route) { DashboardScreen() }
            composable(Screen.Tasks.route) { TasksScreen(navController = navController) }
            composable(
                route = Screen.TaskDetail.ROUTE,
                arguments = listOf(androidx.navigation.navArgument("taskId") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId") ?: return@composable
                TaskDetailScreen(taskId = taskId, onBack = { navController.popBackStack() })
            }
            composable(Screen.Tools.route) { ToolsScreen() }
            composable(Screen.Chat.route) { ChatScreen() }
            composable(Screen.Memory.route) { MemoryScreen() }
            composable(Screen.Settings.route) { SettingsScreen(navController = navController) }
        }
    }
}

@Composable
fun BottomNavigationBar(
    navController: NavHostController,
    onNavItemClicked: ((String) -> Unit)? = null
) {
    val items = PrimaryNavItems

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = SurfaceDark,
        contentColor = TextMuted,
        tonalElevation = 8.dp
    ) {
        items.forEach { screen ->
            val selected = currentRoute == screen.route
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title,
                        tint = if (selected) AccentCyan else TextMuted
                    )
                },
                label = { 
                    Text(
                        text = screen.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    ) 
                },
                selected = selected,
                onClick = {
                    onNavItemClicked?.invoke(screen.route)
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AccentCyan,
                    selectedTextColor = AccentCyan,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor = SurfaceDark
                )
            )
        }
    }
}
