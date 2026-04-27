package com.pralayakaveri.beatflow.presentation.galaxy

import androidx.compose.animation.core.*
import com.pralayakaveri.beatflow.presentation.galaxy.NodeType
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import com.pralayakaveri.beatflow.domain.model.Song
import com.pralayakaveri.beatflow.presentation.components.StarBackground
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalaxyScreen(
    onNavigateBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    viewModel: GalaxyViewModel = hiltViewModel()
) {
    val nodes by viewModel.nodes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val initialFocusOffset by viewModel.initialFocusOffset.collectAsState()

    val animX = remember { Animatable(0f) }
    val animY = remember { Animatable(0f) }
    val animZoom = remember { Animatable(0.1f) }

    var offset by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableStateOf(1f) }

    // Sync with manual gestures vs animation
    LaunchedEffect(animX.value, animY.value, animZoom.value) {
        offset = Offset(animX.value, animY.value)
        zoom = animZoom.value
    }

    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(0.5f, 5f)
        offset += offsetChange
        
        // Interrupt animations on manual interaction
        // (Simplified: we just let manual override state)
    }

    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val imageLoader = remember { ImageLoader(context) }
    val bitmaps = remember { mutableStateMapOf<Long, ImageBitmap>() }
    val extractedColors = remember { mutableStateMapOf<Long, Color>() }
    
    // Rotation animation for orbits
    val infiniteTransition = rememberInfiniteTransition(label = "GalaxyRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * kotlin.math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    // Pulse animation for playing song
    val mainViewModel: com.pralayakaveri.beatflow.presentation.main.MainViewModel = hiltViewModel()
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

    // Fly-in effect on first focus
    LaunchedEffect(initialFocusOffset) {
        if (initialFocusOffset != Offset.Zero) {
            val targetX = initialFocusOffset.x + 500f
            val targetY = initialFocusOffset.y + 500f
            scope.launch {
                animX.animateTo(targetX, animationSpec = tween(1500, easing = FastOutSlowInEasing))
            }
            scope.launch {
                animY.animateTo(targetY, animationSpec = tween(1500, easing = FastOutSlowInEasing))
            }
            scope.launch {
                animZoom.animateTo(0.8f, animationSpec = tween(2000, easing = FastOutSlowInEasing))
            }
        }
    }

    // Palette extraction
    LaunchedEffect(nodes) {
        nodes.forEach { genreNode ->
            genreNode.children.forEach { artistNode ->
                artistNode.children.forEach { songNode ->
                    val song = songNode.song
                    if (song?.albumArtUri != null && !bitmaps.containsKey(song.id)) {
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
    }

    // Tap interaction state
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    val glowAnimation = remember { Animatable(0f) }
    
    // Ripple effect on tap
    var rippleCenter by remember { mutableStateOf(Offset.Zero) }
    val rippleRadius = remember { Animatable(0f) }

    Scaffold(
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            StarBackground()

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF42C6B9))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Materializing Galaxy...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(state = state)
                    .pointerInput(Unit) {
                        detectTapGestures { tapOffset ->
                            val canvasTap = (tapOffset - offset) / zoom
                            
                            rippleCenter = canvasTap
                            scope.launch {
                                rippleRadius.snapTo(0f)
                                rippleRadius.animateTo(2000f, animationSpec = tween(1000, easing = LinearOutSlowInEasing))
                            }

                            var hitNode: GalaxyNode? = null
                            nodes.forEach { genreNode ->
                                if (zoom < 1.2f) {
                                    if (isHit(canvasTap, genreNode, 70f)) hitNode = genreNode
                                } else {
                                    genreNode.children.forEach { artistNode ->
                                        val artistPos = genreNode.position + artistNode.position * 0.3f
                                        
                                        if (zoom < 2.5f) {
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
                            scaleX = zoom
                            scaleY = zoom
                            translationX = offset.x
                            translationY = offset.y
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                ) {
                    // Draw Central Nebula
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF42C6B9).copy(alpha = 0.15f),
                                Color(0xFF1A1A2E).copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = Offset.Zero,
                            radius = 2000f
                        ),
                        radius = 2000f,
                        center = Offset.Zero
                    )

                    // Draw secondary nebulae
                    nodes.take(5).forEach { node ->
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    node.color.copy(alpha = 0.1f),
                                    Color.Transparent
                                ),
                                center = node.position,
                                radius = 1200f
                            ),
                            radius = 1200f,
                            center = node.position
                        )
                    }

                    // Ripple Draw
                    if (rippleRadius.value > 0f) {
                        drawCircle(
                            color = Color.White.copy(alpha = (1f - rippleRadius.value / 2000f).coerceIn(0f, 0.2f)),
                            radius = rippleRadius.value,
                            center = rippleCenter,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f / zoom)
                        )
                    }

                    nodes.forEach { genreNode ->
                        val showGenreLabel = zoom < 1.2f
                        drawNode(
                            node = genreNode,
                            textMeasurer = textMeasurer,
                            isHighlighted = selectedNodeId == genreNode.id,
                            glowAmount = glowAnimation.value,
                            showLabel = showGenreLabel,
                            zoom = zoom,
                            artwork = null,
                            isCurrent = false
                        )

                        if (zoom > 0.8f) {
                            genreNode.children.forEach { artistNode ->
                                val artistPos = genreNode.position + artistNode.position * 0.3f
                                
                                if (zoom > 2.0f) {
                                    val distinctRadii = artistNode.children.map { it.orbitRadius }.distinct()
                                    distinctRadii.forEach { radius ->
                                        drawCircle(
                                            color = Color.White.copy(alpha = 0.05f),
                                            radius = radius,
                                            center = artistPos,
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f / zoom)
                                        )
                                    }
                                }

                                drawNode(
                                    node = artistNode.copy(position = artistPos),
                                    textMeasurer = textMeasurer,
                                    isHighlighted = selectedNodeId == artistNode.id,
                                    glowAmount = glowAnimation.value,
                                    showLabel = zoom in 1.2f..4.0f,
                                    zoom = zoom,
                                    artwork = null,
                                    isCurrent = false
                                )

                                if (zoom > 2.5f) {
                                    artistNode.children.forEach { songNode ->
                                        val animatedPos = getAnimatedSongPosition(songNode, artistPos, rotationAngle)
                                        val isCurrentlyPlaying = currentSong?.id == songNode.song?.id
                                        val songColor = extractedColors[songNode.song?.id] ?: songNode.color

                                        drawNode(
                                            node = songNode.copy(position = animatedPos, color = songColor),
                                            textMeasurer = textMeasurer,
                                            isHighlighted = selectedNodeId == songNode.id,
                                            glowAmount = if (isCurrentlyPlaying) pulseScale else glowAnimation.value,
                                            showLabel = zoom > 4.5f,
                                            zoom = zoom,
                                            artwork = bitmaps[songNode.song?.id],
                                            isCurrent = isCurrentlyPlaying,
                                            pulseFactor = if (isCurrentlyPlaying) pulseScale else 1f
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Zoom indicator
                Text(
                    text = "Zoom: ${"%.1f".format(zoom)}x",
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun isHit(tap: Offset, node: GalaxyNode, radius: Float): Boolean {
    val dx = tap.x - node.position.x
    val dy = tap.y - node.position.y
    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
    return distance <= radius * 1.5f
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
    pulseFactor: Float = 1f
) {
    val baseRadius = when (node.type) {
        NodeType.GENRE -> 60f
        NodeType.ARTIST -> 40f
        NodeType.SONG -> 25f
    }
    val radius = baseRadius * pulseFactor

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
        val path = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(node.position, radius))
        }
        drawContext.canvas.clipPath(path)
        drawImage(
            image = artwork,
            dstOffset = androidx.compose.ui.unit.IntOffset(
                (node.position.x - radius).toInt(),
                (node.position.y - radius).toInt()
            ),
            dstSize = androidx.compose.ui.unit.IntSize((radius * 2).toInt(), (radius * 2).toInt())
        )
        drawContext.canvas.restore()
        
        drawCircle(
            color = if (isCurrent) Color.White else node.color.copy(alpha = 0.5f),
            radius = radius,
            center = node.position,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f / zoom)
        )
    } else {
        drawCircle(
            color = node.color.copy(alpha = 0.8f),
            radius = radius,
            center = node.position
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.3f),
            radius = radius * 0.7f,
            center = node.position,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f / zoom)
        )
    }

    if (showLabel) {
        val labelText = if (node.label.length > 20) node.label.take(17) + "..." else node.label
        val targetSizeOnScreen = (radius / 2).coerceIn(10f, 16f)
        val fontSizeInCanvas = (targetSizeOnScreen / zoom).sp

        val labelStyle = TextStyle(
            color = if (isCurrent) Color.Cyan else Color.White,
            fontSize = fontSizeInCanvas,
            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
            shadow = Shadow(Color.Black, blurRadius = 4f / zoom)
        )

        val labelLayout = textMeasurer.measure(
            text = labelText,
            style = labelStyle
        )

        drawText(
            textLayoutResult = labelLayout,
            color = if (isCurrent) Color.Cyan else Color.White,
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
