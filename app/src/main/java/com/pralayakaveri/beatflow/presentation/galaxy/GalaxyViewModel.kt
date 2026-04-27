package com.pralayakaveri.beatflow.presentation.galaxy

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pralayakaveri.beatflow.domain.model.Song
import com.pralayakaveri.beatflow.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random
import com.pralayakaveri.beatflow.domain.util.cleanSongTitle

data class GalaxyNode(
    val id: String,
    val label: String,
    val type: NodeType,
    var position: Offset, // Base position (anchor)
    val color: Color,
    val songCount: Int = 0,
    val song: Song? = null,
    val children: List<GalaxyNode> = emptyList(),
    // Orbit properties
    val orbitRadius: Float = 0f,
    val orbitSpeed: Float = 0f,
    val orbitDirection: Int = 1, // 1 for CW, -1 for CCW
    val initialAngle: Float = 0f,
    var paletteColor: Color? = null
)

enum class NodeType {
    GENRE, ARTIST, SONG
}

@HiltViewModel
class GalaxyViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _nodes = MutableStateFlow<List<GalaxyNode>>(emptyList())
    val nodes: StateFlow<List<GalaxyNode>> = _nodes.asStateFlow()

    private val _zoomLevel = MutableStateFlow(1f)
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _initialFocusOffset = MutableStateFlow(Offset.Zero)
    val initialFocusOffset: StateFlow<Offset> = _initialFocusOffset.asStateFlow()

    init {
        loadGalaxy()
    }

    private fun loadGalaxy() {
        viewModelScope.launch {
            musicRepository.getAllSongs().collect { songs ->
                // Apply a global limit for total songs handled in Galaxy Mode for performance
                val limitedSongs = songs.take(1000) 
                
                val genreGroups = limitedSongs.groupBy { it.genre ?: "Unknown" }
                val genreNodes = genreGroups.entries.mapIndexed { gIndex, (genre, genreSongs) ->
                    val artistGroups = genreSongs.groupBy { it.artist }
                    
                    val artistNodes = artistGroups.entries.mapIndexed { aIndex, (artist, artistSongs) ->
                        val songNodes = if (artistSongs.size < 50) { 
                            artistSongs.mapIndexed { sIndex, song ->
                                // Songs rotate around artists
                                // Distance increases with index for rings
                                val ringIndex = sIndex / 5 // 5 songs per ring
                                val orbitRadius = 80f + ringIndex * 40f
                                val speed = 0.5f / (ringIndex + 1f) // Outer move slower
                                val direction = if (ringIndex % 2 == 0) 1 else -1
                                
                                GalaxyNode(
                                    id = song.id.toString(),
                                    label = song.title.cleanSongTitle(),
                                    type = NodeType.SONG,
                                    position = Offset.Zero, // Relative to artist
                                    color = randomPastelColor(),
                                    song = song,
                                    orbitRadius = orbitRadius,
                                    orbitSpeed = speed,
                                    orbitDirection = direction,
                                    initialAngle = Random.nextFloat() * 2f * kotlin.math.PI.toFloat()
                                )
                            }
                        } else emptyList()

                        GalaxyNode(
                            id = "artist_$artist",
                            label = artist,
                            type = NodeType.ARTIST,
                            position = getSpiralOffset(aIndex, 400f),
                            color = randomPastelColor(),
                            songCount = artistSongs.size,
                            children = songNodes
                        )
                    }
                    GalaxyNode(
                        id = "genre_$genre",
                        label = genre,
                        type = NodeType.GENRE,
                        position = getSpiralOffset(gIndex, 1200f),
                        color = randomPastelColor(),
                        songCount = genreSongs.size,
                        children = artistNodes
                    )
                }
                _nodes.value = genreNodes
                if (genreNodes.isNotEmpty()) {
                    // Set initial focus to the first genre node
                    _initialFocusOffset.value = -genreNodes.first().position
                }
                _isLoading.value = false
            }
        }
    }

    private fun getSpiralOffset(index: Int, scale: Float): Offset {
        // Increase spread angle and add a significant initial radius for center breathing room
        val angle = index * 0.7f 
        val initialRadius = 500f // Even more breathing room at the beginning
        val radius = initialRadius + (scale * 1.5f) * kotlin.math.sqrt(index.toFloat() + 1f)
        
        return Offset(
            radius * kotlin.math.cos(angle.toDouble()).toFloat(),
            radius * kotlin.math.sin(angle.toDouble()).toFloat()
        )
    }

    private fun randomPastelColor(): Color {
        val h = Random.nextFloat() * 360f
        return Color.hsl(h, 0.6f, 0.7f)
    }
}
