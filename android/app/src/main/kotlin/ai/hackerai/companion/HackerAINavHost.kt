package ai.hackerai.companion

import ai.hackerai.companion.llm.HttpLocalLlmProvider
import ai.hackerai.companion.shizuku.ShizukuManager
import ai.hackerai.companion.ui.ShizukuScreen
import ai.hackerai.companion.ui.SettingsScreen
import ai.hackerai.companion.ui.SkillBrowserScreen
import ai.hackerai.companion.ui.StatusScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private const val ROUTE_STATUS = "status"
private const val ROUTE_SKILLS = "skills"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_PRIVILEGED = "privileged"

@Composable
fun HackerAINavHost(
    isDcaConnected: Boolean = true,
    llmProvider: HttpLocalLlmProvider,
    shizukuManager: ShizukuManager,
    onRequestShizukuPermission: () -> Unit,
) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Status") },
                    label = { Text("Status") },
                    selected = currentDestination?.hierarchy?.any { it.route == ROUTE_STATUS } == true,
                    onClick = {
                        navController.navigate(ROUTE_STATUS) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.List, contentDescription = "Skills") },
                    label = { Text("Skills") },
                    selected = currentDestination?.hierarchy?.any { it.route == ROUTE_SKILLS } == true,
                    onClick = {
                        navController.navigate(ROUTE_SKILLS) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = currentDestination?.hierarchy?.any { it.route == ROUTE_SETTINGS } == true,
                    onClick = {
                        navController.navigate(ROUTE_SETTINGS) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Build, contentDescription = "Privileged") },
                    label = { Text("Shizuku") },
                    selected = currentDestination?.hierarchy?.any { it.route == ROUTE_PRIVILEGED } == true,
                    onClick = {
                        navController.navigate(ROUTE_PRIVILEGED) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_STATUS,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ROUTE_STATUS) {
                StatusScreen(isDcaConnected = isDcaConnected)
            }
            composable(ROUTE_SKILLS) {
                SkillBrowserScreen()
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    currentApiKey = llmProvider.getApiKey(),
                    currentModel = llmProvider.getModel(),
                    currentEndpoint = llmProvider.getEndpoint(),
                    onSaveApiKey = { llmProvider.setApiKey(it) },
                    onClearApiKey = { llmProvider.clearApiKey() },
                    onSaveModel = { llmProvider.setModel(it) },
                    onSaveEndpoint = { llmProvider.setEndpoint(it) },
                )
            }
            composable(ROUTE_PRIVILEGED) {
                val shizukuState = shizukuManager.state.collectAsState()
                ShizukuScreen(
                    stateFlow = shizukuState,
                    onRequestPermission = onRequestShizukuPermission,
                    onRefresh = { shizukuManager.refreshState() },
                    onExec = { shizukuManager.exec(it) },
                )
            }
        }
    }
}

@Composable
fun DcaRequiredScreen() {
    StatusScreen(isDcaConnected = false)
}
