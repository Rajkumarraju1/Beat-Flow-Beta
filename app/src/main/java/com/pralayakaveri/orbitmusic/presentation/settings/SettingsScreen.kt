package com.pralayakaveri.orbitmusic.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pralayakaveri.orbitmusic.presentation.components.AppBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val uriHandler = LocalUriHandler.current
    val filterPrefs by viewModel.filterPreferences.collectAsState()
    val useReducedMotion by viewModel.useReducedMotion.collectAsState()
    var showRescanDialog by remember { mutableStateOf(false) }

    if (showRescanDialog) {
        AlertDialog(
            onDismissRequest = { showRescanDialog = false },
            title = { Text("Force Library Rescan?") },
            text = { Text("This will clear the existing library index and re-scan all folders. This may take a few minutes and will consume battery.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.forceRescan()
                        showRescanDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF42C6B9))
                ) {
                    Text("Rescan Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRescanDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E24),
            titleContentColor = Color.White,
            textContentColor = Color.Gray
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AppBackground()
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Top Level: Accessibility & Motion
                item {
                    SettingSwitchItem(
                        icon = Icons.Default.MotionPhotosAuto,
                        title = "Reduced Motion",
                        subtitle = "Simplify animations and disable nebula warps",
                        checked = useReducedMotion,
                        onCheckedChange = { viewModel.updateReducedMotion(it) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    SettingsHeader("Library & Scanning")
                    SettingSwitchItem(
                        icon = Icons.Default.Timer,
                        title = "Filter Short Tracks",
                        subtitle = "Hide tracks shorter than 60 seconds",
                        checked = filterPrefs?.minDurationEnabled ?: true,
                        onCheckedChange = { viewModel.updateMinDurationEnabled(it) }
                    )
                    SettingSwitchItem(
                        icon = Icons.Default.SdCard,
                        title = "Filter Tiny Files",
                        subtitle = "Hide audio files smaller than 100 KB",
                        checked = filterPrefs?.minSizeEnabled ?: true,
                        onCheckedChange = { viewModel.updateMinSizeEnabled(it) }
                    )
                    SettingsItem(
                        icon = Icons.Default.Refresh,
                        title = "Force Library Rescan",
                        subtitle = "Re-index all music files from scratch",
                        onClick = { showRescanDialog = true }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    SettingsHeader("Support & Privacy")
                    
                    val deviceName = android.os.Build.MODEL
                    val androidVersion = android.os.Build.VERSION.RELEASE
                    val feedbackEmail = "feedback@localnewsindia.in"
                    val feedbackSubject = "OrbitMusic Feedback"
                    val feedbackBody = "Device: $deviceName\nAndroid Version: $androidVersion\n\nIssue / Suggestion:\n\nSteps to reproduce:"
                    
                    val encodedSubject = java.net.URLEncoder.encode(feedbackSubject, "UTF-8").replace("+", "%20")
                    val encodedBody = java.net.URLEncoder.encode(feedbackBody, "UTF-8").replace("+", "%20")
                    val mailtoUri = "mailto:$feedbackEmail?subject=$encodedSubject&body=$encodedBody"

                    SettingsItem(
                        icon = Icons.Default.Email,
                        title = "Send Feedback",
                        subtitle = "Help us improve OrbitMusic",
                        onClick = { 
                            uriHandler.openUri(mailtoUri)
                        }
                    )
                    
                    SettingsItem(
                        icon = Icons.Default.VerifiedUser,
                        title = "Privacy Policy",
                        subtitle = "How we protect your library data",
                        onClick = { 
                            uriHandler.openUri("https://www.localnewsindia.in/OrbitMusic-privacy")
                        }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(48.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "OrbitMusic v1.0 RC1",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Gray.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Made with ❤️ for Music Lovers",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = MaterialTheme.shapes.medium,
            color = Color.White.copy(alpha = 0.05f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                lineHeight = 16.sp
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF42C6B9),
                checkedTrackColor = Color(0xFF42C6B9).copy(alpha = 0.5f),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.Gray.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
fun SettingsHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Color(0xFF42C6B9),
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = MaterialTheme.shapes.medium,
            color = Color.White.copy(alpha = 0.05f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                lineHeight = 16.sp
            )
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.DarkGray
        )
    }
}
