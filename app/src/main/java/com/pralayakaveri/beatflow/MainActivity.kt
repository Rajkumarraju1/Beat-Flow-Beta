package com.pralayakaveri.beatflow

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.pralayakaveri.beatflow.presentation.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        
        if (storageGranted) {
            mainViewModel.startInitialSync()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestStoragePermissions()

        // Handle Edge-to-Edge
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            val currentTheme by mainViewModel.currentTheme.collectAsState()
            
            // Handle System Bar Contrast
            val isDarkTheme = when (currentTheme) {
                com.pralayakaveri.beatflow.presentation.theme.ThemeType.MINIMAL -> false
                else -> true
            }
            
            androidx.compose.runtime.SideEffect {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !isDarkTheme
                controller.isAppearanceLightNavigationBars = !isDarkTheme
            }

            val colorScheme = when (currentTheme) {
                com.pralayakaveri.beatflow.presentation.theme.ThemeType.DYNAMIC -> androidx.compose.material3.darkColorScheme()
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
                    com.pralayakaveri.beatflow.presentation.main.MainScreen(mainViewModel = mainViewModel)
                }
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        mainViewModel.setUiVisibility(true)
    }

    override fun onStop() {
        super.onStop()
        mainViewModel.setUiVisibility(false)
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