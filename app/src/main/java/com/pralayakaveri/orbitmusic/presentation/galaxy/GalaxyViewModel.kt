package com.pralayakaveri.orbitmusic.presentation.galaxy

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pralayakaveri.orbitmusic.domain.model.Song
import com.pralayakaveri.orbitmusic.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.random.Random
import com.pralayakaveri.orbitmusic.domain.util.cleanSongTitle

data class GalaxyNode(
    val id: String,
    val label: String,
    val type: NodeType,
    val position: Offset, // Base position (anchor)
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

sealed interface GalaxyUiState {
    object Loading : GalaxyUiState
    object Empty : GalaxyUiState
    data class Success(val nodes: List<GalaxyNode>) : GalaxyUiState
}

@HiltViewModel
class GalaxyViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private fun hasPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private val _uiState = MutableStateFlow<GalaxyUiState>(GalaxyUiState.Loading)
    val uiState: StateFlow<GalaxyUiState> = _uiState.asStateFlow()

    private val _initialFocusOffset = MutableStateFlow(Offset.Zero)
    val initialFocusOffset: StateFlow<Offset> = _initialFocusOffset.asStateFlow()

    init {
        // Triggered by UI after permission gate
    }

    private var isLoaded = false

    fun loadGalaxy() {
        if (isLoaded || !hasPermission()) return
        
        android.util.Log.d("GALAXY_PERF", "Data Load START")
        
        viewModelScope.launch {
            isLoaded = true
            try {
                // Fetch data on IO
                val songs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    musicRepository.getAllSongs().first()
                }
                
                if (songs.isEmpty()) {
                    _uiState.value = GalaxyUiState.Empty
                    return@launch
                }

                // Process nodes on Default
                val genreNodes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val limitedSongs = songs.take(1000)
                    val genreGroups = limitedSongs.groupBy { it.genre ?: "Uncharted Signals" }
                    genreGroups.entries.mapIndexed { gIndex, (genre, genreSongs) ->
                        val artistGroups = genreSongs.groupBy { it.artist }
                        
                        val artistNodes = artistGroups.entries.mapIndexed { aIndex, (artist, artistSongs) ->
                            val songNodes = artistSongs.take(50).mapIndexed { sIndex, song ->
                                    val ringIndex = sIndex / 6
                                    val orbitRadius = 115f + ringIndex * 60f
                                    val speed = (0.4f - ringIndex * 0.05f).coerceAtLeast(0.1f)
                                    val direction = if (ringIndex % 2 == 0) 1 else -1
                                    
                                    val songsInRing = minOf(6, artistSongs.size - (ringIndex * 6))
                                    val angleOffset = ((2f * kotlin.math.PI.toFloat() * (sIndex % 6)) / songsInRing) + (ringIndex * 0.3f)
                                    
                                    GalaxyNode(
                                        id = song.id.toString(),
                                        label = song.title.cleanSongTitle(),
                                        type = NodeType.SONG,
                                        position = Offset.Zero,
                                        color = randomPastelColor(),
                                        song = song,
                                        orbitRadius = orbitRadius,
                                        orbitSpeed = speed,
                                        orbitDirection = direction,
                                        initialAngle = angleOffset
                                    )
                                }

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
                }

                if (genreNodes.isNotEmpty()) {
                    _initialFocusOffset.value = Offset.Zero
                    _uiState.value = GalaxyUiState.Success(genreNodes)
                } else {
                    _uiState.value = GalaxyUiState.Empty
                }
            } catch (e: Exception) {
                _uiState.value = GalaxyUiState.Empty
            }
        }
    }

    private fun getSpiralOffset(index: Int, scale: Float): Offset {
        val angle = index * 0.7f 
        val initialRadius = 0f 
        val radius = initialRadius + (scale * 1.5f) * kotlin.math.sqrt(index.toFloat())
        
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
