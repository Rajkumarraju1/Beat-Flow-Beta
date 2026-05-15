package com.pralayakaveri.orbitmusic.presentation.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.pralayakaveri.orbitmusic.presentation.navigation.NavGraph
import com.pralayakaveri.orbitmusic.presentation.player.MiniPlayer
import com.pralayakaveri.orbitmusic.presentation.player.PlayerScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (granted) {
            onPermissionGranted()
        }
    }

    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            onPermissionGranted()
        } else {
            launcher.launch(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                } else {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            )
        }
    }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val currentSong by mainViewModel.currentSong.collectAsState()
    val isPlaying by mainViewModel.isPlaying.collectAsState()
    val currentPosition by mainViewModel.currentPosition.collectAsState()
    val customArtworks by mainViewModel.customArtworks.collectAsState()
    var showPlayerSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val showMiniPlayer = currentSong != null && currentRoute != "galaxy"

    val snackbarHostState = remember { SnackbarHostState() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    var miniPlayerHeightPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val miniPlayerHeightDp = remember(miniPlayerHeightPx) { with(density) { miniPlayerHeightPx.toDp() } }

    LaunchedEffect(Unit) {
        mainViewModel.errorEvents.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Retry",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                mainViewModel.forceRescan()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = androidx.compose.ui.graphics.Color.Transparent, // Allow background to show
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Persistent NavGraph (Always Full Screen, No Padding Shifts)
            Box(modifier = Modifier.fillMaxSize()) {
                NavGraph(
                    navController = navController,
                    mainViewModel = mainViewModel
                )
            }
            
            // 2. Floating Overlay MiniPlayer (Sits ABOVE NavGraph, does NOT affect layout)
            androidx.compose.animation.AnimatedVisibility(
                visible = showMiniPlayer,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 8.dp, end = 8.dp, bottom = 0.dp) // Corrected explicit padding syntax
                ) {
                    MiniPlayer(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            miniPlayerHeightPx = coordinates.size.height
                        },
                        song = currentSong!!,
                        customArtworkUri = customArtworks[currentSong!!.id],
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        onPlayPause = { mainViewModel.togglePlayPause() },
                        onExpand = { showPlayerSheet = true },
                        onNext = { mainViewModel.skipToNext() },
                        onPrevious = { mainViewModel.skipToPrevious() },
                        enabled = showMiniPlayer
                    )
                }
            }
        }
    }

    if (showPlayerSheet && currentSong != null) {
        ModalBottomSheet(
            onDismissRequest = { showPlayerSheet = false },
            sheetState = sheetState,
            contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) },
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
