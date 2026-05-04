package com.pralayakaveri.orbitmusic.presentation.galaxy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.core.*
import com.pralayakaveri.orbitmusic.presentation.galaxy.NodeType
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import com.pralayakaveri.orbitmusic.domain.model.Song
import com.pralayakaveri.orbitmusic.presentation.components.StarBackground
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalaxyScreen(
    onNavigateBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    mainViewModel: com.pralayakaveri.orbitmusic.presentation.main.MainViewModel,
    viewModel: GalaxyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val initialFocusOffset by viewModel.initialFocusOffset.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context) {
        val check = {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        hasPermission = check()

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = check()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var isAnimationFinished by remember { mutableStateOf(false) }
    val hasTriggeredLoad = remember { mutableStateOf(false) }

    // Removed immediate loadGalaxy() to prevent jank during entry animation

    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    val imageLoader = remember { ImageLoader(context) }
    val bitmaps = remember { mutableStateMapOf<Long, ImageBitmap>() }
    val extractedColors = remember { mutableStateMapOf<Long, Color>() }
    
    val infiniteTransition = rememberInfiniteTransition(label = "GalaxyRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * kotlin.math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    val currentSong by mainViewModel.currentSong.collectAsState()
    val isPlaying by mainViewModel.isPlaying.collectAsState()
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    val nebulaBrush = remember {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF42C6B9).copy(alpha = 0.15f),
                Color(0xFF1A1A2E).copy(alpha = 0.05f),
                Color.Transparent
            ),
            center = Offset.Zero,
            radius = 2000f
        )
    }

    val firstFrameLogged = remember { mutableStateOf(false) }

    // Performance Caches: Pre-allocated objects to prevent allocations in draw loop
    val sharedPath = remember { Path() }
    val sharedRect = remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val baseTextStyle = remember {
        TextStyle(
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
        )
    }

    // Pre-allocated Glow Brush to avoid allocations per node
    val nodeGlowBrush = remember {
        Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.1f), Color.Transparent),
            center = Offset.Zero,
            radius = 1200f
        )
    }

    LaunchedEffect(uiState) {
        if (uiState is GalaxyUiState.Success) {
            val successState = uiState as GalaxyUiState.Success
            val songsToProcess = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                successState.nodes.flatMap { g -> 
                    g.children.flatMap { a -> 
                        a.children.mapNotNull { s -> s.song } 
                    } 
                }.distinctBy { it.id }
            }

        songsToProcess.forEach { song ->
            if (song.albumArtUri != null && !bitmaps.containsKey(song.id)) {
                launch {
                    val request = ImageRequest.Builder(context)
                        .data(song.albumArtUri)
                        .size(200, 200)
                        .allowHardware(false)
                        .build()
                    val result = imageLoader.execute(request)
                    result.drawable?.let { drawable ->
                        val bitmap = drawable.toBitmap()
                        bitmaps[song.id] = bitmap.asImageBitmap()
                        
                        androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                            val swatch = palette?.vibrantSwatch ?: palette?.dominantSwatch
                            swatch?.let {
                                extractedColors[song.id] = Color(it.rgb)
                            }
                        }
                    }
                }
            }
            }
        }
    }

    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    val glowAnimation = remember { Animatable(0f) }
    
    var rippleCenter by remember { mutableStateOf(Offset.Zero) }
    val rippleRadius = remember { Animatable(0f) }

    DisposableEffect(Unit) {
        mainViewModel.lockUiPlayback(true)
        onDispose {
            mainViewModel.lockUiPlayback(false)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Music Galaxy") },
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

        BoxWithConstraints(
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            val centerX = constraints.maxWidth / 2f
            val centerY = constraints.maxHeight / 2f

            val animX = remember(centerX) { Animatable(centerX - 400f) }
            val animY = remember(centerY) { Animatable(100f) }
            val animZoom = remember { Animatable(0.05f) }

            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                val targetZoom = 0.7f
                val targetX = centerX + initialFocusOffset.x
                val targetY = centerY + initialFocusOffset.y
                
                android.util.Log.d("GALAXY_PERF", "Animation START")
                
                coroutineScope {
                    launch {
                        // Curved motion: animX moves with different easing
                        animX.animateTo(
                            targetValue = targetX,
                            animationSpec = tween(4000, easing = LinearOutSlowInEasing)
                        )
                    }
                    launch {
                        animY.animateTo(
                            targetValue = targetY,
                            animationSpec = tween(3500, easing = FastOutSlowInEasing)
                        )
                    }
                    launch {
                        animZoom.animateTo(
                            targetValue = targetZoom,
                            animationSpec = tween(4000, easing = LinearOutSlowInEasing)
                        )
                    }
                }
                
                android.util.Log.d("GALAXY_PERF", "Animation END")
                isAnimationFinished = true
                
                // Trigger data loading only after entrance animation is complete
                if (hasPermission) {
                    viewModel.loadGalaxy()
                }
            }

            StarBackground(
                modifier = Modifier.matchParentSize()
            )

            // Canvas is now always visible to allow the cinematic entrance animation
            Box(modifier = Modifier.fillMaxSize()) {
                val nodes = if (uiState is GalaxyUiState.Success) {
                    (uiState as GalaxyUiState.Success).nodes
                } else {
                    // Provide a placeholder "Uncharted Signals" node for the entrance animation
                    listOf(
                        GalaxyNode(
                            id = "root",
                            label = "Uncharted Signals",
                            type = NodeType.GENRE,
                            position = Offset.Zero, // Will be centered by the transform
                            color = Color(0xFF42C6B9),
                            songCount = 0
                        )
                    )
                }

                if (uiState is GalaxyUiState.Loading && isAnimationFinished) {
                    // Small subtle indicator if data is still loading after animation
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp).size(24.dp),
                        color = Color(0xFF42C6B9).copy(alpha = 0.5f),
                        strokeWidth = 2.dp
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, gestureZoom, _ ->
                                coroutineScope.launch {
                                    val oldZoom = animZoom.value
                                    val newZoom = (oldZoom * gestureZoom).coerceIn(0.1f, 10f)
                                    val effectiveZoomChange = newZoom / oldZoom
                                    
                                    animZoom.snapTo(newZoom)
                                    animX.snapTo(animX.value * effectiveZoomChange + centroid.x * (1 - effectiveZoomChange) + pan.x)
                                    animY.snapTo(animY.value * effectiveZoomChange + centroid.y * (1 - effectiveZoomChange) + pan.y)
                                }
                            }
                        }
                            .pointerInput(Unit) {
                                detectTapGestures { tapOffset ->
                                    val canvasTap = (tapOffset - Offset(animX.value, animY.value)) / animZoom.value
                                    
                                    rippleCenter = canvasTap
                                    scope.launch {
                                        rippleRadius.snapTo(0f)
                                        rippleRadius.animateTo(2000f, animationSpec = tween(1000, easing = LinearOutSlowInEasing))
                                    }

                                    var hitNode: GalaxyNode? = null
                                    
                                    if (uiState is GalaxyUiState.Success) {
                                        val currentNodes = (uiState as GalaxyUiState.Success).nodes
                                        currentNodes.forEach { genreNode ->
                                        if (animZoom.value < 1.2f) {
                                            if (isHit(canvasTap, genreNode, 70f)) hitNode = genreNode
                                        } else {
                                            genreNode.children.forEach { artistNode ->
                                                val artistPos = genreNode.position + artistNode.position * 0.3f
                                                
                                                if (animZoom.value < 2.5f) {
                                                    if (isHit(canvasTap, artistNode.copy(position = artistPos), 50f)) hitNode = artistNode
                                                } else {
                                                    artistNode.children.forEach { songNode ->
                                                        val animatedPos = getAnimatedSongPosition(songNode, artistPos, rotationAngle)
                                                        if (isHit(canvasTap, songNode.copy(position = animatedPos), 30f)) hitNode = songNode
                                                    }
                                                    if (hitNode == null && isHit(canvasTap, artistNode.copy(position = artistPos), 50f)) hitNode = artistNode
                                                }
                                            }
                                        }
                                        }
                                    }

                                    hitNode?.let { node ->
                                        selectedNodeId = node.id
                                        scope.launch {
                                            glowAnimation.snapTo(1f)
                                            glowAnimation.animateTo(0f, animationSpec = tween(500))
                                            selectedNodeId = null
                                        }

                                        if (node.type == NodeType.SONG && node.song != null) {
                                            onPlaySong(node.song)
                                        }
                                    }
                                }
                            }
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = animZoom.value
                                    scaleY = animZoom.value
                                    translationX = animX.value
                                    translationY = animY.value
                                    transformOrigin = TransformOrigin(0f, 0f)
                                }
                        ) {
                            if (!firstFrameLogged.value) {
                                android.util.Log.d("GALAXY_DEBUG", "First frame render triggered")
                                firstFrameLogged.value = true
                            }
                            drawCircle(
                                brush = nebulaBrush,
                                radius = 2000f,
                                center = Offset.Zero
                            )

                            nodes.take(5).forEach { node ->
                                // Optimized: Use translated canvas to avoid creating new Brush objects
                                drawContext.canvas.save()
                                drawContext.canvas.translate(node.position.x, node.position.y)
                                drawCircle(
                                    brush = nodeGlowBrush,
                                    radius = 1200f,
                                    center = Offset.Zero
                                )
                                drawContext.canvas.restore()
                            }

                            if (rippleRadius.value > 0f) {
                                drawCircle(
                                    color = Color.White.copy(alpha = (1f - rippleRadius.value / 2000f).coerceIn(0f, 0.2f)),
                                    radius = rippleRadius.value,
                                    center = rippleCenter,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f / animZoom.value)
                                )
                            }

                            nodes.forEachIndexed { index, genreNode ->
                                val isMainNode = index == 0
                                val entranceAlpha = if (isMainNode) 1f else {
                                    // Reveal others as we zoom in
                                    ((animZoom.value - 0.1f) / 0.5f).coerceIn(0f, 1f)
                                }
                                
                                val showGenreLabel = animZoom.value < 1.2f
                                drawNode(
                                    node = genreNode,
                                    textMeasurer = textMeasurer,
                                    isHighlighted = selectedNodeId == genreNode.id,
                                    glowAmount = glowAnimation.value,
                                    showLabel = showGenreLabel,
                                    zoom = animZoom.value,
                                    artwork = null,
                                    isCurrent = false,
                                    alpha = entranceAlpha,
                                    sharedPath = sharedPath,
                                    baseTextStyle = baseTextStyle
                                )

                                if (animZoom.value > 0.8f) {
                                    genreNode.children.forEach { artistNode ->
                                        val artistPos = genreNode.position + artistNode.position * 0.3f
                                        
                                        if (animZoom.value > 2.0f) {
                                            val distinctRadii = artistNode.children.map { it.orbitRadius }.distinct()
                                            distinctRadii.forEach { radius ->
                                                drawCircle(
                                                    color = Color.White.copy(alpha = 0.05f),
                                                    radius = radius,
                                                    center = artistPos,
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f / animZoom.value)
                                                )
                                            }
                                        }

                                        drawNode(
                                            node = artistNode.copy(position = artistPos),
                                            textMeasurer = textMeasurer,
                                            isHighlighted = selectedNodeId == artistNode.id,
                                            glowAmount = glowAnimation.value,
                                            showLabel = animZoom.value in 1.2f..4.0f,
                                            zoom = animZoom.value,
                                            artwork = null,
                                            isCurrent = false,
                                            alpha = entranceAlpha,
                                            sharedPath = sharedPath,
                                            baseTextStyle = baseTextStyle
                                        )

                                        if (animZoom.value > 2.5f) {
                                            artistNode.children.forEach { songNode ->
                                                val animatedPos = getAnimatedSongPosition(songNode, artistPos, rotationAngle)
                                                val isCurrentlyPlaying = currentSong?.id == songNode.song?.id
                                                val songColor = extractedColors[songNode.song?.id] ?: songNode.color
                                                
                                                val t = (songNode.orbitRadius / 1500f).coerceIn(0f, 1f)
                                                val distanceScale = 0.75f + 0.25f * kotlin.math.sqrt(1f - t)
                                                val songAlpha = entranceAlpha * (if (isAnimationFinished) 1f else 0.4f)

                                                drawNode(
                                                    node = songNode.copy(position = animatedPos, color = songColor),
                                                    textMeasurer = textMeasurer,
                                                    isHighlighted = selectedNodeId == songNode.id,
                                                    glowAmount = glowAnimation.value,
                                                    showLabel = animZoom.value > 3.5f,
                                                    zoom = animZoom.value,
                                                    artwork = bitmaps[songNode.song?.id],
                                                    isCurrent = isCurrentlyPlaying,
                                                    alpha = songAlpha,
                                                    pulseFactor = if (isCurrentlyPlaying && isPlaying) pulseScale else 1f,
                                                    scale = distanceScale,
                                                    sharedPath = sharedPath,
                                                    baseTextStyle = baseTextStyle
                                                )
                                            }
                                        }
                                }
                            }
                        }
                    }
                }

                // Show Empty state if loading is done and no music was found
                if (uiState is GalaxyUiState.Empty && isAnimationFinished) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Interstellar Void", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(8.dp))
                            Text("No music found in this sector", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

private fun isHit(tap: Offset, node: GalaxyNode, radius: Float): Boolean {
    val dx = tap.x - node.position.x
    val dy = tap.y - node.position.y
    return (dx * dx + dy * dy) <= (radius * radius)
}

private fun DrawScope.drawNode(
    node: GalaxyNode,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    isHighlighted: Boolean,
    glowAmount: Float,
    showLabel: Boolean,
    zoom: Float,
    artwork: ImageBitmap?,
    isCurrent: Boolean,
    alpha: Float = 1f,
    pulseFactor: Float = 1f,
    scale: Float = 1f,
    sharedPath: Path,
    baseTextStyle: TextStyle
) {
    val baseRadius = when (node.type) {
        NodeType.GENRE -> 72f
        NodeType.ARTIST -> 48f
        NodeType.SONG -> 25f
    }
    val radius = baseRadius * pulseFactor * scale
    val nodeColor = node.color.copy(alpha = (node.color.alpha * alpha * scale).coerceIn(0f, 1f))

    if (isHighlighted || isCurrent || glowAmount > 0f) {
        val currentGlow = if (isHighlighted) 1f else glowAmount
        val glowColor = if (isCurrent) node.color else node.color.copy(alpha = 0.6f)
        
        drawCircle(
            color = glowColor.copy(alpha = (0.3f * currentGlow).coerceIn(0f, 0.6f)),
            radius = radius * (1.5f + 0.8f * currentGlow),
            center = node.position
        )
        
        if (isCurrent) {
            drawCircle(
                color = node.color.copy(alpha = 0.15f),
                radius = radius * 2.5f,
                center = node.position
            )
        }
    }

    if (node.type == NodeType.SONG && artwork != null) {
        drawContext.canvas.save()
        // Optimized: Reuse path to avoid allocation
        sharedPath.reset()
        sharedPath.addOval(androidx.compose.ui.geometry.Rect(
            left = node.position.x - radius,
            top = node.position.y - radius,
            right = node.position.x + radius,
            bottom = node.position.y + radius
        ))
        drawContext.canvas.clipPath(sharedPath)
        drawImage(
            image = artwork,
            dstOffset = androidx.compose.ui.unit.IntOffset(
                (node.position.x - radius).toInt(),
                (node.position.y - radius).toInt()
            ),
            dstSize = androidx.compose.ui.unit.IntSize((radius * 2).toInt(), (radius * 2).toInt()),
            alpha = nodeColor.alpha
        )
        drawContext.canvas.restore()
        
        drawCircle(
            color = if (isCurrent) Color.White.copy(alpha = nodeColor.alpha) else nodeColor.copy(alpha = 0.5f * nodeColor.alpha),
            radius = radius,
            center = node.position,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f / zoom)
        )
    } else {
        drawCircle(
            color = nodeColor.copy(alpha = 0.8f * nodeColor.alpha),
            radius = radius,
            center = node.position
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.3f * nodeColor.alpha),
            radius = radius * 0.7f,
            center = node.position,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f / zoom)
        )
    }

    if (showLabel) {
        val labelText = if (node.label.length > 20) node.label.take(17) + "..." else node.label
        val targetSizeOnScreen = (radius / 2).coerceIn(10f, 16f)
        val fontSizeInCanvas = (targetSizeOnScreen / zoom).sp

        val labelAlpha = (nodeColor.alpha + 0.1f).coerceAtMost(1f)
        
        // Optimized: Reuse baseTextStyle with modified properties to avoid allocation
        val labelLayout = textMeasurer.measure(
            text = labelText,
            style = baseTextStyle.copy(
                color = if (isCurrent) Color.Cyan.copy(alpha = labelAlpha) else Color.White.copy(alpha = labelAlpha),
                fontSize = fontSizeInCanvas,
                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                shadow = Shadow(Color.Black.copy(alpha = labelAlpha), blurRadius = 4f / zoom)
            )
        )

        drawText(
            textLayoutResult = labelLayout,
            color = if (isCurrent) Color.Cyan.copy(alpha = labelAlpha) else Color.White.copy(alpha = labelAlpha),
            topLeft = Offset(
                x = node.position.x - (labelLayout.size.width / 2),
                y = node.position.y + radius + (8f / zoom)
            )
        )
    }
}

private fun getAnimatedSongPosition(
    songNode: GalaxyNode,
    artistPos: Offset,
    rotationAngle: Float
): Offset {
    val angle = songNode.initialAngle + (songNode.orbitDirection * rotationAngle * songNode.orbitSpeed)
    val relativeX = songNode.orbitRadius * kotlin.math.cos(angle)
    val relativeY = songNode.orbitRadius * kotlin.math.sin(angle)
    return artistPos + Offset(relativeX, relativeY)
}
