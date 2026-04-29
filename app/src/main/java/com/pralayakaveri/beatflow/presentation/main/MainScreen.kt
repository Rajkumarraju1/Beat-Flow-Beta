package com.pralayakaveri.beatflow.presentation.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.pralayakaveri.beatflow.presentation.navigation.NavGraph
import com.pralayakaveri.beatflow.presentation.player.MiniPlayer
import com.pralayakaveri.beatflow.presentation.player.PlayerScreen
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel
) {
    val navController = rememberNavController()
    val currentSong by mainViewModel.currentSong.collectAsState()
    val isPlaying by mainViewModel.isPlaying.collectAsState()
    val currentPosition by mainViewModel.currentPosition.collectAsState()
    val customArtworks by mainViewModel.customArtworks.collectAsState()
    var showPlayerSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentSong != null) {
                MiniPlayer(
                    song = currentSong!!,
                    customArtworkUri = customArtworks[currentSong!!.id],
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    onPlayPause = { mainViewModel.togglePlayPause() },
                    onExpand = { showPlayerSheet = true },
                    onNext = { mainViewModel.skipToNext() },
                    onPrevious = { mainViewModel.skipToPrevious() }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            NavGraph(
                navController = navController,
                mainViewModel = mainViewModel
            )
        }
    }

        if (showPlayerSheet && currentSong != null) {
            ModalBottomSheet(
                onDismissRequest = { showPlayerSheet = false },
                sheetState = sheetState,
                dragHandle = null,
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                shape = androidx.compose.ui.graphics.RectangleShape
            ) {
                PlayerScreen(
                    song = currentSong!!,
                    mainViewModel = mainViewModel,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    onPlayPause = { mainViewModel.togglePlayPause() },
                    onNext = { mainViewModel.skipToNext() },
                    onPrevious = { mainViewModel.skipToPrevious() },
                    onSeek = { position -> mainViewModel.seekTo(position) },
                    onCollapse = { showPlayerSheet = false }
                )
        }
    }
}
