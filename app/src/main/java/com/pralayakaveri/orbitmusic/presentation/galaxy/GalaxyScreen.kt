package com.pralayakaveri.orbitmusic.presentation.galaxy

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.*
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.remote.creation.first
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.ImageLoader
import coil.request.ImageRequest
import com.pralayakaveri.orbitmusic.domain.model.Song
import com.pralayakaveri.orbitmusic.presentation.galaxy.GalaxyPalette
import com.pralayakaveri.orbitmusic.presentation.components.StarBackground
import com.pralayakaveri.orbitmusic.presentation.main.MainViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.math.*

/**
 * Galaxy 2.0 Screen
 * A high-performance, cinematic visualization for music libraries.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalaxyScreen(
    onNavigateBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    mainViewModel: MainViewModel,
    viewModel: GalaxyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val successState = uiState as? GalaxyUiState.Success
    val initialFocusOffset by viewModel.initialFocusOffset.collectAsState()

    var isReadyToDraw by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    val imageLoader = remember { ImageLoader(context) }

    val animX = remember { Animatable(0f) }
    val animY = remember { Animatable(0f) }
    val animZoom = remember { Animatable(0.05f) } // Original Phase 2 starting zoom
    val transitionAnimAlpha = remember { Animatable(0f) } // Start at 0 for Intro
    var isAnimationFinished by remember { mutableStateOf(false) }

    // 2. Global Animations (Orbits & Pulses)
    val infiniteTransition = rememberInfiniteTransition(label = "GalaxyDynamics")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing)),
        label = "OrbitRotation"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            tween(1000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "NodePulse"
    )

    // 3. Asset & Color Management
    val bitmaps = remember { mutableStateMapOf<Long, ImageBitmap>() }
    val extractedColors = remember { mutableStateMapOf<Long, Color>() }
    val textLayoutCache =
        remember { mutableMapOf<Pair<String, Int>, androidx.compose.ui.text.TextLayoutResult>() }

    // 4. Music Sync
    val currentSong by mainViewModel.currentSong.collectAsState()
    val isPlaying by mainViewModel.isPlaying.collectAsState()

    // 5. Navigation Control (Clean Architectural Separation)
    BackHandler {
        val state = uiState as? GalaxyUiState.Success
        if (state != null) {
            if (state.sceneState.currentScene == GalaxySceneType.UNIVERSE) {
                onNavigateBack()
            } else {
                viewModel.navigateBack()
            }
        }
    }

    // Permission Handling
    var hasPermission by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val check = {
            val permission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
        hasPermission = check()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasPermission = check()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Load Data
    LaunchedEffect(hasPermission) { if (hasPermission) viewModel.loadGalaxy() }

    // Load Artwork & Palette
    LaunchedEffect(uiState) {
        if (uiState is GalaxyUiState.Success) {
            val success = uiState as GalaxyUiState.Success

            // 1. Load Song Artwork
            val songs =
                success.nodes.flatMap { g -> g.children.flatMap { a -> a.children.mapNotNull { s -> s.song } } }
                    .distinctBy { it.id }
            songs.forEach { song ->
                if (song.albumArtUri != null && !bitmaps.containsKey(song.id)) {
                    launch {
                        val request = ImageRequest.Builder(context).data(song.albumArtUri).size(150)
                            .allowHardware(false).build()
                        imageLoader.execute(request).drawable?.let { drawable ->
                            val bitmap = drawable.toBitmap()
                            bitmaps[song.id] = bitmap.asImageBitmap()
                            androidx.palette.graphics.Palette.from(bitmap).generate { p ->
                                (p?.vibrantSwatch ?: p?.dominantSwatch)?.let {
                                    extractedColors[song.id] = Color(it.rgb)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Load Artist & Hub Portraits (String keys)
            val portraitNodes = mutableListOf<GalaxyNode>()
            success.nodes.forEach { root ->
                if (root.artistImageUri != null) portraitNodes.add(root)
                root.children.forEach { artist ->
                    if (artist.artistImageUri != null) portraitNodes.add(artist)
                }
            }

            portraitNodes.forEach { node ->
                val key = node.id.hashCode().toLong()
                // Force reload if it's the custom hub or if it's not in the cache
                val isCustomHub = node.id == "uncharted_root"
                if (node.artistImageUri != null && (!bitmaps.containsKey(key) || isCustomHub)) {
                    launch {
                        val request =
                            ImageRequest.Builder(context).data(node.artistImageUri).size(250)
                                .allowHardware(false).build()
                        imageLoader.execute(request).drawable?.let { drawable ->
                            bitmaps[key] = drawable.toBitmap().asImageBitmap()
                        }
                    }
                }
            }
        }
    }

    val animScale = remember { Animatable(1f) }
    val animTransOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    // Pulse Animation for Milky Way
    val pulseTransition = rememberInfiniteTransition()
    val milkyWayPulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Live Glowing Star Pulse (Solar System Heartbeat)
    val solarPulseTransition = rememberInfiniteTransition()
    val solarGlowScale by solarPulseTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val solarGlowAlpha by solarPulseTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Tap Feedback State
    val tapAnimScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // Step 4: Orbital Rotation Animation
    val orbitTransition = rememberInfiniteTransition()
    val orbitAngle by orbitTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(40000, easing = LinearEasing)
        )
    )

    // Cinematic Transition Logic
    val travelVelocity = remember { Animatable(0f) }
    val bloomAlpha = remember { Animatable(0f) }

    val currentTransition = (uiState as? GalaxyUiState.Success)?.sceneState?.transitionState
    LaunchedEffect(currentTransition?.isActive, currentTransition?.selectedGalaxyId) {
        if (currentTransition?.isActive == true) {
            Log.d(
                "TRANSITION",
                "LOG_ANIM -> Animation effect started for ${currentTransition.selectedGalaxyId}"
            )

            val successState = uiState as? GalaxyUiState.Success
            val selectedGalaxy = successState?.sceneState?.universeGalaxies
                ?.find { it.id == currentTransition.selectedGalaxyId }

            val targetPos = selectedGalaxy?.position ?: Offset.Zero

            coroutineScope {
                // 1. Hyperdrive Start
                launch { travelVelocity.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }

                // 2. Zoom & Pan
                launch {
                    transitionAnimAlpha.animateTo(0f, tween(1500, easing = LinearEasing))
                }
                launch {
                    animScale.animateTo(
                        15f,
                        tween(1500, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))
                    )
                }
                launch {
                    animTransOffset.animateTo(
                        Offset(-targetPos.x * 15f, -targetPos.y * 15f),
                        tween(1500, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))
                    )
                }

                // 3. Bloom Peak (Simulate entering the galaxy)
                launch {
                    delay(1000)
                    bloomAlpha.animateTo(1f, tween(300))
                    bloomAlpha.animateTo(0f, tween(500))
                }
            }

            // Reset state
            travelVelocity.snapTo(0f)
            animScale.snapTo(1f)
            transitionAnimAlpha.snapTo(1f)
            animTransOffset.snapTo(Offset.Zero)

            viewModel.completeTransition()
        }
    }


    // Phase 2: Full Celestial Bitmap Loading
    val celestialBitmaps by produceState<Map<String, ImageBitmap>>(initialValue = emptyMap()) {
        val names = listOf(
            "sun_texture",
            "mercury_texture",
            "venus_texture",
            "earth_texture",
            "mars_texture",
            "jupiter_texture",
            "saturn_texture",
            "uranus_texture",
            "neptune_texture"
        )
        val loaded = mutableMapOf<String, ImageBitmap>()
        names.forEach { name ->
            try {
                context.assets.open("celestial/$name.png")
                    .use { loaded[name] = BitmapFactory.decodeStream(it).asImageBitmap() }
            } catch (e: Exception) {
                Log.e("ASSETS", "Failed to load $name")
            }
        }
        value = loaded
    }

    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.setCustomHubImage(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.animation.AnimatedContent(
                        targetState = (uiState as? GalaxyUiState.Success)?.sceneState?.currentScene,
                        transitionSpec = {
                            (androidx.compose.animation.fadeIn(tween(500)) + androidx.compose.animation.scaleIn(
                                initialScale = 0.95f
                            ))
                                .togetherWith(androidx.compose.animation.fadeOut(tween(500)))
                        },
                        label = "TitleTransition"
                    ) { scene ->
                        val title = when (scene) {
                            GalaxySceneType.UNIVERSE -> "The Universe"
                            GalaxySceneType.GALAXY -> "Milky Way Galaxy"
                            GalaxySceneType.SOLAR_SYSTEM -> "Solar System"
                            GalaxySceneType.EARTH -> "Personal Home"
                            GalaxySceneType.SONGS -> {
                                val nodes =
                                    (uiState as? GalaxyUiState.Success)?.nodes ?: emptyList()
                                if (nodes.size == 1 && nodes[0].type == NodeType.ARTIST) nodes[0].label else "Artist Universe"
                            }

                            else -> "Music Galaxy"
                        }
                        Text(title, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        val state = uiState as? GalaxyUiState.Success
                        if (state?.sceneState?.currentScene == GalaxySceneType.UNIVERSE) {
                            onNavigateBack()
                        } else {
                            viewModel.navigateBack()
                        }
                    }) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                },
                actions = {
                    val state = uiState as? GalaxyUiState.Success
                    if (state?.sceneState?.currentScene == GalaxySceneType.EARTH) {
                        IconButton(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier.background(
                                Color.White.copy(alpha = 0.15f),
                                androidx.compose.foundation.shape.CircleShape
                            )
                        ) {
                            Icon(Icons.Default.Settings, "Edit Hub", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        // RENDER STAR BACKGROUND HERE (Outside Constraints for absolute stability)
        // Handle System Back Button
        androidx.activity.compose.BackHandler {
            val state = uiState as? GalaxyUiState.Success
            if (state?.sceneState?.currentScene == GalaxySceneType.UNIVERSE) {
                onNavigateBack()
            } else {
                viewModel.navigateBack()
            }
        }

        StarBackground(
            modifier = Modifier.fillMaxSize(),
            velocity = travelVelocity.value
        )

        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize()) {
            val screenW = constraints.maxWidth.toFloat()
            val screenH = constraints.maxHeight.toFloat()
            val canvasCenterX = screenW / 2f
            val canvasCenterY = screenH / 2f

            // 1. Decoupled Intro Animation (Runs ONCE on composition entry)
            LaunchedEffect(Unit) {
                // Wait for initial data to be ready
                snapshotFlow { uiState }.first { it is GalaxyUiState.Success }

                if (!isAnimationFinished) {
                    // 1. Snap to top (-1000f as requested)
                    animX.snapTo(0f)
                    animY.snapTo(-1000f)
                    animZoom.snapTo(0.1f)
                    transitionAnimAlpha.snapTo(0f)

                    // 2. GATE OPEN: Safe to show
                    isReadyToDraw = true

                    coroutineScope {
                        // 2. 5s Epic Descent to Center
                        launch {
                            animZoom.animateTo(
                                1.0f,
                                tween(5000, easing = FastOutSlowInEasing)
                            )
                        }
                        launch { animY.animateTo(0f, tween(5000, easing = FastOutSlowInEasing)) }
                        launch { transitionAnimAlpha.animateTo(1f, tween(5000)) }
                    }
                }
            }

            // 2. Earth Descent Offset Normalization
            // If we enter Earth Hub, smoothly pull camera back to center (0,0)
            if (uiState is GalaxyUiState.Success) {
                val state = uiState as GalaxyUiState.Success
                if (state.sceneState.currentScene == GalaxySceneType.EARTH &&
                    state.sceneState.earthSceneState.currentPhase == EarthRevealPhase.ENTRY
                ) {
                    LaunchedEffect(Unit) {
                        launch {
                            animX.animateTo(
                                0f,
                                tween(1500, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f))
                            )
                        }
                        launch {
                            animY.animateTo(
                                0f,
                                tween(1500, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f))
                            )
                        }
                    }
                }
            }

            // 3. Immersive Zoom-to-Enter Logic for Earth Hub
            LaunchedEffect(animZoom.value) {
                val state = uiState as? GalaxyUiState.Success ?: return@LaunchedEffect
                if (state.sceneState.currentScene == GalaxySceneType.EARTH &&
                    state.sceneState.earthSceneState.currentPhase == EarthRevealPhase.INTERACTIVE &&
                    animZoom.value > 4.5f && state.sceneState.transitionState.isActive != true
                ) {

                    // Find the satellite closest to the camera center
                    val satellites = state.sceneState.earthSceneState.satellites
                    var closestSat: SatelliteNode? = null
                    var minDist = Float.MAX_VALUE

                    val canvasCenterX = screenW / 2f
                    val canvasCenterY = screenH / 2f

                    // We check proximity in screen space
                    satellites.forEachIndexed { index, sat ->
                        val orbitRadius = (160f + index * 60f) * animZoom.value
                        val angle =
                            (orbitAngle * sat.orbitSpeed) + (index * (2 * PI / 5f)).toFloat()
                        val sX =
                            (canvasCenterX * animZoom.value + animX.value) + cos(angle.toDouble()).toFloat() * orbitRadius
                        val sY =
                            (canvasCenterY * animZoom.value + animY.value) + sin(angle.toDouble()).toFloat() * orbitRadius

                        val dist = Offset(sX - canvasCenterX, sY - canvasCenterY).getDistance()
                        if (dist < minDist) {
                            minDist = dist
                            closestSat = sat
                        }
                    }

                    // Strict check: Must be very close to the center of the satellite to enter its world
                    if (minDist < 60f * animZoom.value) {
                        closestSat?.let { viewModel.transitionTo(GalaxySceneType.SONGS, it.id) }
                    }
                }
            }

            // Transformation Layer (ALWAYS PRESENT to prevent flicker)
            if (isReadyToDraw) {
                Box(
                    modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            coroutineScope.launch {
                                if (!isAnimationFinished) {
                                    isAnimationFinished =
                                        true; animX.stop(); animY.stop(); animZoom.stop()
                                }

                                val oldZoom = animZoom.value
                                val newZoom = (oldZoom * zoom).coerceIn(0.1f, 10f)

                                if (zoom != 1f) {
                                    // ZOOM-CENTRIC PANNING: Adjust offsets to keep centroid stable
                                    val zoomFactor = newZoom / oldZoom
                                    val newX =
                                        (animX.value - (centroid.x - canvasCenterX)) * zoomFactor + (centroid.x - canvasCenterX) + pan.x
                                    val newY =
                                        (animY.value - (centroid.y - canvasCenterY)) * zoomFactor + (centroid.y - canvasCenterY) + pan.y

                                    animZoom.snapTo(newZoom)
                                    animX.snapTo(newX)
                                    animY.snapTo(newY)
                                } else {
                                    // PURE PANNING: Single finger movement
                                    animX.snapTo(animX.value + pan.x)
                                    animY.snapTo(animY.value + pan.y)
                                }
                            }
                        }
                    }
                    .pointerInput(uiState) {
                        detectTapGestures { tapOffset ->
                            if (uiState is GalaxyUiState.Success) {
                                val state = uiState as GalaxyUiState.Success
                                val zoom = animZoom.value
                                val offX = animX.value + (size.width / 2)
                                val offY = animY.value + (size.height / 2)
                                val canvasCenterX = size.width / 2
                                val canvasCenterY = size.height / 2

                                when (state.sceneState.currentScene) {
                                    GalaxySceneType.UNIVERSE -> {
                                        state.sceneState.universeGalaxies.forEach { galaxy ->
                                            val gX = galaxy.position.x * zoom + offX
                                            val gY = galaxy.position.y * zoom + offY
                                            val dist = (tapOffset - Offset(gX, gY)).getDistance()
                                            if (dist < galaxy.bounds.interactionRadius * zoom && galaxy.isMilkyWay) {
                                                // 1. Trigger Transition State Immediately
                                                viewModel.transitionTo(
                                                    GalaxySceneType.GALAXY,
                                                    galaxy.id
                                                )

                                                // 2. Play Feedback in Parallel
                                                scope.launch {
                                                    tapAnimScale.animateTo(1.15f, tween(100))
                                                    tapAnimScale.animateTo(1f, tween(100))
                                                }
                                            }
                                        }
                                    }

                                    GalaxySceneType.GALAXY -> {
                                        state.sceneState.galaxyArms.forEach { arm ->
                                            if (arm.isSolarSystem) {
                                                // UNIFIED TRANSFORM PARITY
                                                val visualPos = calculateNodeVisualOffset(
                                                    anchor = arm.anchorOffset,
                                                    rotation = rotationAngle * 0.001f,
                                                    zoom = zoom,
                                                    offX = offX,
                                                    offY = offY,
                                                    canvasCenterX = canvasCenterX.toFloat(),
                                                    canvasCenterY = canvasCenterY.toFloat()
                                                )

                                                val dist = (tapOffset - visualPos).getDistance()

                                                // ZOOM-AWARE HIT RADIUS
                                                val interactionRadius =
                                                    85f * zoom.coerceAtLeast(0.5f)

                                                if (dist < interactionRadius) {
                                                    viewModel.transitionTo(GalaxySceneType.SOLAR_SYSTEM)
                                                }
                                            }
                                        }
                                    }

                                    GalaxySceneType.SOLAR_SYSTEM -> {
                                        var foundPlanet = false
                                        state.sceneState.planets.forEach { planet ->
                                            val pAngle =
                                                (orbitAngle * planet.speed) + (planet.id.hashCode() % 10).toFloat()
                                            val pX =
                                                (canvasCenterX + cos(pAngle) * planet.orbitRadius) * zoom + offX
                                            val pY =
                                                (canvasCenterY + sin(pAngle) * planet.orbitRadius) * zoom + offY
                                            val dist = (tapOffset - Offset(pX, pY)).getDistance()

                                            val hitRadius =
                                                (planet.size + 20f) * zoom.coerceAtLeast(0.5f)

                                            if (dist < hitRadius) {
                                                if (planet.isEarth) {
                                                    viewModel.transitionTo(
                                                        GalaxySceneType.EARTH,
                                                        planet.id
                                                    )
                                                    foundPlanet = true
                                                }
                                            }
                                        }
                                    }

                                    GalaxySceneType.EARTH -> {
                                        // REVERSE PROJECTION: Tap -> World Space
                                        val canvasCenterX = size.width / 2f
                                        val canvasCenterY = size.height / 2f

                                        val worldTapX =
                                            (tapOffset.x - animX.value - canvasCenterX) / animZoom.value
                                        val worldTapY =
                                            (tapOffset.y - animY.value - canvasCenterY) / animZoom.value
                                        val worldTap = Offset(worldTapX, worldTapY)

                                        state.nodes.forEach { root ->
                                            root.children.forEach { artist ->
                                                artist.children.forEach { sNode ->
                                                    // Calculate world position of rotating song
                                                    val angle =
                                                        sNode.initialAngle + (rotationAngle * sNode.orbitSpeed * 8f)
                                                    val sPosWorld = artist.position + Offset(
                                                        cos(angle) * sNode.orbitRadius,
                                                        sin(angle) * sNode.orbitRadius
                                                    )

                                                    val dist = (worldTap - sPosWorld).getDistance()
                                                    if (dist < 60f) { // Constant hit-radius in world-space
                                                        sNode.song?.let { onPlaySong(it) }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    GalaxySceneType.SONGS -> {
                                        val nodes = state.nodes
                                        var foundNode: GalaxyNode? = null
                                        if (nodes.size == 1 && nodes[0].type == NodeType.ARTIST) {
                                            // FOCUS MODE: Single artist, orbiting songs
                                            val aNode = nodes[0]
                                            val cX = size.width / 2f
                                            val cY = size.height / 2f
                                            aNode.children.forEachIndexed { index, sNode ->
                                                val rad = (160f + index * 50f) * zoom
                                                val ang =
                                                    (rotationAngle * 1.5f) + (index * (2 * PI / aNode.children.size)).toFloat()
                                                val sX = cX + cos(ang.toDouble()).toFloat() * rad
                                                val sY = cY + sin(ang.toDouble()).toFloat() * rad
                                                if ((tapOffset - Offset(
                                                        sX,
                                                        sY
                                                    )).getDistance() < 35f * zoom.coerceAtLeast(
                                                        0.8f
                                                    )
                                                ) {
                                                    sNode.song?.let { onPlaySong(it) }
                                                }
                                            }
                                        } else {
                                            // CLUSTER MODE: Finding artists
                                            var found: GalaxyNode? = null
                                            nodes.forEach { g ->
                                                g.children.forEach { a ->
                                                    val aPos = g.position + a.position * 0.3f
                                                    val ax = aPos.x * zoom + offX
                                                    val ay = aPos.y * zoom + offY
                                                    if ((tapOffset - Offset(
                                                            ax,
                                                            ay
                                                        )).getDistance() < 60f * zoom.coerceAtLeast(
                                                            1f
                                                        )
                                                    ) found = a
                                                }
                                            }
                                            found?.let {
                                                if (it.type == NodeType.ARTIST) viewModel.transitionTo(
                                                    GalaxySceneType.SONGS,
                                                    it.id
                                                )
                                            }
                                        }
                                    }

                                    else -> {}
                                }
                            }
                        }
                    }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val zoom = animZoom.value
                        val offX = animX.value + canvasCenterX
                        val offY = animY.value + canvasCenterY
                        if (uiState is GalaxyUiState.Success) {
                            val state = uiState as GalaxyUiState.Success
                            val nodes = state.nodes
                            val sceneType = state.sceneState.currentScene
                            val trans = state.sceneState.transitionState

                            if (trans?.isActive == true) {
                                drawTransitionScene(
                                    state = state,
                                    scale = animScale.value,
                                    alpha = transitionAnimAlpha.value,
                                    offset = animTransOffset.value,
                                    w = screenW,
                                    h = screenH,
                                    celestialBitmaps = celestialBitmaps
                                )
                            } else {
                                when (sceneType) {
                                    GalaxySceneType.UNIVERSE -> {
                                        drawUniverseScene(
                                            state = state,
                                            pulse = milkyWayPulseScale,
                                            tap = tapAnimScale.value,
                                            zoom = zoom,
                                            offX = offX,
                                            offY = offY,
                                            w = screenW,
                                            h = screenH,
                                            transitionAlpha = transitionAnimAlpha.value,
                                            textMeasurer = textMeasurer,
                                            cache = textLayoutCache
                                        )
                                    }

                                    GalaxySceneType.GALAXY -> {
                                        drawGalaxyScene(
                                            state = state,
                                            pulse = milkyWayPulseScale,
                                            tap = tapAnimScale.value,
                                            zoom = zoom,
                                            offX = offX,
                                            offY = offY,
                                            w = screenW,
                                            h = screenH,
                                            rotation = rotationAngle * 0.001f,
                                            solarGlowScale = solarGlowScale,
                                            solarGlowAlpha = solarGlowAlpha,
                                            textMeasurer = textMeasurer
                                        )
                                    }

                                    GalaxySceneType.SOLAR_SYSTEM -> {
                                        drawSolarSystemScene(
                                            state = state,
                                            baseAngle = orbitAngle,
                                            zoom = zoom,
                                            offX = offX,
                                            offY = offY,
                                            w = screenW,
                                            h = screenH,
                                            solarGlowScale = solarGlowScale,
                                            solarGlowAlpha = solarGlowAlpha,
                                            celestialBitmaps = celestialBitmaps,
                                            textMeasurer = textMeasurer
                                        )
                                    }

                                    GalaxySceneType.EARTH -> {
                                        drawUnchartedSignals(
                                            state = state,
                                            zoom = animZoom.value,
                                            offX = animX.value + (screenW / 2f),
                                            offY = animY.value + (screenH / 2f),
                                            w = screenW,
                                            h = screenH,
                                            bitmaps = bitmaps,
                                            textMeasurer = textMeasurer,
                                            cache = textLayoutCache,
                                            pulse = pulseScale,
                                            colors = extractedColors,
                                            rotation = rotationAngle
                                        )
                                    }

                                    GalaxySceneType.SONGS -> {
                                        nodes.forEachIndexed { gIdx, gNode ->
                                            renderSongsScene(
                                                state = state,
                                                zoom = zoom,
                                                offX = offX,
                                                offY = offY,
                                                screenW = screenW,
                                                screenH = screenH,
                                                textMeasurer = textMeasurer,
                                                cache = textLayoutCache,
                                                rotationAngle = orbitAngle,
                                                currentSong = currentSong,
                                                isPlaying = isPlaying,
                                                pulseScale = pulseScale,
                                                extractedColors = extractedColors,
                                                bitmaps = bitmaps
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Cinematic Bloom Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = bloomAlpha.value * 0.4f))
            )

            // Atmospheric Entry Bloom (Earth Specific Flash)
            val currentUiState = uiState
            if (currentUiState is GalaxyUiState.Success && currentUiState.sceneState.currentScene == GalaxySceneType.EARTH) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawUnchartedSignals(
                        state = currentUiState,
                        zoom = animZoom.value,
                        offX = animX.value + (size.width / 2f),
                        offY = animY.value + (size.height / 2f),
                        w = size.width,
                        h = size.height,
                        bitmaps = bitmaps,
                        textMeasurer = textMeasurer,
                        cache = textLayoutCache,
                        pulse = pulseScale,
                        colors = extractedColors,
                        rotation = rotationAngle
                    )
                }
            }


            // --- GALACTIC NAVIGATOR MINI-MAP (EARTH SCENE ONLY) ---
            if (currentUiState is GalaxyUiState.Success &&
                currentUiState.sceneState.currentScene == GalaxySceneType.EARTH &&
                currentUiState.sceneState.earthSceneState.currentPhase == EarthRevealPhase.INTERACTIVE
            ) {

                val bounds = currentUiState.sceneState.topologyBounds
                    ?: androidx.compose.ui.geometry.Rect(-2000f, -2000f, 2000f, 2000f)
                val radarSize = 120.dp

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 80.dp, end = 20.dp)
                        .size(radarSize)
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color.Cyan.copy(alpha = 0.3f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) // CLIP EXTRUDED RECTS
                        .pointerInput(bounds) {
                            detectTapGestures { tapOffset ->
                                val normX = tapOffset.x / size.width.toFloat()
                                val normY = tapOffset.y / size.height.toFloat()

                                val targetWorldX = bounds.left + (normX * bounds.width)
                                val targetWorldY = bounds.top + (normY * bounds.height)

                                coroutineScope.launch {
                                    launch {
                                        animX.animateTo(
                                            -targetWorldX * animZoom.value,
                                            tween(1000, easing = FastOutSlowInEasing)
                                        )
                                    }
                                    launch {
                                        animY.animateTo(
                                            -targetWorldY * animZoom.value,
                                            tween(1000, easing = FastOutSlowInEasing)
                                        )
                                    }
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        val canvasW = size.width
                        val canvasH = size.height

                        // 1. Draw Artist Clusters
                        currentUiState.nodes.forEach { root ->
                            root.children.forEach { artist ->
                                val normX = (artist.position.x - bounds.left) / bounds.width
                                val normY = (artist.position.y - bounds.top) / bounds.height
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.4f),
                                    radius = 1.5f,
                                    center = Offset(normX * canvasW, normY * canvasH)
                                )
                            }
                        }

                        // 2. Origin Marker
                        val originX = (0f - bounds.left) / bounds.width
                        val originY = (0f - bounds.top) / bounds.height
                        drawCircle(
                            color = Color(0xFFE91E63).copy(alpha = 0.8f),
                            radius = 3f,
                            center = Offset(originX * canvasW, originY * canvasH)
                        )

                        // 3. RECALIBRATED VIEWPORT RECTANGLE
                        // The world-space view area is determined by the screen dimensions and the inverse camera transform.
                        val viewW = screenW / animZoom.value
                        val viewH = screenH / animZoom.value
                        val viewCenterX = -animX.value / animZoom.value
                        val viewCenterY = -animY.value / animZoom.value

                        val viewLeft = viewCenterX - (viewW / 2f)
                        val viewTop = viewCenterY - (viewH / 2f)

                        val rectLeft = ((viewLeft - bounds.left) / bounds.width) * canvasW
                        val rectTop = ((viewTop - bounds.top) / bounds.height) * canvasH
                        val rectW = (viewW / bounds.width) * canvasW
                        val rectH = (viewH / bounds.height) * canvasH

                        drawRect(
                            color = Color.Cyan.copy(alpha = 0.6f),
                            topLeft = Offset(rectLeft, rectTop),
                            size = androidx.compose.ui.geometry.Size(rectW, rectH),
                            style = Stroke(width = 1.5f)
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawProceduralGalaxy(
    center: Offset,
    size: Float,
    rotation: Float,
    alpha: Float,
    isPrimary: Boolean,
    pointCloud: List<GalaxyPoint>,
    palette: GalaxyPalette
) {
    val baseSize = if (isPrimary) 85f else 55f
    
    // STABILIZED TRANSFORM STACK (Baseline Architecture)
    withTransform({
        translate(center.x, center.y)
        rotate(Math.toDegrees(rotation.toDouble()).toFloat(), pivot = Offset.Zero)
        val scaleFactor = size / baseSize
        scale(scaleFactor, scaleFactor, pivot = Offset.Zero)
    }) {
        // 1. Solar Nucleus (The painting's distinct central Sun)
        drawCircle(
            color = Color(0xFFFFD700), // Solid Gold
            radius = baseSize * 0.04f, 
            center = Offset.Zero
        )
        // High Intensity Core Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFD700).copy(alpha = 0.8f), Color.Transparent),
                center = Offset.Zero,
                radius = baseSize * 0.15f
            ),
            radius = baseSize * 0.15f,
            center = Offset.Zero
        )
        
        // 2. Volumetric Particle Rendering (PHASE 2 UPGRADE)
        // Render 3 layers of precomputed clouds (Micro -> Medium -> Large)
        pointCloud.forEach { point ->
            val pAlpha = point.alpha * alpha
            if (pAlpha > 0.01f) {
                // Per-particle volumetric coloring from ViewModel
                drawCircle(
                    color = point.color.copy(alpha = pAlpha),
                    radius = point.size,
                    center = point.offset
                )
            }
        }
    }
}

private fun DrawScope.drawTransitionScene(
    state: GalaxyUiState.Success,
    scale: Float,
    alpha: Float,
    offset: Offset,
    w: Float,
    h: Float,
    celestialBitmaps: Map<String, ImageBitmap>
) {
    val canvasCenterX = w / 2
    val canvasCenterY = h / 2
    val sceneType = state.sceneState.currentScene
    val trans = state.sceneState.transitionState
    val selectedId = trans.selectedGalaxyId

    when (sceneType) {
        GalaxySceneType.UNIVERSE -> {
            // Universe -> Galaxy: Draw universe galaxies zooming
            state.sceneState.universeGalaxies.forEach { galaxy ->
                val isSelected = galaxy.id == selectedId
                val gX = galaxy.position.x * scale + offset.x + canvasCenterX
                val gY = galaxy.position.y * scale + offset.y + canvasCenterY
                val drawAlpha = if (isSelected) 1f else alpha
                if (drawAlpha > 0.05f) {
                    drawProceduralGalaxy(
                        center = Offset(gX, gY),
                        size = galaxy.bounds.maxExtent * scale,
                        rotation = 0f, 
                        alpha = drawAlpha,
                        isPrimary = galaxy.isMilkyWay,
                        pointCloud = galaxy.pointCloud,
                        palette = galaxy.palette
                    )
                }
            }
        }
        GalaxySceneType.GALAXY -> {
            // Galaxy -> Solar System: Draw internal Milky Way zooming
            val internalMW = state.sceneState.milkyWayInternal ?: return
            
            val center = Offset(offset.x + canvasCenterX, offset.y + canvasCenterY)

            drawProceduralGalaxy(
                center = center,
                size = 380f * scale, // Internal scale
                rotation = 0f,
                alpha = 1.0f,
                isPrimary = true,
                pointCloud = internalMW.pointCloud,
                palette = internalMW.palette
            )
        }
        GalaxySceneType.SOLAR_SYSTEM -> {
            // Solar System -> Earth: Draw Earth focal zoom
            // We ignore offsets here to ensure a perfectly centered 'descent'
            val earthPlanet = state.sceneState.planets.find { it.isEarth } ?: return
            val earthX = w / 2f
            val earthY = h / 2f
            val earthSize = earthPlanet.size * scale

            val earthBitmap = celestialBitmaps[earthPlanet.assetName]
            if (earthBitmap != null) {
                drawImage(
                    image = earthBitmap,
                    dstOffset = IntOffset((earthX - earthSize).toInt(), (earthY - earthSize).toInt()),
                    dstSize = IntSize((earthSize * 2).toInt(), (earthSize * 2).toInt())
                )
            } else {
                drawCircle(Color.Blue, earthSize, Offset(earthX, earthY))
            }
        }
        else -> {
            // Default fallback
        }
    }
}

private fun DrawScope.drawUniverseScene(
    state: GalaxyUiState.Success,
    pulse: Float,
    tap: Float,
    zoom: Float,
    offX: Float,
    offY: Float,
    w: Float,
    h: Float,
    transitionAlpha: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    cache: MutableMap<Pair<String, Int>, androidx.compose.ui.text.TextLayoutResult>
) {
    // Universe: Show simple galaxy nodes
    state.sceneState.universeGalaxies.forEach { galaxy ->
        val isMilkyWay = galaxy.isMilkyWay
        
        // 1. Calculate Hierarchy & Scale
        val focusScale = if (isMilkyWay) 1.5f * pulse * tap else 1.0f
        val focusAlpha = if (isMilkyWay) 1.0f else 0.5f
        
        val gX = galaxy.position.x * zoom + offX
        val gY = galaxy.position.y * zoom + offY
        
        // Bounds-Aware Culling
        val margin = galaxy.bounds.maxExtent * zoom
        if (gX > -margin && gX < w + margin && gY > -margin && gY < h + margin) {
            val visualRadius = galaxy.bounds.maxExtent * focusScale
            
            // Procedural Galaxy Rendering
            drawProceduralGalaxy(
                center = Offset(gX, gY),
                size = visualRadius,
                rotation = 0f,
                alpha = focusAlpha * transitionAlpha, // Corrected Universe Fade
                isPrimary = isMilkyWay,
                pointCloud = galaxy.pointCloud,
                palette = galaxy.palette
            )
            
            // 4. Label Polish (PHASE 2 UPGRADE)
            val labelAlpha = if (isMilkyWay) 1.0f else 0.75f 
            val layout = cache.getOrPut(galaxy.id to (labelAlpha * 100).toInt()) {
                textMeasurer.measure(
                    galaxy.name, 
                    TextStyle(
                        color = Color.White.copy(alpha = labelAlpha), 
                        fontSize = (if (isMilkyWay) 13.5f else 11.5f).sp,
                        fontWeight = if (isMilkyWay) FontWeight.SemiBold else FontWeight.Medium,
                        shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(1f, 1f), 2f)
                    )
                )
            }
            // DYNAMIC FIX: Centering label precisely below the precomputed max extent
            drawText(layout, topLeft = Offset(gX - layout.size.width / 2, gY + visualRadius + 18f))
        }
    }
}

private fun calculateNodeVisualOffset(
    anchor: Offset,
    rotation: Float,
    zoom: Float,
    offX: Float,
    offY: Float,
    canvasCenterX: Float,
    canvasCenterY: Float,
    eccentricity: Float = 0.75f,
    baseScale: Float = (380f / 85f)
): Offset {
    // 1. ROTATE around logical center (0,0)
    val cosR = cos(rotation.toDouble()).toFloat()
    val sinR = sin(rotation.toDouble()).toFloat()
    val rx = anchor.x * cosR - anchor.y * sinR
    // 2. APPLY ELLIPTICAL PROJECTION (Eccentricity parity with renderer)
    val ry = (anchor.x * sinR + anchor.y * cosR) * eccentricity
    
    // 3. SCALE & TRANSLATE to Global Screen Space
    val finalX = (canvasCenterX + rx * baseScale) * zoom + offX
    val finalY = (canvasCenterY + ry * baseScale) * zoom + offY
    
    return Offset(finalX, finalY)
}

private fun DrawScope.drawGalaxyScene(
    state: GalaxyUiState.Success,
    pulse: Float,
    tap: Float,
    zoom: Float,
    offX: Float,
    offY: Float,
    w: Float,
    h: Float,
    rotation: Float,
    solarGlowScale: Float,
    solarGlowAlpha: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val internalMW = state.sceneState.milkyWayInternal ?: return
    val canvasCenterX = w / 2f
    val canvasCenterY = h / 2f
    val galaxyCenter = Offset(canvasCenterX * zoom + offX, canvasCenterY * zoom + offY)

    // 1. MASSIVE PROCEDURAL GALAXY BODY (The "Inside" View)
    drawProceduralGalaxy(
        center = galaxyCenter,
        size = 380f * zoom,
        rotation = rotation,
        alpha = 1.0f, // SOLID INTERNAL ALPHA
        isPrimary = true,
        pointCloud = internalMW.pointCloud,
        palette = internalMW.palette
    )

    // 2. Discoverable Discovery Points (SS Focus & Arm Anchors)
    state.sceneState.galaxyArms.forEach { arm ->
        val isSS = arm.isSolarSystem
        
        // UNIFIED TRANSFORM PARITY
        val visualPos = calculateNodeVisualOffset(
            anchor = arm.anchorOffset,
            rotation = rotation,
            zoom = zoom,
            offX = offX,
            offY = offY,
            canvasCenterX = canvasCenterX,
            canvasCenterY = canvasCenterY
        )
        
        val gX = visualPos.x
        val gY = visualPos.y
        
        if (isSS) {
            // LIVE GLOWING DOT (Pulsing Heartbeat)
            // 1. Radiant Aura (Pulsing Bloom)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Cyan.copy(alpha = solarGlowAlpha * 0.6f), Color.Transparent),
                    center = Offset(gX, gY),
                    radius = 35f * zoom * solarGlowScale
                ),
                radius = 35f * zoom * solarGlowScale,
                center = Offset(gX, gY)
            )
            
            // 2. Atmospheric Pulsing Ring
            drawCircle(
                color = Color.Cyan.copy(alpha = solarGlowAlpha), 
                radius = 18f * zoom * (solarGlowScale * 0.8f), 
                center = Offset(gX, gY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f * zoom)
            )

            // 3. Solar Nucleus (White-Hot Sharp Center)
            drawCircle(
                color = Color.White, 
                radius = 5f * zoom, 
                center = Offset(gX, gY)
            )
            
            // Hub Logic: If it's a root anchor, make it huge and glowing
            val isHub = arm.id.startsWith("root_")
            val baseRadius = if (isHub) 45f else 18f
            val radius = baseRadius * zoom
            val x = gX
            val y = gY

            if (isHub) {
                // High-Intensity Hub Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.4f), Color.Transparent),
                        center = Offset(x, y),
                        radius = radius * 3f
                    ),
                    radius = radius * 3f,
                    center = Offset(x, y)
                )
            }
            
            // 4. Living Atmosphere Specks (Rotating)
            for (i in 0 until 8) {
                val a = (i * 0.8f + rotation * 3.5f)
                val r = 24f * zoom * solarGlowScale
                drawCircle(Color.Cyan.copy(alpha = 0.6f), 1.5f * zoom, Offset(gX + (cos(a) * r).toFloat(), gY + (sin(a) * r).toFloat()))
            }
        }

        // 3. Immersive Typography
        val labelAlpha = if (isSS) 0.9f else 0.5f
        val layout = textMeasurer.measure(
            if (isSS) "Solar System" else arm.name, 
            TextStyle(
                color = Color.White.copy(alpha = labelAlpha), 
                fontSize = (if (isSS) 13.5f else 10.5f).sp,
                fontWeight = if (isSS) FontWeight.SemiBold else FontWeight.Medium,
                shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(1f, 1f), 2f)
            )
        )
        drawText(layout, topLeft = Offset(gX - (layout.size.width / 2f), gY + (if (isSS) 32f else 22f) * zoom))
    }
}

private fun DrawScope.drawEarthScene(
    state: GalaxyUiState.Success,
    baseAngle: Float,
    zoom: Float,
    offX: Float,
    offY: Float,
    w: Float,
    h: Float,
    solarGlowScale: Float,
    solarGlowAlpha: Float,
    celestialBitmaps: Map<String, ImageBitmap>,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val earthState = state.sceneState.earthSceneState
    
    // Phase-based Alpha for reveal
    val revealAlpha = when(earthState.currentPhase) {
        EarthRevealPhase.ENTRY -> 0f
        EarthRevealPhase.STABILIZE -> 0.4f
        else -> 1f
    }

    // 1. Render Central Earth Hub (FULLY DYNAMIC COORDINATES)
    val centerX = (w / 2f) * zoom + offX
    val centerY = (h / 2f) * zoom + offY
    val earthSize = 130f * zoom // Focal size (35-45% screen width)

    // Cinematic Atmospheric Glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF4FC3F7).copy(alpha = 0.6f * revealAlpha), Color.Transparent),
            center = Offset(centerX, centerY),
            radius = earthSize * 1.25f
        ),
        radius = earthSize * 1.25f,
        center = Offset(centerX, centerY)
    )

    val earthBitmap = celestialBitmaps["earth_texture"]
    if (earthBitmap != null) {
        withTransform({
            translate(centerX, centerY)
            rotate(baseAngle * 4f) // Slow axial rotation
            
            val path = androidx.compose.ui.graphics.Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(
                    -earthSize, -earthSize, earthSize, earthSize
                ))
            }
            clipPath(path)
        }) {
            drawImage(
                image = earthBitmap,
                dstOffset = IntOffset(-(earthSize).toInt(), -(earthSize).toInt()),
                dstSize = IntSize((earthSize * 2).toInt(), (earthSize * 2).toInt()),
                alpha = revealAlpha,
                filterQuality = FilterQuality.Medium
            )
        }
    } else {
        drawCircle(Color.Blue.copy(alpha = revealAlpha), earthSize, Offset(centerX, centerY))
    }

    // 2. Render Orbiting Musical Moons (Satellites)
    if (earthState.currentPhase != EarthRevealPhase.ENTRY) {
        val satelliteAlpha = if (earthState.currentPhase == EarthRevealPhase.STABILIZE) 0f 
                            else if (earthState.currentPhase == EarthRevealPhase.REVEAL) 0.5f 
                            else 1f
        
        earthState.satellites.forEachIndexed { index, sat ->
            // Use structured circular orbits around center
            val orbitRadius = (160f + index * 60f) * zoom
            val angle = (baseAngle * sat.orbitSpeed) + (index * (2 * PI / 5f)).toFloat()
            val sX = centerX + cos(angle.toDouble()).toFloat() * orbitRadius
            val sY = centerY + sin(angle.toDouble()).toFloat() * orbitRadius
            
            val revealScale = if (earthState.currentPhase == EarthRevealPhase.REVEAL) 0.8f else 1f
            val satSize = 14f * zoom * revealScale

            // Placeholder Visual: Glowing Moon
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(sat.accentColor.copy(alpha = 0.4f * satelliteAlpha), Color.Transparent),
                    center = Offset(sX, sY),
                    radius = satSize * 2.5f
                ),
                radius = satSize * 2.5f,
                center = Offset(sX, sY)
            )
            drawCircle(
                color = Color.White.copy(alpha = satelliteAlpha),
                radius = 3f * zoom * revealScale,
                center = Offset(sX, sY)
            )

            // Elegant Typography
            val layout = textMeasurer.measure(
                sat.label.uppercase(),
                androidx.compose.ui.text.TextStyle(
                    color = Color.White.copy(alpha = 0.7f * satelliteAlpha),
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(1f, 1f), 3f)
                )
            )
            drawText(layout, topLeft = Offset(sX - layout.size.width / 2f, sY + satSize + 12f))
        }
    }
}

private fun DrawScope.drawSolarSystemScene(
    state: GalaxyUiState.Success,
    baseAngle: Float,
    zoom: Float,
    offX: Float,
    offY: Float,
    w: Float,
    h: Float,
    solarGlowScale: Float,
    solarGlowAlpha: Float,
    celestialBitmaps: Map<String, ImageBitmap>,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val canvasCenterX = w / 2
    val canvasCenterY = h / 2

    // 1. Draw Sun (The Solar Heart)
    val sunPlanet = state.sceneState.planets.find { it.id == "sun" }
    if (sunPlanet != null) {
        val sunPos = calculateNodeVisualOffset(
            anchor = Offset.Zero,
            rotation = 0f, 
            zoom = zoom,
            offX = offX,
            offY = offY,
            canvasCenterX = canvasCenterX,
            canvasCenterY = canvasCenterY
        )
        
        val sX = sunPos.x
        val sY = sunPos.y
        val sunRadius = 45f * zoom
        
        // SUN LAYER 1: Deep Galactic Bloom
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFCC33).copy(alpha = 0.4f * solarGlowAlpha), Color.Transparent),
                center = Offset(sX, sY),
                radius = sunRadius * 3.5f * solarGlowScale
            ),
            radius = sunRadius * 3.5f * solarGlowScale,
            center = Offset(sX, sY)
        )
        
        // SUN LAYER 2: Solar Flare Bloom
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFF9800).copy(alpha = 0.6f * solarGlowAlpha), Color.Transparent),
                center = Offset(sX, sY),
                radius = sunRadius * 1.8f * solarGlowScale
            ),
            radius = sunRadius * 1.8f * solarGlowScale,
            center = Offset(sX, sY)
        )
        
        // SUN LAYER 3: Circular Masked Texture
        val sunTexture = celestialBitmaps["sun_texture"]
        if (sunTexture != null) {
            val clipPath = androidx.compose.ui.graphics.Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(Offset(sX, sY), sunRadius))
            }
            clipPath(clipPath) {
                drawImage(
                    image = sunTexture,
                    dstOffset = IntOffset((sX - sunRadius).toInt(), (sY - sunRadius).toInt()),
                    dstSize = IntSize((sunRadius * 2).toInt(), (sunRadius * 2).toInt()),
                    blendMode = androidx.compose.ui.graphics.BlendMode.Plus
                )
            }
        }
        
        // SUN LAYER 4: Edge Feathering
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.3f), Color.Transparent),
                center = Offset(sX, sY),
                radius = sunRadius * 1.1f
            ),
            radius = sunRadius * 1.1f,
            center = Offset(sX, sY)
        )
        
        // Sun Label
        val sunLayout = textMeasurer.measure("Sun", TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold))
        drawText(sunLayout, topLeft = Offset(sX - sunLayout.size.width / 2, sY + sunRadius + 14f))
    }

    // 2. Draw Planets & Orbits
    state.sceneState.planets.forEach { planet ->
        if (planet.orbitRadius == 0f) return@forEach
        
        val pAngle = (baseAngle * planet.speed) + (planet.id.hashCode() % 10).toFloat()
        val pX = (canvasCenterX + cos(pAngle) * planet.orbitRadius) * zoom + offX
        val pY = (canvasCenterY + sin(pAngle) * planet.orbitRadius) * zoom + offY
        
        // Draw Orbit Ring (Minimalist Cinematic Style)
        drawCircle(
            color = Color.White.copy(alpha = 0.15f),
            radius = planet.orbitRadius * zoom,
            center = Offset(canvasCenterX * zoom + offX, canvasCenterY * zoom + offY),
            style = Stroke(width = 0.8f)
        )
        
        // PLANET RENDERING
        val pRadius = planet.size * zoom
        val planetTexture = planet.assetName?.let { celestialBitmaps[it] }
        
        if (planetTexture != null) {
            // LAYER 1: Atmospheric Aura
            val auraColor = when(planet.id) {
                "earth" -> Color(0xFF4FC3F7)
                "venus" -> Color(0xFFFFD54F)
                "mars" -> Color(0xFFFF8A65)
                "jupiter" -> Color(0xFFFFCCBC)
                "saturn" -> Color(0xFFFFF9C4)
                "uranus" -> Color(0xFFB2EBF2)
                "neptune" -> Color(0xFF81D4FA)
                else -> Color.White
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(auraColor.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(pX, pY),
                    radius = pRadius * 2.2f
                ),
                radius = pRadius * 2.2f,
                center = Offset(pX, pY)
            )
            
            // LAYER 2: SATURN SPECIAL - Procedural Rings (Behind Planet)
            if (planet.id == "saturn") {
                for (i in 0 until 3) {
                    val rRadius = (pRadius * (1.6f + i * 0.25f))
                    drawCircle(
                        color = Color(0xFFF5DEB3).copy(alpha = 0.3f - i * 0.05f),
                        radius = rRadius,
                        center = Offset(pX, pY),
                        style = Stroke(width = 2f * zoom)
                    )
                }
            }
            
            // LAYER 3: Circular Masked Body
            val planetClipPath = androidx.compose.ui.graphics.Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(
                    pX - pRadius, pY - pRadius, pX + pRadius, pY + pRadius
                ))
            }
            clipPath(planetClipPath) {
                drawImage(
                    image = planetTexture,
                    dstOffset = IntOffset((pX - pRadius).toInt(), (pY - pRadius).toInt()),
                    dstSize = IntSize((pRadius * 2).toInt(), (pRadius * 2).toInt())
                )
            }
            
            // REMOVED: LAYER 4 - Sphere Shadow (Preventing dark edge artifacts)
        } else {
            drawCircle(color = planet.color, radius = pRadius, center = Offset(pX, pY))
        }
        
        // PLANET LABELS (Shadowed & Consistent)
        val labelAlpha = if (planet.id == "earth" || planet.id == "saturn" || planet.id == "jupiter") 0.95f else 0.7f
        val layout = textMeasurer.measure(
            planet.name, 
            TextStyle(
                color = Color.White.copy(alpha = labelAlpha), 
                fontSize = (if (planet.id == "earth") 13.sp else 11.sp),
                fontWeight = if (planet.id == "earth") FontWeight.ExtraBold else FontWeight.Bold,
                shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(1f, 1f), 3f)
            )
        )
        drawText(layout, topLeft = Offset(pX - layout.size.width / 2, pY + pRadius + 12f))

        // Earth Navigation Pulse
        if (planet.isEarth) {
            drawCircle(
                color = Color.White.copy(alpha = 0.5f),
                radius = (pRadius + 8f),
                center = Offset(pX, pY),
                style = Stroke(width = 1.5f)
            )
        }
    }
}

private fun DrawScope.drawUnchartedSignals(
    state: GalaxyUiState.Success,
    zoom: Float,
    offX: Float,
    offY: Float,
    w: Float,
    h: Float,
    bitmaps: Map<Long, ImageBitmap>,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    cache: MutableMap<Pair<String, Int>, androidx.compose.ui.text.TextLayoutResult>,
    pulse: Float,
    colors: Map<Long, Color>,
    rotation: Float
) {
    val root = state.nodes.find { it.id == "uncharted_root" } ?: return
    
    // 1. Draw Large Orbital Rings (Signature "Image 2" look)
    val rootX = root.position.x * zoom + offX
    val rootY = root.position.y * zoom + offY
    
    for (i in 1..6) {
        drawCircle(
            color = Color.White.copy(alpha = 0.04f),
            radius = (250f + i * 400f) * zoom,
            center = Offset(rootX, rootY),
            style = Stroke(width = 1.2f)
        )
    }

    // 2. Draw Root Signal Hub
    val rootArt = bitmaps[root.id.hashCode().toLong()]
    drawGalaxyNode(root, zoom, offX, offY, 1f, pulse, rootArt, true, textMeasurer, cache)

    // 3. Draw Artist Constellations
    val artistThreshold = 0.35f
    if (zoom > artistThreshold) {
        val aAlpha = ((zoom - artistThreshold) / 0.5f).coerceIn(0f, 1f)
        root.children.forEach { artist ->
            val aX = artist.position.x * zoom + offX
            val aY = artist.position.y * zoom + offY
            
            // Tight Culling
            if (aX < -400 || aX > w + 400 || aY < -400 || aY > h + 400) return@forEach
            
            // Smart Text Culling: Only show labels for central nodes or when zoomed in
            val distToCenter = sqrt((aX - w/2).pow(2) + (aY - h/2).pow(2))
            val isCentral = distToCenter < (w * 0.45f)
            val showLabel = (isCentral && zoom > 0.6f) || zoom > 1.2f
            
            val artistArt = bitmaps[artist.id.hashCode().toLong()]
            drawGalaxyNode(artist, zoom, offX, offY, aAlpha, pulse, artistArt, showLabel, textMeasurer, cache)
            
            // 4. Draw Song Orbits (Planetary System)
            val songThreshold = 1.4f
            if (zoom > songThreshold) {
                val sAlpha = ((zoom - songThreshold) / 0.8f).coerceIn(0f, 1f) * aAlpha
                artist.children.forEachIndexed { i, sNode ->
                    val angle = sNode.initialAngle + (rotation * sNode.orbitSpeed * 8f)
                    val sPos = artist.position + Offset(cos(angle) * sNode.orbitRadius, sin(angle) * sNode.orbitRadius)
                    
                    val artwork = bitmaps[sNode.song?.id]
                    val sColor = colors[sNode.song?.id] ?: sNode.color
                    drawGalaxyNode(sNode.copy(position = sPos, color = sColor), zoom, offX, offY, sAlpha, pulse, artwork, true, textMeasurer, cache)
                }
            }
        }
    }
}

private fun DrawScope.drawGalaxyScene(
    node: GalaxyNode, index: Int, zoom: Float, offX: Float, offY: Float, w: Float, h: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer, cache: MutableMap<Pair<String, Int>, androidx.compose.ui.text.TextLayoutResult>
) {
    // Focus on Milky Way (Genre 0)
    if (index == 0 && isNodeVisible(node.position, 500f, zoom, offX, offY, w, h)) {
        drawGalaxyNode(node, zoom, offX, offY, 1f, 1f, null, false, textMeasurer, cache)
    }
}

private fun DrawScope.drawSolarSystemScene(
    node: GalaxyNode, index: Int, zoom: Float, offX: Float, offY: Float, w: Float, h: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer, cache: MutableMap<Pair<String, Int>, androidx.compose.ui.text.TextLayoutResult>
) {
    // Show Sun (Genre center) + planets (Artist clusters)
    if (index == 0) {
        drawGalaxyNode(node, zoom, offX, offY, 1f, 1f, null, false, textMeasurer, cache)
        node.children.forEach { artist ->
            val aPos = node.position + artist.position * 0.3f
            drawGalaxyNode(artist.copy(position = aPos), zoom, offX, offY, 1f, 1f, null, false, textMeasurer, cache)
        }
    }
}

private fun DrawScope.renderSongsScene(
    state: GalaxyUiState.Success,
    zoom: Float, offX: Float, offY: Float, screenW: Float, screenH: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer, cache: MutableMap<Pair<String, Int>, androidx.compose.ui.text.TextLayoutResult>,
    rotationAngle: Float, currentSong: Song?, isPlaying: Boolean, pulseScale: Float,
    extractedColors: Map<Long, Color>, bitmaps: Map<Long, ImageBitmap>
) {
    // If we have exactly ONE node and it's an ARTIST, we are in FOCUS MODE
    if (state.nodes.size == 1 && state.nodes[0].type == NodeType.ARTIST) {
        val aNode = state.nodes[0]
        val centerX = screenW / 2f
        val centerY = screenH / 2f
        
        // Draw Central Artist star
        drawGalaxyNode(aNode.copy(position = Offset.Zero), zoom, centerX, centerY, 1f, 1f, null, false, textMeasurer, cache)
        
        // Draw Orbiting Songs
        aNode.children.forEachIndexed { index, sNode ->
            val orbitRadius = (160f + index * 50f) * zoom
            val angle = (rotationAngle * 1.5f) + (index * (2 * PI / aNode.children.size)).toFloat()
            val sPos = Offset(cos(angle.toDouble()).toFloat() * orbitRadius, sin(angle.toDouble()).toFloat() * orbitRadius)
            
            val isCurr = currentSong?.id == sNode.song?.id
            val sPulse = if (isCurr && isPlaying) pulseScale else 1f
            val sColor = extractedColors[sNode.song?.id] ?: sNode.color
            
            drawGalaxyNode(sNode.copy(position = sPos, color = sColor), 1f, centerX, centerY, 1f, sPulse, bitmaps[sNode.song?.id], isCurr, textMeasurer, cache)
            
            // Draw Orbit Line
            drawCircle(
                color = Color.White.copy(alpha = 0.1f),
                radius = orbitRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 1f)
            )
        }
        return
    }

    // ORIGINAL CLUSTER RENDERING
    state.nodes.forEachIndexed { gIdx, gNode ->
        val gVisible = isNodeVisible(gNode.position, gNode.territorialLimit, zoom, offX, offY, screenW, screenH)
        if (gVisible) {
            val gAlpha = if (gIdx == 0) 1f else ((0.5f - zoom) / 0.2f).coerceIn(0f, 1f)
            if (gAlpha > 0.05f) {
                drawGalaxyNode(gNode, zoom, offX, offY, gAlpha, 1f, null, false, textMeasurer, cache)
            }

            val aThreshold = 1.0f
            if (zoom > aThreshold) {
                val aAlpha = ((zoom - aThreshold) / 0.5f).coerceIn(0f, 1f) * gAlpha
                gNode.children.forEach { aNode ->
                    val aPos = gNode.position + aNode.position * 0.3f
                    if (isNodeVisible(aPos, aNode.territorialLimit, zoom, offX, offY, screenW, screenH)) {
                        drawGalaxyNode(aNode.copy(position = aPos), zoom, offX, offY, aAlpha, 1f, null, false, textMeasurer, cache)

                        val sThreshold = 1.8f
                        if (zoom > sThreshold) {
                            val sAlpha = ((zoom - sThreshold) / 0.6f).coerceIn(0f, 1f) * aAlpha
                            aNode.children.forEach { sNode ->
                                val angle = sNode.initialAngle + (sNode.orbitDirection * rotationAngle * sNode.orbitSpeed)
                                val sPos = aPos + Offset(sNode.orbitRadius * cos(angle), sNode.orbitRadius * sin(angle))
                                if (isNodeVisible(sPos, 50f, zoom, offX, offY, screenW, screenH)) {
                                    val isCurr = currentSong?.id == sNode.song?.id
                                    val sPulse = if (isCurr && isPlaying) pulseScale else 1f
                                    val sColor = extractedColors[sNode.song?.id] ?: sNode.color
                                    drawGalaxyNode(sNode.copy(position = sPos, color = sColor), zoom, offX, offY, sAlpha, sPulse, bitmaps[sNode.song?.id], isCurr, textMeasurer, cache)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Optimized Node Drawing
 */
private fun DrawScope.drawGalaxyNode(
    node: GalaxyNode,
    zoom: Float,
    offX: Float,
    offY: Float,
    alpha: Float,
    pulse: Float,
    artwork: ImageBitmap?,
    isCurrent: Boolean,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    cache: MutableMap<Pair<String, Int>, androidx.compose.ui.text.TextLayoutResult>
) {
    val screenPos = Offset(node.position.x * zoom + offX, node.position.y * zoom + offY)
    
    // 1. Dynamic Scale with logarithmic dampening
    val baseRadius = when (node.type) {
        NodeType.GENRE -> 85f
        NodeType.ARTIST -> 45f
        NodeType.SONG -> 25f
    }
    val visualRadius = baseRadius * zoom.coerceAtLeast(0.65f) * pulse

    // 2. Subtle Glow Layer (Themed)
    val glowAlpha = (0.3f * alpha).coerceIn(0f, 1f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(node.color.copy(alpha = glowAlpha), Color.Transparent),
            center = screenPos,
            radius = visualRadius * 2.2f
        ),
        radius = visualRadius * 2.2f,
        center = screenPos
    )

    // 3. Content Nucleus (Artwork vs Geometric)
    val artThreshold = if (node.type == NodeType.SONG) 0.85f else 0.0f
    if (artwork != null && zoom > artThreshold) {
        val sAlpha = if (node.type == NodeType.SONG) ((zoom - artThreshold) / 0.5f).coerceIn(0f, 1f) * alpha else alpha
        val rect = Rect(screenPos.x - visualRadius, screenPos.y - visualRadius, screenPos.x + visualRadius, screenPos.y + visualRadius)
        val path = Path().apply { addOval(rect) }
        
        drawContext.canvas.save()
        drawContext.canvas.clipPath(path)
        drawImage(
            image = artwork,
            dstOffset = androidx.compose.ui.unit.IntOffset((screenPos.x - visualRadius).toInt(), (screenPos.y - visualRadius).toInt()),
            dstSize = androidx.compose.ui.unit.IntSize((visualRadius * 2).toInt(), (visualRadius * 2).toInt()),
            alpha = sAlpha
        )
        drawContext.canvas.restore()
        
        // Crisp Rim
        drawCircle(
            color = Color.White.copy(alpha = 0.3f * sAlpha),
            radius = visualRadius,
            center = screenPos,
            style = Stroke(width = 1.2f)
        )
    } else {
        // Geometric Core
        drawCircle(
            color = node.color.copy(alpha = alpha),
            radius = visualRadius * 0.85f,
            center = screenPos
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.4f * alpha),
            radius = visualRadius * 0.95f,
            center = screenPos,
            style = Stroke(width = 1f)
        )
    }

    // 4. Premium Labeling
    val labelThreshold = when(node.type) {
        NodeType.GENRE -> 0.0f
        NodeType.ARTIST -> 0.5f
        NodeType.SONG -> 1.3f
    }

    if (zoom > labelThreshold || isCurrent) {
        val labelAlpha = if (isCurrent) 1f else ((zoom - labelThreshold) / 0.4f).coerceIn(0f, 1f) * alpha
        if (labelAlpha > 0.05f) {
            val cacheKey = node.id to (zoom * 5).toInt()
            val layout = cache.getOrPut(cacheKey) {
                val fontSize = when(node.type) { NodeType.GENRE -> 15f; NodeType.ARTIST -> 12f; else -> 10.5f }
                val cleanLabel = if (node.label.contains("::")) {
                    node.label.split("::")[0].trim()
                } else if (node.label.length > 20) {
                    node.label.take(17) + "..."
                } else node.label
                
                textMeasurer.measure(
                    cleanLabel, 
                    TextStyle(
                        color = Color.White.copy(alpha = labelAlpha),
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(1f, 1f), 3f)
                    )
                )
            }
            drawText(layout, topLeft = Offset(screenPos.x - layout.size.width / 2, screenPos.y + visualRadius + 10f))
        }
    }
}

/**
 * High-speed visibility culling
 */
private fun isNodeVisible(pos: Offset, radius: Float, zoom: Float, offX: Float, offY: Float, w: Float, h: Float): Boolean {
    val sx = pos.x * zoom + offX
    val sy = pos.y * zoom + offY
    val pad = (radius * zoom) + 100f
    return sx + pad > 0f && sx - pad < w && sy + pad > 0f && sy - pad < h
}
