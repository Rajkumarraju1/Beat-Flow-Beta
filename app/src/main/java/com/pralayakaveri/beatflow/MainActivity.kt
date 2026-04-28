package com.pralayakaveri.beatflow

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.Tune
import com.pralayakaveri.beatflow.presentation.home.HomeScreen
import com.pralayakaveri.beatflow.presentation.main.MainViewModel
import com.pralayakaveri.beatflow.presentation.player.MiniPlayer
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Could refresh list here if granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestStoragePermissions()

        setContent {
            val currentTheme by mainViewModel.currentTheme.collectAsState()
            val colorScheme = when (currentTheme) {
                com.pralayakaveri.beatflow.presentation.theme.ThemeType.DYNAMIC -> androidx.compose.material3.darkColorScheme() // Handled dynamically in components via Palette
                com.pralayakaveri.beatflow.presentation.theme.ThemeType.AMOLED_DARK -> androidx.compose.material3.darkColorScheme(
                    background = androidx.compose.ui.graphics.Color.Black,
                    surface = androidx.compose.ui.graphics.Color.Black,
                    onBackground = androidx.compose.ui.graphics.Color.White,
                    onSurface = androidx.compose.ui.graphics.Color.White,
                    primary = androidx.compose.ui.graphics.Color(0xFFBB86FC)
                )
                com.pralayakaveri.beatflow.presentation.theme.ThemeType.NEON -> androidx.compose.material3.darkColorScheme(
                    background = androidx.compose.ui.graphics.Color(0xFF0F0F1A),
                    surface = androidx.compose.ui.graphics.Color(0xFF19192D),
                    primary = androidx.compose.ui.graphics.Color(0xFF00FFCC),
                    secondary = androidx.compose.ui.graphics.Color(0xFFFF00FF),
                    onBackground = androidx.compose.ui.graphics.Color.White
                )
                com.pralayakaveri.beatflow.presentation.theme.ThemeType.MINIMAL -> androidx.compose.material3.lightColorScheme(
                    background = androidx.compose.ui.graphics.Color(0xFFF9F9F9),
                    surface = androidx.compose.ui.graphics.Color.White,
                    primary = androidx.compose.ui.graphics.Color(0xFF111111),
                    onBackground = androidx.compose.ui.graphics.Color.Black,
                    onSurface = androidx.compose.ui.graphics.Color.Black
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentSong by mainViewModel.currentSong.collectAsState()
                    val isPlaying by mainViewModel.isPlaying.collectAsState()
                    val currentPosition by mainViewModel.currentPosition.collectAsState()

                    val navController = androidx.navigation.compose.rememberNavController()

                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) {
                            androidx.navigation.compose.NavHost(
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
                                    com.pralayakaveri.beatflow.presentation.settings.SettingsScreen(
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                                composable("galaxy") {
                                    com.pralayakaveri.beatflow.presentation.galaxy.GalaxyScreen(
                                        onNavigateBack = { navController.popBackStack() },
                                        onPlaySong = { song -> 
                                            mainViewModel.playSongs(listOf(song), 0)
                                            navController.navigate("player")
                                        }
                                    )
                                }
                                composable("insights") {
                                    com.pralayakaveri.beatflow.presentation.insights.InsightsScreen(
                                        onNavigateBack = { navController.popBackStack() },
                                        onPlaySong = { song -> 
                                            mainViewModel.playSongs(listOf(song), 0)
                                            navController.navigate("player")
                                        }
                                    )
                                }

                                composable("player") {
                                    val currentPosition by mainViewModel.currentPosition.collectAsState()
                                    currentSong?.let { song ->
                                        com.pralayakaveri.beatflow.presentation.player.PlayerScreen(
                                            song = song,
                                            mainViewModel = mainViewModel,
                                            isPlaying = isPlaying,
                                            currentPosition = currentPosition,
                                            onPlayPause = { mainViewModel.togglePlayPause() },
                                            onNext = { mainViewModel.skipToNext() },
                                            onPrevious = { mainViewModel.skipToPrevious() },
                                            onSeek = { position -> mainViewModel.seekTo(position) },
                                            onCollapse = { navController.popBackStack() }
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Hide MiniPlayer when we are on the PlayerScreen
                        val navBackStackEntry by navController.currentBackStackEntryFlow.collectAsState(initial = null)
                        val currentRoute = navBackStackEntry?.destination?.route

                        if (currentRoute != "player") {
                            currentSong?.let { song ->
                                val customArtworks by mainViewModel.customArtworks.collectAsState()
                                MiniPlayer(
                                    song = song,
                                    customArtworkUri = customArtworks[song.id],
                                    isPlaying = isPlaying,
                                    currentPosition = currentPosition,
                                    onPlayPause = { mainViewModel.togglePlayPause() },
                                    onExpand = { navController.navigate("player") },
                                    onNext = { mainViewModel.skipToNext() },
                                    onPrevious = { mainViewModel.skipToPrevious() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun requestStoragePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissions.add(Manifest.permission.RECORD_AUDIO)
        permissionLauncher.launch(permissions.toTypedArray())
    }
}