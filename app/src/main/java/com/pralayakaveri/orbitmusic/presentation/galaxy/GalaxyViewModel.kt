package com.pralayakaveri.orbitmusic.presentation.galaxy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pralayakaveri.orbitmusic.domain.model.Song
import com.pralayakaveri.orbitmusic.domain.repository.MusicRepository
import com.pralayakaveri.orbitmusic.domain.util.cleanSongTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Galaxy 2.0 ViewModel
 * Handles the spatial topology and data management for the Music Galaxy.
 */

/**
 * Galaxy 2.0 Scene-Based Architecture
 */

enum class GalaxySceneType {
    UNIVERSE,
    GALAXY,
    SOLAR_SYSTEM,
    EARTH,
    SONGS
}

enum class EarthFeature {
    FAVORITES, RECENT, TOP_ARTISTS, PLAYLISTS, MOST_PLAYED
}

enum class EarthRevealPhase {
    ENTRY, STABILIZE, REVEAL, INTERACTIVE
}

data class SatelliteNode(
    val id: String,
    val label: String,
    val feature: EarthFeature,
    val orbitRadius: Float,
    val orbitSpeed: Float,
    val accentColor: Color,
    val isUnlocked: Boolean = false
)

data class EarthSceneState(
    val currentPhase: EarthRevealPhase = EarthRevealPhase.ENTRY,
    val satellites: List<SatelliteNode> = emptyList()
)

data class GalaxyPoint(
    val offset: Offset,
    val alpha: Float,
    val size: Float,
    val color: Color = Color.White // Support for per-particle volumetric blending
)

data class GalaxyPalette(
    val coreColor: Color,
    val armColor: Color,
    val highlightColor: Color = Color.White
)

data class GalaxyBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val visualCenter: Offset,
    val maxExtent: Float,
    val interactionRadius: Float
)

data class UniverseGalaxy(
    val id: String,
    val name: String,
    val position: Offset,
    val isMilkyWay: Boolean,
    val pointCloud: List<GalaxyPoint> = emptyList(),
    val bounds: GalaxyBounds = GalaxyBounds(0f, 0f, 0f, 0f, Offset.Zero, 0f, 0f),
    val palette: GalaxyPalette = GalaxyPalette(Color.White, Color.White),
    val assetName: String? = null // PHASE 1: Support for cinematic textures
)

data class TransitionState(
    val isActive: Boolean = false,
    val targetScene: GalaxySceneType? = null,
    val selectedGalaxyId: String? = null
)

data class Planet(
    val id: String,
    val name: String,
    val orbitRadius: Float,
    val size: Float,
    val color: Color,
    val speed: Float,
    val isEarth: Boolean = false,
    val assetName: String? = null // PHASE 1: Support for cinematic textures
)

data class GalaxyArm(
    val id: String,
    val name: String,
    val angle: Float,
    val radius: Float,
    val isSolarSystem: Boolean = false,
    val anchorOffset: Offset = Offset.Zero, // Stabilized label anchor
    val palette: GalaxyPalette = GalaxyPalette(Color.White, Color.White)
)

data class SceneState(
    val currentScene: GalaxySceneType = GalaxySceneType.UNIVERSE,
    val transitionState: TransitionState = TransitionState(),
    val universeGalaxies: List<UniverseGalaxy> = emptyList(),
    val milkyWayInternal: UniverseGalaxy? = null, // THE MASSIVE INTERNAL VIEW
    val galaxyArms: List<GalaxyArm> = emptyList(),
    val planets: List<Planet> = emptyList(),
    val topologyBounds: androidx.compose.ui.geometry.Rect? = null,
    val earthSceneState: EarthSceneState = EarthSceneState()
)

data class GalaxyNode(
    val id: String,
    val label: String,
    val type: NodeType,
    val position: Offset, // Anchor position
    val color: Color,
    val songCount: Int = 0,
    val children: List<GalaxyNode> = emptyList(),
    val song: Song? = null,
    val orbitRadius: Float = 0f,
    val orbitSpeed: Float = 0f,
    val initialAngle: Float = 0f,
    val orbitDirection: Int = 1,
    val territorialLimit: Float = 1000f,
    val artist: String? = null,
    val albumArtUri: android.net.Uri? = null,
    val artistImageUri: android.net.Uri? = null
)

enum class NodeType {
    GENRE, ARTIST, SONG
}

sealed interface GalaxyUiState {
    object Loading : GalaxyUiState
    object Empty : GalaxyUiState
    data class Success(
        val nodes: List<GalaxyNode>,
        val sceneState: SceneState = SceneState()
    ) : GalaxyUiState
}

@HiltViewModel
class GalaxyViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<GalaxyUiState>(GalaxyUiState.Loading)
    val uiState: StateFlow<GalaxyUiState> = _uiState.asStateFlow()

    private val _initialFocusOffset = MutableStateFlow(Offset.Zero)
    val initialFocusOffset: StateFlow<Offset> = _initialFocusOffset.asStateFlow()

    private var isLoaded = false

    /**
     * Scene Controller Logic
     */
    fun transitionTo(nextScene: GalaxySceneType, targetId: String? = null) {
        Log.d("TRANSITION", "LOG_START -> Triggering transition to $nextScene for $targetId")
        _uiState.update { current ->
            if (current is GalaxyUiState.Success) {
                // Phase 1: Check if this is an Artist Focus dive
                if (current.sceneState.currentScene == GalaxySceneType.SONGS && nextScene == GalaxySceneType.SONGS) {
                    focusArtist(targetId)
                    return@update current
                }

                // Phase 2: Start visual transition
                val updatedState = current.copy(
                    sceneState = current.sceneState.copy(
                        transitionState = TransitionState(
                            isActive = true,
                            targetScene = nextScene,
                            selectedGalaxyId = targetId
                        )
                    )
                )

                // Phase 3: Prepare target data
                if (nextScene == GalaxySceneType.SONGS) {
                    prepareSongsScene(targetId)
                } else if (nextScene == GalaxySceneType.EARTH) {
                    prepareUnchartedSignals()
                }
                
                updatedState
            } else current
        }
    }

    private fun focusArtist(artistName: String?) {
        _uiState.update { current ->
            if (current is GalaxyUiState.Success) {
                val artistNode = current.nodes.find { it.id == artistName }
                if (artistNode != null) {
                    current.copy(nodes = listOf(artistNode))
                } else current
            } else current
        }
    }

    fun completeTransition() {
        _uiState.update { current ->
            if (current is GalaxyUiState.Success) {
                val nextScene = current.sceneState.transitionState.targetScene ?: GalaxySceneType.GALAXY
                Log.d("TRANSITION", "LOG_SWITCH -> Switching to scene: $nextScene")
                
                val updated = current.copy(
                    sceneState = current.sceneState.copy(
                        currentScene = nextScene,
                        transitionState = TransitionState(),
                        earthSceneState = if (nextScene == GalaxySceneType.EARTH) {
                            EarthSceneState(currentPhase = EarthRevealPhase.ENTRY)
                        } else {
                            current.sceneState.earthSceneState
                        }
                    )
                )
                
                // Side effects outside of update if possible, but for simplicity:
                if (nextScene == GalaxySceneType.EARTH) {
                    viewModelScope.launch {
                        delay(100) // Small delay to ensure state propagates
                        startEarthRevealSequence()
                    }
                }
                updated
            } else current
        }
    }

    private fun startEarthRevealSequence() {
        viewModelScope.launch {
            // Phase 1: Descend through atmosphere (ENTRY is active for first 1.5s)
            delay(1500)
            updateEarthPhase(EarthRevealPhase.STABILIZE)
            
            // Phase 2: Fade in the orbiting nodes
            delay(1000)
            updateEarthPhase(EarthRevealPhase.REVEAL)
            
            // Phase 3: Enable interactivity
            delay(1000)
            updateEarthPhase(EarthRevealPhase.INTERACTIVE)
        }
    }

    /**
     * Set a custom image for the central Uncharted Signals hub
     */
    fun setCustomHubImage(uri: android.net.Uri) {
        viewModelScope.launch {
            musicRepository.saveArtistImage("uncharted_hub_custom", uri.toString())
            // Re-generate topology to reflect changes immediately
            prepareUnchartedSignals()
        }
    }

    private fun updateEarthPhase(phase: EarthRevealPhase) {
        val current = _uiState.value as? GalaxyUiState.Success ?: return
        _uiState.value = current.copy(
            sceneState = current.sceneState.copy(
                earthSceneState = current.sceneState.earthSceneState.copy(
                    currentPhase = phase
                )
            )
        )
    }

    private fun prepareUnchartedSignals() {
        viewModelScope.launch(Dispatchers.Default) {
            val allSongs = musicRepository.getAllSongs().first()
            if (allSongs.isEmpty()) {
                _uiState.update { GalaxyUiState.Empty }
                return@launch
            }

            // Cluster by Artist to create constellations
            val artistGroups = allSongs.groupBy { s: Song -> s.artist }
            
            // Fetch Custom Personalization Data
            val artistImages = musicRepository.getArtistImages().first()
            val customHubUri = artistImages["uncharted_hub_custom"]?.let { android.net.Uri.parse(it) }

            // Central Hub Node (Uncharted Signals)
            val rootNode = GalaxyNode(
                id = "uncharted_root",
                label = "Uncharted Signals",
                type = NodeType.GENRE,
                position = Offset.Zero,
                color = Color(0xFFE91E63),
                songCount = allSongs.size,
                artistImageUri = customHubUri
            )

            val artistNodes = mutableListOf<GalaxyNode>()
            var artistIndex = 0
            
            artistGroups.forEach { entry ->
                val artistName = entry.key
                val songs = entry.value
                
                // Position artists in a spacious, structured cluster (Spiraling Out)
                val angle = artistIndex * 0.75f 
                val radius = 450f + (artistIndex * 180f)
                val artistPos = Offset(cos(angle) * radius, sin(angle) * radius)
                
                val songNodes = songs.mapIndexed { i, s ->
                    val sAngle = i * (2 * PI / songs.size.coerceAtLeast(1)).toFloat()
                    GalaxyNode(
                        id = s.id.toString(),
                        label = s.title,
                        type = NodeType.SONG,
                        position = Offset.Zero, // Managed by Orbit in Screen
                        color = generateColorForArtist(artistName),
                        song = s,
                        orbitRadius = 120f + (i * 15f),
                        orbitSpeed = 0.005f + (Random.nextFloat() * 0.01f),
                        initialAngle = sAngle,
                        albumArtUri = s.albumArtUri
                    )
                }

                artistNodes.add(GalaxyNode(
                    id = "artist_$artistName",
                    label = artistName,
                    type = NodeType.ARTIST,
                    position = artistPos,
                    color = generateColorForArtist(artistName),
                    children = songNodes,
                    songCount = songs.size,
                    artistImageUri = artistImages[artistName]?.let { android.net.Uri.parse(it) }
                ))
                artistIndex++
            }

            val finalRoot = rootNode.copy(children = artistNodes)

            // Calculate Topology Bounds for the Mini-Map Navigator
            var minX = 0f
            var maxX = 0f
            var minY = 0f
            var maxY = 0f
            artistNodes.forEach { 
                minX = minOf(minX, it.position.x)
                maxX = maxOf(maxX, it.position.x)
                minY = minOf(minY, it.position.y)
                maxY = maxOf(maxY, it.position.y)
            }
            // Add cinematic buffer (padding) for the radar view
            val buffer = 500f
            val bounds = androidx.compose.ui.geometry.Rect(minX - buffer, minY - buffer, maxX + buffer, maxY + buffer)

            _uiState.update { current ->
                if (current is GalaxyUiState.Success) {
                    current.copy(
                        nodes = listOf(finalRoot),
                        sceneState = current.sceneState.copy(
                            currentScene = GalaxySceneType.EARTH,
                            topologyBounds = bounds
                        )
                    )
                } else {
                    GalaxyUiState.Success(
                        nodes = listOf(finalRoot),
                        sceneState = SceneState(
                            currentScene = GalaxySceneType.EARTH,
                            topologyBounds = bounds
                        )
                    )
                }
            }
        }
    }

    private fun generateColorForArtist(artist: String): Color {
        val hash = artist.hashCode()
        return Color(
            red = (hash and 0xFF0000 shr 16) / 255f,
            green = (hash and 0x00FF00 shr 8) / 255f,
            blue = (hash and 0x0000FF) / 255f,
            alpha = 1f
        ).copy(alpha = 0.8f)
    }

    private fun prepareSongsScene(featureId: String?) {
        viewModelScope.launch {
            val nodes = when (featureId) {
                "moon" -> { // Favorites
                    musicRepository.getFavoriteSongs().first()
                        .mapIndexed { i: Int, song: Song ->
                            createSongNode(song, i, 1)
                        }
                }
                "clouds" -> { // Recent
                    musicRepository.getRecentlyPlayedSongs(20)
                        .mapIndexed { i: Int, song: Song ->
                            createSongNode(song, i, 20)
                        }
                }
                "cities" -> { // Top Artists Hub
                    val artists = musicRepository.getAllSongs().first().groupBy { s: Song -> s.artist }
                    val artistNodes = artists.map { (artist: String, songs: List<Song>) ->
                        GalaxyNode(
                            id = artist,
                            label = artist,
                            type = NodeType.ARTIST,
                            position = Offset(Random.nextFloat() * 2000f - 1000f, Random.nextFloat() * 2000f - 1000f),
                            color = Color.White,
                            songCount = songs.size,
                            children = songs.mapIndexed { i: Int, s: Song -> createSongNode(s, i, songs.size) }
                        )
                    }
                    
                    // Create the Central Anchor (The "Star" of the category)
                    listOf(GalaxyNode(
                        id = "root_artists",
                        label = "Artists",
                        type = NodeType.GENRE, 
                        position = Offset.Zero,
                        color = Color(0xFFFFD700),
                        children = artistNodes,
                        songCount = artists.size
                    ))
                }
                "aurora" -> { // Most Played
                    musicRepository.getTopPlayedSongs(15)
                        .mapIndexed { i: Int, song: Song ->
                            createSongNode(song, i, 15)
                        }
                }
                else -> emptyList()
            }
            _uiState.update { currentState ->
                if (currentState is GalaxyUiState.Success) {
                    currentState.copy(nodes = nodes)
                } else currentState
            }
        }
    }

    private fun createSongNode(song: Song, index: Int, total: Int): GalaxyNode {
        val angle = (index.toFloat() / total) * 2 * PI.toFloat()
        val radius = 300f + (Random.nextFloat() * 100f)
        return GalaxyNode(
            id = song.id.toString(),
            label = song.title,
            type = NodeType.SONG,
            position = Offset(cos(angle) * radius, sin(angle) * radius),
            color = Color.Cyan,
            song = song,
            orbitRadius = 150f + (index * 20f),
            orbitSpeed = 0.01f + (index * 0.001f),
            initialAngle = angle
        )
    }

    fun navigateBack() {
        _uiState.update { current ->
            if (current is GalaxyUiState.Success) {
                // 1. If in Artist Focus Mode, go back to Artist Universe Cluster
                if (current.sceneState.currentScene == GalaxySceneType.SONGS && current.nodes.size == 1) {
                    prepareSongsScene("cities") // Re-load Top Artists cluster
                    return@update current
                }

                val prevScene = when (current.sceneState.currentScene) {
                    GalaxySceneType.SONGS -> GalaxySceneType.EARTH
                    GalaxySceneType.EARTH -> GalaxySceneType.SOLAR_SYSTEM
                    GalaxySceneType.SOLAR_SYSTEM -> GalaxySceneType.GALAXY
                    GalaxySceneType.GALAXY -> GalaxySceneType.UNIVERSE
                    else -> null
                }
                
                if (prevScene != null) {
                    transitionTo(prevScene)
                }
                current
            } else current
        }
    }

    /**
     * Entry point to trigger galaxy generation.
     */

    fun loadGalaxy() {
        if (isLoaded || !hasPermission()) return
        
        viewModelScope.launch {
            isLoaded = true
            try {
                val songs = withContext(Dispatchers.IO) {
                    musicRepository.getAllSongs().first()
                }
                
                if (songs.isEmpty()) {
                    _uiState.value = GalaxyUiState.Empty
                    return@launch
                }

                val generatedNodes = withContext(Dispatchers.Default) {
                    generateGalaxyTopology(songs)
                }

                if (generatedNodes.isNotEmpty()) {
                    _initialFocusOffset.value = generatedNodes.first().position
                    
                    // Step 2: Static Universe Definition with Cinematic Palettes
                    val mwData = generateGalaxyPointCloud(isPrimary = true, colorTheme = "gold")
                    val andData = generateGalaxyPointCloud(isPrimary = false, colorTheme = "peach")
                    val somData = generateGalaxyPointCloud(isPrimary = false, colorTheme = "sapphire")
                    val whpData = generateGalaxyPointCloud(isPrimary = false, colorTheme = "cyan")
                    val triData = generateGalaxyPointCloud(isPrimary = false, colorTheme = "violet")
                    val cenData = generateGalaxyPointCloud(isPrimary = false, colorTheme = "pink")

                    // Step 3: THE MASSIVE INTERNAL MILKY WAY (High Density for GalaxyScene)
                    val internalMwData = generateGalaxyPointCloud(isPrimary = true, colorTheme = "gold", densityMultiplier = 3.5f)
                    val milkyWayInternal = UniverseGalaxy("mw_internal", "Milky Way", Offset.Zero, true, internalMwData.first, internalMwData.second, internalMwData.third)

                    val universe = listOf(
                        UniverseGalaxy("mw", "Milky Way", Offset(0f, 0f), true, mwData.first, mwData.second, mwData.third),
                        UniverseGalaxy("and", "Andromeda", Offset(500f, 250f), false, andData.first, andData.second, andData.third),
                        UniverseGalaxy("som", "Sombrero", Offset(-400f, -300f), false, somData.first, somData.second, somData.third),
                        UniverseGalaxy("whp", "Whirlpool", Offset(250f, -400f), false, whpData.first, whpData.second, whpData.third),
                        UniverseGalaxy("tri", "Triangulum", Offset(-450f, 350f), false, triData.first, triData.second, triData.third),
                        UniverseGalaxy("cen", "Centaurus A", Offset(550f, -150f), false, cenData.first, cenData.second, cenData.third)
                    )

                    // Step 5: Static Galaxy Arms Definition (Anchored inside the internal MW)
                    val arms = listOf(
                        GalaxyArm("1", "Orion Arm", 0f, 150f, isSolarSystem = true, 
                            anchorOffset = calculateArmAnchor(0f, 150f),
                            palette = GalaxyPalette(Color(0xFF00FFFF), Color(0xFF00BCD4))),
                        GalaxyArm("2", "Sagittarius Arm", 1.5f, 230f, 
                            anchorOffset = calculateArmAnchor(1.5f, 230f),
                            palette = GalaxyPalette(Color(0xFFFF9800), Color(0xFFFF5722))),
                        GalaxyArm("3", "Perseus Arm", 3.0f, 320f, 
                            anchorOffset = calculateArmAnchor(3.0f, 320f),
                            palette = GalaxyPalette(Color(0xFFADD8E6), Color(0xFF87CEEB))),
                        GalaxyArm("4", "Scutum Arm", 4.5f, 200f, 
                            anchorOffset = calculateArmAnchor(4.5f, 200f),
                            palette = GalaxyPalette(Color(0xFFE0E0E0), Color(0xFFB0B0B0)))
                    )

                    // Step 4: Static Solar System Definition
                    val planets = listOf(
                        Planet("sun", "Sun", 0f, 40f, Color.Yellow, 0f, assetName = "sun_texture"),
                        Planet("mercury", "Mercury", 85f, 6f, Color.Gray, 1.25f, assetName = "mercury_texture"),
                        Planet("venus", "Venus", 130f, 11f, Color(0xFFFFC107), 0.95f, assetName = "venus_texture"),
                        Planet("earth", "Earth", 185f, 13f, Color.Blue, 0.75f, isEarth = true, assetName = "earth_texture"),
                        Planet("mars", "Mars", 240f, 10f, Color.Red, 0.65f, assetName = "mars_texture"),
                        Planet("jupiter", "Jupiter", 340f, 26f, Color(0xFFFF9800), 0.45f, assetName = "jupiter_texture"),
                        Planet("saturn", "Saturn", 460f, 22f, Color(0xFFF5DEB3), 0.35f, assetName = "saturn_texture"),
                        Planet("uranus", "Uranus", 570f, 17f, Color(0xFFADD8E6), 0.25f, assetName = "uranus_texture"),
                        Planet("neptune", "Neptune", 660f, 17f, Color(0xFF1E90FF), 0.18f, assetName = "neptune_texture")
                    )

                    _uiState.value = GalaxyUiState.Success(
                        nodes = generatedNodes,
                        sceneState = SceneState(
                            universeGalaxies = universe,
                            milkyWayInternal = milkyWayInternal,
                            galaxyArms = arms,
                            planets = planets
                        )
                    )
                } else {
                    _uiState.value = GalaxyUiState.Empty
                }
            } catch (e: Exception) {
                _uiState.value = GalaxyUiState.Empty
            }
        }
    }

    /**
     * Core Topology Generator
     */
    private fun generateGalaxyTopology(allSongs: List<Song>): List<GalaxyNode> {
        val limitedSongs = allSongs.take(1000)
        val genreGroups = limitedSongs.groupBy { it.genre ?: "Uncharted Signals" }
        
        // Calculate dynamic scaling based on library density
        val totalArtists = genreGroups.values.sumOf { it.groupBy { s -> s.artist }.size }
        val avgArtistsPerGenre = (totalArtists.toFloat() / genreGroups.size.coerceAtLeast(1)).coerceAtLeast(1f)
        
        return genreGroups.entries.mapIndexed { gIndex, (genre, genreSongs) ->
            val artistGroups = genreSongs.groupBy { it.artist }
            
            // 1. Position Genre in a growing spiral
            val sizeFactor = 1f + (artistGroups.size / avgArtistsPerGenre)
            val genreAngle = gIndex * 0.8f 
            val genreRadius = gIndex * 600f * sizeFactor
            val genrePos = Offset(
                genreRadius * cos(genreAngle),
                genreRadius * sin(genreAngle)
            )

            // 2. Generate Artist Clusters
            val artistNodes = generateArtistClusters(artistGroups, genrePos)
            
            // 3. Define Genre Territorial Limit (for UI culling/zoom logic)
            val genreArtistGap = 2f * genreRadius * sin(0.4f)
            val genreLimit = (genreArtistGap * 0.35f) - 150f

            GalaxyNode(
                id = "genre_$genre",
                label = genre,
                type = NodeType.GENRE,
                position = genrePos,
                color = randomPastelColor(),
                songCount = genreSongs.size,
                children = artistNodes,
                territorialLimit = genreLimit.coerceAtLeast(400f)
            )
        }
    }

    /**
     * Generates a cluster of Artist nodes around a Genre center.
     */
    private fun generateArtistClusters(
        artistGroups: Map<String, List<Song>>, 
        center: Offset
    ): List<GalaxyNode> {
        val artists = artistGroups.keys.toList()
        val artistCount = artists.size
        val maxPerRing = 8
        
        return artists.mapIndexed { idx, artist ->
            val ringIndex = idx / maxPerRing
            val indexInRing = idx % maxPerRing
            val artistsInThisRing = if ((ringIndex + 1) * maxPerRing <= artistCount) maxPerRing else artistCount % maxPerRing
            
            val angleStep = (2f * PI.toFloat()) / artistsInThisRing.coerceAtLeast(1)
            val ringRadius = (ringIndex + 1) * 600f
            val artistAngle = indexInRing * angleStep
            
            val artistPos = center + Offset(
                ringRadius * cos(artistAngle),
                ringRadius * sin(artistAngle)
            )

            // Calculate territorial limit for song clusters
            val artistGap = 2f * ringRadius * sin(angleStep / 2f)
            val maxClusterRadius = artistGap * 0.35f

            // Generate Song Orbits
            val songs = artistGroups[artist] ?: emptyList()
            val songNodes = generateSongOrbits(songs, artistPos, maxClusterRadius)

            GalaxyNode(
                id = "artist_$artist",
                label = artist,
                type = NodeType.ARTIST,
                position = artistPos,
                color = randomPastelColor(),
                songCount = songs.size,
                children = songNodes,
                territorialLimit = maxClusterRadius
            )
        }
    }

    /**
     * Generates orbiting Song nodes around an Artist center.
     */
    private fun generateSongOrbits(
        songs: List<Song>, 
        center: Offset, 
        maxRadius: Float
    ): List<GalaxyNode> {
        val sortedSongs = songs.sortedBy { it.id }
        val songNodes = mutableListOf<GalaxyNode>()
        
        var currentSongIndex = 0
        var ringIndex = 0
        val baseRadius = 90f
        val ringSpacing = 40f
        val nodePadding = 16f
        val nodeSize = 36f

        while (currentSongIndex < sortedSongs.size) {
            val radius = baseRadius + (ringSpacing * ringIndex)
            if (radius > maxRadius) break // Territorial safety check

            val circumference = 2f * PI.toFloat() * radius
            val capacityMultiplier = if (ringIndex == 0) 0.65f else 0.85f
            val ringCapacity = ((circumference / (nodeSize + nodePadding)) * capacityMultiplier).toInt().coerceAtLeast(1)
            
            val songsInRing = (sortedSongs.size - currentSongIndex).coerceAtMost(ringCapacity)
            
            for (i in 0 until songsInRing) {
                val song = sortedSongs[currentSongIndex]
                val angle = i * (2f * PI.toFloat() / songsInRing)
                val speed = (0.25f - ringIndex * 0.03f).coerceAtLeast(0.05f)
                
                songNodes.add(
                    GalaxyNode(
                        id = song.id.toString(),
                        label = song.title.cleanSongTitle(),
                        type = NodeType.SONG,
                        position = center,
                        color = randomPastelColor(),
                        song = song,
                        orbitRadius = radius,
                        orbitSpeed = speed,
                        orbitDirection = if (ringIndex % 2 == 0) 1 else -1,
                        initialAngle = angle
                    )
                )
                currentSongIndex++
            }
            ringIndex++
        }
        return songNodes
    }

    private fun generateArmStream(
        baseAngle: Float, 
        baseRadius: Float, 
        isSolarSystem: Boolean
    ): List<GalaxyPoint> {
        val points = mutableListOf<GalaxyPoint>()
        val pointCount = if (isSolarSystem) 150 else 100 // Orion is denser
        
        for (i in 0 until pointCount) {
            val progress = i.toFloat() / pointCount
            // Logarithmic spiral segment math
            val armExpansion = progress * 0.8f
            val angle = baseAngle + armExpansion + (sin(i * 0.5f) * 0.08f)
            
            // Random spread for "Dust Lane" effect
            val spreadX = (Random.nextFloat() - 0.5f) * 45f * (1f + progress)
            val spreadY = (Random.nextFloat() - 0.5f) * 45f * (1f + progress)
            
            val pX = cos(angle.toDouble()).toFloat() * baseRadius + spreadX
            val pY = sin(angle.toDouble()).toFloat() * baseRadius + spreadY
            
            val alpha = (0.6f - progress * 0.4f).coerceIn(0.1f, 0.6f)
            val pSize = 0.8f + Random.nextFloat() * 1.5f
            
            points.add(GalaxyPoint(Offset(pX, pY), alpha, pSize))
        }
        return points
    }

    private fun hasPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun generateGalaxyPointCloud(
        isPrimary: Boolean, 
        colorTheme: String,
        densityMultiplier: Float = 1.0f
    ): Triple<List<GalaxyPoint>, GalaxyBounds, GalaxyPalette> {
        val points = mutableListOf<GalaxyPoint>()
        
        // ULTRA-DENSITY 10-ARM BUDGET
        // Increasing to ~6000 particles to fill the 10-arm structure
        val baseCount = if (isPrimary) 2400 else 1000
        val totalCount = (baseCount * densityMultiplier).toInt()
        
        val armCount = if (isPrimary) 10 else 6 // Standard Phase 2 arm count
        val galaxySize = if (isPrimary) (85f * if (densityMultiplier > 1f) 4.5f else 1f) else 55f
        
        var sumX = 0f
        var sumY = 0f

        // 1. GOLD CORE INFUSION (Filling the center void)
        // Dedicated population to ensure "no space" around the core
        generateCoreFiller(points, (totalCount * 0.25f).toInt(), galaxySize * 0.3f)

        // 2. Standard 10-ARM Structure
        generateLayer(points, (totalCount * 0.75f).toInt(), armCount, galaxySize)

        // Centroid calculation for normalization
        points.forEach { sumX += it.offset.x; sumY += it.offset.y }
        // ... Normalization logic remains exactly the same to preserve pivot = Offset.Zero

        // Calculate Arithmetic Centroid for perfect stabilization
        val centroidX = sumX / points.size
        val centroidY = sumY / points.size
        val centroid = Offset(centroidX, centroidY)

        // Second Pass: Normalize and finalize bounds
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        var maxExtent = 0f

        val normalizedPoints = points.map { point ->
            val shiftedOffset = point.offset - centroid
            
            minX = minX.coerceAtMost(shiftedOffset.x)
            maxX = maxX.coerceAtLeast(shiftedOffset.x)
            minY = minY.coerceAtMost(shiftedOffset.y)
            maxY = maxY.coerceAtLeast(shiftedOffset.y)
            maxExtent = maxExtent.coerceAtLeast(shiftedOffset.getDistance())

            point.copy(offset = shiftedOffset)
        }
        
        val palette = when(colorTheme) {
            "gold" -> GalaxyPalette(Color(0xFFFFD700), Color(0xFFFF9800), Color.White)
            "cyan" -> GalaxyPalette(Color(0xFF00FFFF), Color(0xFF00BCD4), Color.White)
            "violet" -> GalaxyPalette(Color(0xFFEE82EE), Color(0xFF9C27B0), Color.White)
            "peach" -> GalaxyPalette(Color(0xFFFFDAB9), Color(0xFFFF5722), Color.White)
            "sapphire" -> GalaxyPalette(Color(0xFF0F52BA), Color(0xFFB0E0E6), Color.White)
            "pink" -> GalaxyPalette(Color(0xFFFFC0CB), Color(0xFFE91E63), Color.White)
            else -> GalaxyPalette(Color.White, Color.Gray)
        }

        val bounds = GalaxyBounds(
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
            visualCenter = Offset.Zero,
            maxExtent = maxExtent,
            interactionRadius = maxExtent * 1.3f
        )
        
        return Triple(normalizedPoints, bounds, palette)
    }

    private fun generateLayer(
        points: MutableList<GalaxyPoint>,
        count: Int,
        armCount: Int,
        size: Float
    ) {
        val eccentricity = 0.75f
        val palette = listOf(
            Color(0xFFFF69B4), Color(0xFF00BFFF), Color(0xFF90EE90), 
            Color(0xFFFFA500), Color(0xFFD81B60), Color(0xFFFFD700), Color(0xFFE0E0E0)
        )

        for (arm in 0 until armCount) {
            val armOffset = arm * (2 * PI.toFloat() / armCount)
            for (i in 0 until count / armCount) {
                val progress = i.toFloat() / (count / armCount)
                
                val spiralAngle = progress * 9.5f + armOffset
                val jitter = (sin(i * 1.5f) * 0.12f) + (Random.nextFloat() * 0.04f)
                val angle = spiralAngle + jitter
                
                val spread = (Random.nextFloat() - 0.5f) * 45f * progress
                val radius = progress * size * 0.98f + spread
                
                val pX = cos(angle.toDouble()).toFloat() * radius
                val pY = sin(angle.toDouble()).toFloat() * radius * eccentricity
                
                val colorRand = Random.nextFloat()
                val finalColor = when {
                    progress < 0.15f -> Color(0xFFFFD700)
                    else -> {
                        val index = (colorRand * palette.size).toInt().coerceIn(0, palette.size - 1)
                        palette[index]
                    }
                }

                // GLOWING STAR LOGIC (10% chance)
                if (Random.nextFloat() > 0.9f) {
                    // 1. The Radiant Halo (Soft Glow)
                    points.add(GalaxyPoint(Offset(pX, pY), 0.08f, 6.0f, finalColor))
                    // 2. The Nucleus (Sharp Point)
                    points.add(GalaxyPoint(Offset(pX, pY), 0.95f, 1.0f, Color.White))
                } else {
                    // Standard Fine Dust Speck
                    val pAlpha = (0.95f - progress * 0.35f).coerceIn(0.4f, 1.0f)
                    val pSize = 0.25f + Random.nextFloat() * 0.55f 
                    points.add(GalaxyPoint(Offset(pX, pY), pAlpha, pSize, finalColor))
                }
            }
        }
    }

    private fun generateCoreFiller(
        points: MutableList<GalaxyPoint>,
        count: Int,
        radiusLimit: Float
    ) {
        val eccentricity = 0.75f
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 2 * PI.toFloat()
            val dist = Random.nextFloat() * radiusLimit
            
            val pX = cos(angle.toDouble()).toFloat() * dist
            val pY = sin(angle.toDouble()).toFloat() * dist * eccentricity
            
            points.add(GalaxyPoint(
                Offset(pX, pY), 
                0.7f + Random.nextFloat() * 0.3f, 
                0.3f + Random.nextFloat() * 0.4f, 
                Color(0xFFFFD700)
            ))
        }
    }

    private fun generateCoreCluster(
        points: MutableList<GalaxyPoint>,
        count: Int,
        radiusRange: ClosedFloatingPointRange<Float>,
        alphaRange: ClosedFloatingPointRange<Float>,
        color: Color
    ) {
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 2 * PI.toFloat()
            val dist = Random.nextFloat() * Random.nextFloat() * 12f 
            
            val pX = cos(angle.toDouble()).toFloat() * dist
            val pY = sin(angle.toDouble()).toFloat() * dist
            
            // Cluster Nucleus
            val pAlpha = alphaRange.start + Random.nextFloat() * (alphaRange.endInclusive - alphaRange.start)
            val pSize = radiusRange.start + Random.nextFloat() * (radiusRange.endInclusive - radiusRange.start)
            points.add(GalaxyPoint(Offset(pX, pY), pAlpha, pSize, color))
            
            // Add extra "Bloom" specks for the core
            if (Random.nextFloat() > 0.7f) {
                points.add(GalaxyPoint(Offset(pX, pY), 0.05f, 15f, color))
            }
        }
    }

    private fun calculateArmAnchor(angle: Float, radius: Float): Offset {
        return Offset(cos(angle) * radius, sin(angle) * radius)
    }

    private fun randomPastelColor(): Color {
        val h = Random.nextFloat() * 360f
        return Color.hsl(h, 0.6f, 0.7f)
    }
}
