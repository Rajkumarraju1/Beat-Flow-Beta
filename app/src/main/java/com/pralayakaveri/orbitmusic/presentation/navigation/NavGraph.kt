package com.pralayakaveri.orbitmusic.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.pralayakaveri.orbitmusic.presentation.home.HomeScreen
import com.pralayakaveri.orbitmusic.presentation.main.MainViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    mainViewModel: MainViewModel
) {
    NavHost(
        modifier = Modifier.fillMaxSize(),
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                mainViewModel = mainViewModel,
                onSongClick = { songs, index ->
                    mainViewModel.playSongs(songs, index)
                },
                onCollectionClick = { title, songs, type ->
                    mainViewModel.selectCollection(title, songs, type)
                },
                onGalaxyClick = { 
                    if (navController.currentDestination?.route != "galaxy") {
                        navController.navigate("galaxy") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onInsightsClick = { 
                    if (navController.currentDestination?.route != "insights") {
                        navController.navigate("insights") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onSettingsClick = { 
                    if (navController.currentDestination?.route != "settings") {
                        navController.navigate("settings") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
        composable("settings") {
            com.pralayakaveri.orbitmusic.presentation.settings.SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("galaxy") {
            com.pralayakaveri.orbitmusic.presentation.galaxy.GalaxyScreen(
                onNavigateBack = { navController.popBackStack() },
                onPlaySong = { song ->
                    mainViewModel.playSongs(listOf(song), 0)
                },
                mainViewModel = mainViewModel
            )
        }
        composable("insights") {
            com.pralayakaveri.orbitmusic.presentation.insights.InsightsScreen(
                onNavigateBack = { navController.popBackStack() },
                onPlaySong = { song ->
                    mainViewModel.playSongs(listOf(song), 0)
                }
            )
        }
    }
}
