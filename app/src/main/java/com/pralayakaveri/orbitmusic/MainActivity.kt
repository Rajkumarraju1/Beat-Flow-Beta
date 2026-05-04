package com.pralayakaveri.orbitmusic

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
import com.pralayakaveri.orbitmusic.presentation.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.d("MainActivity", "MainActivity onCreate")
        super.onCreate(savedInstanceState)
        
        // Handle Edge-to-Edge
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        setContent {
            val currentTheme by mainViewModel.currentTheme.collectAsState()
            
            // Handle System Bar Contrast
            val isDarkTheme = when (currentTheme) {
                com.pralayakaveri.orbitmusic.presentation.theme.ThemeType.MINIMAL -> false
                else -> true
            }
            
            androidx.compose.runtime.SideEffect {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !isDarkTheme
                controller.isAppearanceLightNavigationBars = !isDarkTheme
            }

            val colorScheme = when (currentTheme) {
                com.pralayakaveri.orbitmusic.presentation.theme.ThemeType.DYNAMIC -> androidx.compose.material3.darkColorScheme()
                com.pralayakaveri.orbitmusic.presentation.theme.ThemeType.AMOLED_DARK -> androidx.compose.material3.darkColorScheme(
                    background = androidx.compose.ui.graphics.Color.Black,
                    surface = androidx.compose.ui.graphics.Color.Black,
                    onBackground = androidx.compose.ui.graphics.Color.White,
                    onSurface = androidx.compose.ui.graphics.Color.White,
                    primary = androidx.compose.ui.graphics.Color(0xFFBB86FC)
                )
                com.pralayakaveri.orbitmusic.presentation.theme.ThemeType.NEON -> androidx.compose.material3.darkColorScheme(
                    background = androidx.compose.ui.graphics.Color(0xFF0F0F1A),
                    surface = androidx.compose.ui.graphics.Color(0xFF19192D),
                    primary = androidx.compose.ui.graphics.Color(0xFF00FFCC),
                    secondary = androidx.compose.ui.graphics.Color(0xFFFF00FF),
                    onBackground = androidx.compose.ui.graphics.Color.White
                )
                com.pralayakaveri.orbitmusic.presentation.theme.ThemeType.MINIMAL -> androidx.compose.material3.lightColorScheme(
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
                    com.pralayakaveri.orbitmusic.presentation.main.MainScreen(
                        mainViewModel = mainViewModel,
                        onPermissionGranted = {
                            mainViewModel.startInitialSync()
                        }
                    )
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

    override fun onDestroy() {
        android.util.Log.d("MainActivity", "MainActivity onDestroy")
        super.onDestroy()
    }
}
