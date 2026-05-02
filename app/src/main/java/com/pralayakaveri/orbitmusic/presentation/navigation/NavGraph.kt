package com.pralayakaveri.orbitmusic.presentation.navigation

import androidx.compose.runtime.Composable
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
                onGalaxyClick = { navController.navigate("galaxy") },
                onInsightsClick = { navController.navigate("insights") },
                onSettingsClick = { navController.navigate("settings") }
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
