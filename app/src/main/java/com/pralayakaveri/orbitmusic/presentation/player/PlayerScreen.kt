package com.pralayakaveri.orbitmusic.presentation.player

import com.pralayakaveri.orbitmusic.presentation.main.MainViewModel
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import coil.compose.AsyncImage
import coil.request.SuccessResult
import com.pralayakaveri.orbitmusic.domain.model.Song
import com.pralayakaveri.orbitmusic.presentation.components.StarBackground
import android.net.Uri
import com.pralayakaveri.orbitmusic.domain.util.cleanSongTitle
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.draw.alpha
import java.io.InputStreamReader

@Composable
fun PlayerScreen(
    song: Song,
    mainViewModel: MainViewModel,
    isPlaying: Boolean,
    currentPosition: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCollapse: () -> Unit
) {
    val duration = song.duration
    val context = LocalContext.current
    val defaultSurface = MaterialTheme.colorScheme.surface
    val defaultOnSurface = MaterialTheme.colorScheme.onSurface
    val defaultPrimary = MaterialTheme.colorScheme.primary

    var dominantColor by remember { mutableStateOf(defaultSurface) }
    var onDominantColor by remember { mutableStateOf(defaultOnSurface) }
    var vibrantColor by remember { mutableStateOf(defaultPrimary) }

    val currentTheme by mainViewModel.currentTheme.collectAsState()
    
    val backgroundColor = when (currentTheme) {
        com.pralayakaveri.orbitmusic.presentation.theme.ThemeType.DYNAMIC -> dominantColor
        com.pralayakaveri.orbitmusic.presentation.theme.ThemeType.AMOLED_DARK -> Color.Black
        com.pralayakaveri.orbitmusic.presentation.theme.ThemeType.NEON -> Color(0xFF0F0F1A)
        com.pralayakaveri.orbitmusic.presentation.theme.ThemeType.MINIMAL -> Color(0xFFF9F9F9)
        else -> Color.Black
    }

    val primaryColor = when (currentTheme) {
        com.pralayakaveri.orbitmusic.presentation.theme.ThemeType.DYNAMIC -> vibrantColor
        com.pralayakaveri.orbitmusic.presentation.theme.ThemeType.AMOLED_DARK -> Color(0xFFBB86FC)
        com.pralayakaveri.orbitmusic.presentation.theme.ThemeType.NEON -> Color(0xFF00FFCC)
        com.pralayakaveri.orbitmusic.presentation.theme.ThemeType.MINIMAL -> Color(0xFF111111)
        else -> Color(0xFFBB86FC)
    }

    val onBackgroundColor = when (currentTheme) {
        com.pralayakaveri.orbitmusic.presentation.theme.ThemeType.DYNAMIC -> onDominantColor
        com.pralayakaveri.orbitmusic.presentation.theme.ThemeType.AMOLED_DARK -> Color.White
        com.pralayakaveri.orbitmusic.presentation.theme.ThemeType.NEON -> Color.White
        com.pralayakaveri.orbitmusic.presentation.theme.ThemeType.MINIMAL -> Color.Black
        else -> Color.White
    }
    
    val customArtworks by mainViewModel.customArtworks.collectAsState()
    val displayUri = customArtworks[song.id] ?: song.albumArtUri

    LaunchedEffect(displayUri) {
        dominantColor = defaultSurface
        onDominantColor = defaultOnSurface
        vibrantColor = defaultPrimary
        
        displayUri?.let { uri ->
            val request = ImageRequest.Builder(context)
                .data(uri)
                .allowHardware(false)
                .build()
            
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                bitmap?.let { b ->
                    androidx.palette.graphics.Palette.from(b).generate { palette ->
                        palette?.dominantSwatch?.let { swatch ->
                            dominantColor = Color(swatch.rgb)
                            onDominantColor = Color(swatch.bodyTextColor)
                        }
                        palette?.vibrantSwatch?.let { swatch ->
                            vibrantColor = Color(swatch.rgb)
                        } ?: palette?.lightVibrantSwatch?.let { swatch ->
                            vibrantColor = Color(swatch.rgb)
                        }
                    }
                }
            }
        }
    }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    
    val density = LocalDensity.current
    val screenWidthPx = with(density) { screenWidth.toPx() }
    val screenHeightPx = with(density) { screenHeight.toPx() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1C212D)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF2A2E3B), Color.Transparent),
                            center = Offset(screenWidthPx * 0.2f, screenHeightPx * 0.1f),
                            radius = screenWidthPx * 1.2f
                        )
                    )
            )
            
            StarBackground()
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown, 
                            contentDescription = "Collapse", 
                            tint = Color.White
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Now Playing",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold, 
                                letterSpacing = 2.sp
                            ),
                            color = Color.White.copy(alpha = 0.9f)
                        )


                    }
                    Spacer(modifier = Modifier.size(48.dp))
                }
                
                Spacer(modifier = Modifier.height(10.dp))

                var offsetX by remember { mutableStateOf(0f) }
                var offsetY by remember { mutableStateOf(0f) }
                
                var showTimerDialog by remember { mutableStateOf(false) }
                var showLyricsDialog by remember { mutableStateOf(false) }
                var showArtworkMenu by remember { mutableStateOf(false) }
                
                val artworkPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    uri?.let {
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                it,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                            mainViewModel.saveCustomArtwork(song.id, it.toString())
                        } catch (e: Exception) {
                            android.util.Log.e("PlayerScreen", "Failed to take persistable permission", e)
                        }
                    }
                }

                if (showArtworkMenu) {
                    AlertDialog(
                        onDismissRequest = { showArtworkMenu = false },
                        title = { Text("Custom Album Artwork") },
                        text = {
                            Column {
                                TextButton(onClick = { 
                                    artworkPickerLauncher.launch("image/*")
                                    showArtworkMenu = false 
                                }) {
                                    Text("Choose Image from Gallery", color = Color.White)
                                }
                                if (customArtworks[song.id] != null) {
                                    TextButton(onClick = { 
                                        mainViewModel.removeCustomArtwork(song.id)
                                        showArtworkMenu = false 
                                    }) {
                                        Text("Remove Custom Image", color = Color.Red.copy(alpha = 0.8f))
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showArtworkMenu = false }) { Text("Cancel", color = Color.White) }
                        },
                        containerColor = Color(0xFF2A2E3B),
                        titleContentColor = Color.White
                    )
                }

                if (showTimerDialog) {
                    AlertDialog(
                        onDismissRequest = { showTimerDialog = false },
                        title = { Text("Sleep Timer") },
                        text = {
                            Column {
                                listOf(15, 30, 45, 60).forEach { minutes ->
                                    TextButton(onClick = { 
                                        mainViewModel.setSleepTimer(minutes)
                                        showTimerDialog = false 
                                    }) {
                                        Text("$minutes minutes", color = Color.White)
                                    }
                                }
                                TextButton(onClick = { 
                                    mainViewModel.cancelSleepTimer()
                                    showTimerDialog = false 
                                }) {
                                    Text("Turn Off", color = vibrantColor)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showTimerDialog = false }) { Text("Cancel", color = Color.White) }
                        },
                        containerColor = Color(0xFF2A2E3B),
                        titleContentColor = Color.White
                    )
                }

                var showAddLyricsDialog by remember { mutableStateOf(false) }
                var lyricsToEdit by remember { mutableStateOf("") }
                var isEditingLyrics by remember { mutableStateOf(false) }

                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    uri?.let {
                        android.util.Log.d("PlayerScreen", "Lyrics file selected: $it")
                        try {
                            context.contentResolver.openInputStream(it)?.use { stream ->
                                val content = InputStreamReader(stream).readText()
                                android.util.Log.d("PlayerScreen", "File content read: ${content.length} chars")
                                mainViewModel.saveLyrics(song, content)
                                showAddLyricsDialog = false
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("PlayerScreen", "Failed to read lyrics file", e)
                        }
                    }
                }

                if (showLyricsDialog) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showLyricsDialog = false },
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f)) // Readability overlay
                                .pointerInput(Unit) { detectDragGestures { _, _ -> } }
                        ) {
                            // Backdrop Album Art (Subtle Blur)
                            AsyncImage(
                                model = song.albumArtUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().alpha(0.25f).blur(25.dp),
                                contentScale = ContentScale.Crop
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 40.dp, bottom = 20.dp)
                            ) {
                                // Header
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { showLyricsDialog = false }) {
                                        Icon(Icons.Default.Close, null, tint = Color.White)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = song.title.cleanSongTitle(),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = song.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                    
                                    val lyricsList by mainViewModel.lyrics.collectAsState()
                                    if (lyricsList.isNotEmpty()) {
                                        var expanded by remember { mutableStateOf(false) }
                                        Box {
                                            IconButton(onClick = { expanded = true }) {
                                                Icon(Icons.Default.MoreVert, "Options", tint = Color.White)
                                            }
                                            DropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false },
                                                containerColor = Color(0xFF2A2E3B)
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Edit Lyrics", color = Color.White) },
                                                    onClick = {
                                                        expanded = false
                                                        lyricsToEdit = lyricsList.joinToString("\n") { it.text }
                                                        isEditingLyrics = true
                                                        showAddLyricsDialog = true
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Replace File", color = Color.White) },
                                                    onClick = {
                                                        expanded = false
                                                        filePickerLauncher.launch("text/*")
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Remove Lyrics", color = Color.Red) },
                                                    onClick = {
                                                        expanded = false
                                                        mainViewModel.deleteLyrics(song)
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(Modifier.size(48.dp))
                                    }
                                }

                                Spacer(Modifier.height(32.dp))

                                // Lyrics Content
                                val lyricsList by mainViewModel.lyrics.collectAsState()
                                val progress by mainViewModel.currentPosition.collectAsState()
                                val userOffset by mainViewModel.userOffsetMs.collectAsState()
                                val autoOffset by mainViewModel.autoOffsetMs.collectAsState()
                                val runningOffset by mainViewModel.runningOffsetMs.collectAsState()
                                
                                // Unified adjusted time: Playback + Auto Offset + Running Drift Offset + User Offset + 100ms Lead Bias
                                val adjustedProgress = progress + autoOffset + runningOffset + userOffset + 100
                                
                                val currentIndex = remember(lyricsList, adjustedProgress) {
                                    if (lyricsList.isEmpty()) -1
                                    else {
                                        // Binary search: find the LAST line that has startTimeMs <= adjustedProgress
                                        var low = 0
                                        var high = lyricsList.size - 1
                                        var result = -1
                                        while (low <= high) {
                                            val mid = low + (high - low) / 2
                                            if (lyricsList[mid].startTimeMs <= adjustedProgress) {
                                                result = mid
                                                low = mid + 1
                                            } else {
                                                high = mid - 1
                                            }
                                        }
                                        result
                                    }
                                }

                                val isVocalPresent by mainViewModel.isVocalPresent.collectAsState()

                                val isInstrumental = remember(lyricsList, adjustedProgress, currentIndex, isVocalPresent) {
                                    if (lyricsList.isEmpty()) false
                                    else {
                                        val firstLyric = lyricsList[0]
                                        // Intro Guard: Strictly hide lyrics until the first vocal begins
                                        if (adjustedProgress < firstLyric.startTimeMs) {
                                            true
                                        } else if (currentIndex < 0) {
                                            true
                                        } else {
                                            val currentLine = lyricsList[currentIndex]
                                            val nextLine = if (currentIndex < lyricsList.size - 1) lyricsList[currentIndex + 1] else null
                                            
                                            // Real-time frequency check: If vocals are physically present, we are NOT instrumental
                                            if (isVocalPresent) return@remember false

                                            if (nextLine != null) {
                                                val gapSize = nextLine.startTimeMs - currentLine.startTimeMs
                                                if (gapSize > 3000) { // Gap larger than 3 seconds
                                                    val timeInCurrent = adjustedProgress - currentLine.startTimeMs
                                                    val timeToNext = nextLine.startTimeMs - adjustedProgress
                                                    // Enter instrumental state if we are past the current line and far from the next
                                                    timeInCurrent > 2000 && timeToNext > 1000
                                                } else false
                                            } else false
                                        }
                                    }
                                }
                                
                                if (lyricsList.isEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            "No lyrics found.", 
                                            color = Color.White.copy(alpha = 0.7f),
                                            style = MaterialTheme.typography.headlineSmall
                                        )
                                        Spacer(Modifier.height(24.dp))
                                        Button(
                                            onClick = { 
                                                lyricsToEdit = ""
                                                isEditingLyrics = false
                                                showAddLyricsDialog = true 
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = vibrantColor),
                                            shape = RoundedCornerShape(24.dp)
                                        ) {
                                            Icon(Icons.Default.Add, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Add Lyrics")
                                        }
                                    }
                                } else {
                                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                                    LaunchedEffect(currentIndex) {
                                        if (currentIndex >= 0) {
                                            // Instant scroll to avoid animation-induced lag
                                            listState.scrollToItem(
                                                index = currentIndex,
                                                scrollOffset = -300
                                            )
                                        }
                                    }
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        androidx.compose.foundation.lazy.LazyColumn(
                                            state = listState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(vertical = 200.dp)
                                        ) {
                                            items(lyricsList) { lyricLine ->
                                                val lineIndex = lyricsList.indexOf(lyricLine)
                                                val isActive = lineIndex == currentIndex && !isInstrumental
                                                
                                                val fontSize by animateFloatAsState(
                                                    targetValue = if (isActive) 24f else 18f,
                                                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                                                )
                                                val alpha by animateFloatAsState(
                                                    targetValue = if (isActive) 1f else 0.35f
                                                )

                                                Text(
                                                    text = lyricLine.text,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .alpha(alpha)
                                                        .clickable { 
                                                            mainViewModel.tapToSync(lyricLine.startTimeMs)
                                                        }
                                                        .padding(vertical = 12.dp, horizontal = 32.dp),
                                                    color = if (isActive) vibrantColor else Color.White,
                                                    fontSize = fontSize.sp,
                                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                                    textAlign = TextAlign.Center,
                                                    lineHeight = 32.sp,
                                                    style = androidx.compose.ui.text.TextStyle(
                                                        shadow = if (isActive) androidx.compose.ui.graphics.Shadow(
                                                            color = vibrantColor.copy(alpha = 0.5f),
                                                            blurRadius = 20f
                                                        ) else null
                                                    )
                                                )
                                            }
                                        }

                                        // Instrumental Indicator Overlay
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = isInstrumental,
                                            enter = fadeIn() + expandVertically(),
                                            exit = fadeOut() + shrinkVertically(),
                                            modifier = Modifier.align(Alignment.Center)
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    Icons.Default.MusicNote, 
                                                    null, 
                                                    tint = vibrantColor.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(32.dp)
                                                )
                                                Text(
                                                    "♪ Instrumental",
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontStyle = FontStyle.Italic,
                                                    letterSpacing = 2.sp
                                                )
                                            }
                                        }

                                        // Tap-to-Sync Hint
                                        if (lyricsList.isNotEmpty() && !isInstrumental) {
                                            Text(
                                                "Tap a lyric to fix sync",
                                                color = Color.White.copy(alpha = 0.4f),
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(bottom = 8.dp)
                                            )
                                        }
                                    }
                                }

                                // Sync Offset Controls
                                if (lyricsList.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, bottom = 16.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = { mainViewModel.setUserOffset(userOffset - 100) }) {
                                            Icon(Icons.Outlined.RemoveCircleOutline, null, tint = Color.White.copy(alpha = 0.6f))
                                        }
                                        Text(
                                            text = "Sync: ${if (userOffset >= 0) "+" else ""}${userOffset}ms",
                                            color = Color.White.copy(alpha = 0.8f),
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        )
                                        IconButton(onClick = { mainViewModel.setUserOffset(userOffset + 100) }) {
                                            Icon(Icons.Outlined.AddCircleOutline, null, tint = Color.White.copy(alpha = 0.6f))
                                        }
                                        
                                        if (userOffset != 0L) {
                                            TextButton(onClick = { mainViewModel.setUserOffset(0) }) {
                                                Text("Reset", color = vibrantColor, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showAddLyricsDialog) {
                    AlertDialog(
                        onDismissRequest = { showAddLyricsDialog = false },
                        title = { Text(if (isEditingLyrics) "Edit Lyrics" else "Add Lyrics") },
                        text = {
                            Column {
                                Text(
                                    "Supported formats: .lrc (synced), .txt (plain)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = lyricsToEdit,
                                    onValueChange = { lyricsToEdit = it },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp),
                                    placeholder = { Text("Paste lyrics here...") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        cursorColor = vibrantColor,
                                        focusedBorderColor = vibrantColor,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )
                                Spacer(Modifier.height(16.dp))
                                    OutlinedButton(
                                        onClick = { filePickerLauncher.launch("text/*") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = vibrantColor),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, vibrantColor)
                                    ) {
                                        Icon(Icons.Default.FileUpload, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Import File (.lrc, .txt)")
                                    }
                            }
                        },
                        confirmButton = {
                                Button(
                                    onClick = {
                                        android.util.Log.d("PlayerScreen", "Saving lyrics from text field. Length: ${lyricsToEdit.length}")
                                        mainViewModel.saveLyrics(song, lyricsToEdit)
                                        showAddLyricsDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = vibrantColor),
                                    enabled = lyricsToEdit.isNotBlank()
                                ) {
                                    Text("Save")
                                }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAddLyricsDialog = false }) {
                                Text("Cancel", color = Color.White)
                            }
                        },
                        containerColor = Color(0xFF2A2E3B),
                        titleContentColor = Color.White
                    )
                }


                Spacer(modifier = Modifier.height(50.dp))

                // Premium Glowing Album Art Redesign
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1f)
                        .padding(bottom = 16.dp)
                        // Outer Glow Layer 1 (Broad & Subtle)
                        .shadow(
                            elevation = 32.dp,
                            shape = RoundedCornerShape(48.dp),
                            spotColor = vibrantColor.copy(alpha = 0.3f),
                            ambientColor = vibrantColor.copy(alpha = 0.2f)
                        )
                        // Outer Glow Layer 2 (Tight & Bright)
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(48.dp),
                            spotColor = vibrantColor.copy(alpha = 0.6f),
                            ambientColor = vibrantColor.copy(alpha = 0.4f)
                        )
                        // Frame Container
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF2A2E3B), Color(0xFF1C212D))
                            ),
                            shape = RoundedCornerShape(48.dp)
                        )
                        // Premium Gradient Border
                        .border(
                            width = 2.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    vibrantColor.copy(alpha = 0.9f),
                                    vibrantColor.copy(alpha = 0.1f)
                                )
                            ),
                            shape = RoundedCornerShape(48.dp)
                        )
                        .padding(12.dp) // Space for Ambient Bleed
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    if (offsetX > 150f) onPrevious() 
                                    else if (offsetX < -150f) onNext() 
                                    else if (offsetY > 250f) onCollapse() 
                                    offsetX = 0f; offsetY = 0f
                                }
                            ) { change, dragAmount -> 
                                change.consume()
                                offsetX += dragAmount.x; offsetY += dragAmount.y
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Inner Ambient Bleed Layer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        vibrantColor.copy(alpha = 0.15f),
                                        Color.Transparent
                                    )
                                ),
                                shape = RoundedCornerShape(38.dp)
                            )
                    )
                    
                    // The Actual Image with Edit Hint Map
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.96f) // Slight inset for depth
                            .clip(RoundedCornerShape(36.dp))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = { showArtworkMenu = true }
                                )
                            }
                    ) {
                        AsyncImage(
                            model = displayUri,
                            contentDescription = song.title.cleanSongTitle(),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Subtle Edit Hint
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                .clickable { showArtworkMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Artwork",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Song Info
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = song.title.cleanSongTitle(),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Progress Bar
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                    
                    Box(modifier = Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            val centerY = size.height / 2
                            val thumbX = size.width * progress
                            
                            // Inactive Track (Very thin and subtle)
                            drawLine(
                                color = Color.White.copy(alpha = 0.1f),
                                start = Offset(0f, centerY),
                                end = Offset(size.width, centerY),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                            
                            // Active Track (Gradient glow)
                            if (progress > 0f) {
                                drawLine(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.3f),
                                            vibrantColor.copy(alpha = 0.8f),
                                            vibrantColor
                                        )
                                    ),
                                    start = Offset(0f, centerY),
                                    end = Offset(thumbX, centerY),
                                    strokeWidth = 4f,
                                    cap = StrokeCap.Round
                                )
                            }
                            
                            // Glowing Thumb
                            // Outer extended soft glow
                            drawCircle(
                                color = vibrantColor.copy(alpha = 0.15f),
                                radius = 16.dp.toPx(),
                                center = Offset(thumbX, centerY)
                            )
                            // Inner intense glow
                            drawCircle(
                                color = vibrantColor.copy(alpha = 0.4f),
                                radius = 9.dp.toPx(),
                                center = Offset(thumbX, centerY)
                            )
                            // Core white dot
                            drawCircle(
                                color = Color.White,
                                radius = 4.5.dp.toPx(),
                                center = Offset(thumbX, centerY)
                            )
                        }

                        Slider(
                            value = progress,
                            onValueChange = { onSeek((it * duration).toLong()) },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Transparent,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = formatTime(currentPosition), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f))
                        Text(text = formatTime(duration), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .shadow(elevation = 24.dp, shape = CircleShape, spotColor = vibrantColor)
                            .background(Color(0xFF28243D), CircleShape)
                            .border(width = 2.dp, color = vibrantColor.copy(alpha = 0.6f), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onPlayPause, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = vibrantColor.copy(alpha = 0.9f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    
                    IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Panel Pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .navigationBarsPadding()
                        .height(72.dp)
                        .shadow(elevation = 12.dp, shape = RoundedCornerShape(percent = 50), spotColor = Color.Black)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(percent = 50))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(percent = 50)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val shuffleModeEnabled by mainViewModel.shuffleModeEnabled.collectAsState()
                        IconButton(onClick = { mainViewModel.toggleShuffle() }) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (shuffleModeEnabled) vibrantColor else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
                        }
                        
                        val repeatMode by mainViewModel.repeatMode.collectAsState()
                        IconButton(onClick = { mainViewModel.toggleRepeat() }) {
                            Icon(if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                contentDescription = "Repeat",
                                tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) vibrantColor else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
                        }
                        
                        val favoriteIds by mainViewModel.favoriteIds.collectAsState()
                        val isFavorite = favoriteIds.contains(song.id)
                        IconButton(onClick = { mainViewModel.toggleFavorite(song.id) }) {
                            Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favorite", tint = if (isFavorite) vibrantColor else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
                        }
                        
                        val lyricsList by mainViewModel.lyrics.collectAsState()
                        IconButton(onClick = { showLyricsDialog = true }) {
                            Icon(Icons.Default.Article, contentDescription = "Lyrics", tint = if (lyricsList.isNotEmpty()) vibrantColor else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
                        }
                        
                        val sleepTimerTimeRemaining by mainViewModel.sleepTimerTimeRemaining.collectAsState()
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (sleepTimerTimeRemaining != null) {
                                Text(
                                    text = formatTime(sleepTimerTimeRemaining!!),
                                    color = vibrantColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                            IconButton(
                                onClick = { showTimerDialog = true },
                                modifier = Modifier.size(if (sleepTimerTimeRemaining != null) 32.dp else 48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Timer, 
                                    contentDescription = "Timer", 
                                    tint = if (sleepTimerTimeRemaining != null) vibrantColor else Color.White.copy(alpha = 0.6f), 
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
