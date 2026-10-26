package fr.openfbx.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.openfbx.core.AppGraph
import fr.openfbx.core.model.BoxConfig
import fr.openfbx.ui.components.LoadingPane
import fr.openfbx.ui.downloads.DownloadsScreen
import fr.openfbx.ui.files.FilesScreen
import fr.openfbx.ui.graphs.GraphsScreen
import fr.openfbx.ui.home.HomeScreen
import fr.openfbx.ui.network.NetworkScreen
import fr.openfbx.ui.settings.SettingsScreen
import fr.openfbx.ui.setup.SetupFlow
import fr.openfbx.ui.system.SystemScreen
import fr.openfbx.ui.wifi.WifiScreen
import kotlinx.coroutines.flow.combine

private enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Accueil", Icons.Filled.Home),
    DOWNLOADS("downloads", "Téléch.", Icons.Filled.Download),
    FILES("files", "Fichiers", Icons.Filled.Folder),
    NETWORK("network", "Réseau", Icons.Filled.Router),
    SETTINGS("settings", "Réglages", Icons.Filled.Settings),
}

private const val SYSTEM_ROUTE = "system"
private const val WIFI_ROUTE = "wifi"
private const val GRAPHS_ROUTE = "graphs"

private sealed interface ConfigState {
    data object Loading : ConfigState
    data object MissingBox : ConfigState
    data class NeedsPairing(val config: BoxConfig) : ConfigState
    data class Ready(val config: BoxConfig) : ConfigState
}

@Composable
fun OpenFbxRoot() {
    val stateFlow = remember {
        combine(AppGraph.repository.boxConfig, AppGraph.store.hasAppTokenFlow) { config, hasToken ->
            when {
                config == null -> ConfigState.MissingBox
                !hasToken -> ConfigState.NeedsPairing(config)
                else -> ConfigState.Ready(config)
            }
        }
    }
    val state by stateFlow.collectAsStateWithLifecycle(initialValue = ConfigState.Loading)

    when (val current = state) {
        ConfigState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingPane("Ouverture d'OpenFbx…")
        }
        ConfigState.MissingBox -> SetupFlow(pairingRequired = false)
        is ConfigState.NeedsPairing -> SetupFlow(pairingRequired = true)
        is ConfigState.Ready -> MainShell(boxName = current.config.displayName)
    }
}

@Composable
private fun MainShell(boxName: String) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Dest.HOME.route

    fun navigate(dest: Dest) {
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (expanded) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    Spacer(Modifier.height(12.dp))
                    Dest.entries.forEach { dest ->
                        NavigationRailItem(
                            selected = currentRoute == dest.route,
                            onClick = { navigate(dest) },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(dest.label, textAlign = TextAlign.Center) },
                        )
                    }
                }
                Spacer(Modifier.width(1.dp))
            }

            Scaffold(
                modifier = Modifier.fillMaxSize().weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (!expanded) {
                        NavigationBar {
                            Dest.entries.forEach { dest ->
                                NavigationBarItem(
                                    selected = currentRoute == dest.route,
                                    onClick = { navigate(dest) },
                                    icon = { Icon(dest.icon, contentDescription = null) },
                                    label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = Dest.HOME.route,
                    modifier = Modifier.padding(padding),
                ) {
                    composable(Dest.HOME.route) {
                        HomeScreen(
                            boxName = boxName,
                            onOpenSystem = { navController.navigate(SYSTEM_ROUTE) },
                            onOpenWifi = { navController.navigate(WIFI_ROUTE) },
                            onOpenGraphs = { navController.navigate(GRAPHS_ROUTE) },
                        )
                    }
                    composable(Dest.DOWNLOADS.route) { DownloadsScreen() }
                    composable(Dest.FILES.route) { FilesScreen() }
                    composable(Dest.NETWORK.route) { NetworkScreen() }
                    composable(Dest.SETTINGS.route) { SettingsScreen() }
                    composable(SYSTEM_ROUTE) {
                        SystemScreen(onBack = { navController.popBackStack() })
                    }
                    composable(WIFI_ROUTE) {
                        WifiScreen(onBack = { navController.popBackStack() })
                    }
                    composable(GRAPHS_ROUTE) {
                        GraphsScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
