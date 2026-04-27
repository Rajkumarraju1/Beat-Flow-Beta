package com.pralayakaveri.beatflow.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.pralayakaveri.beatflow.domain.model.Album
import com.pralayakaveri.beatflow.domain.model.Artist
import com.pralayakaveri.beatflow.domain.model.Song
import com.pralayakaveri.beatflow.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val generateMoodPlaylistUseCase: com.pralayakaveri.beatflow.domain.usecase.GenerateMoodPlaylistUseCase,
    private val workManager: WorkManager
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    fun getFilteredSongs(searchQuery: StateFlow<String>): StateFlow<List<Song>> = 
        combine(_songs, searchQuery) { songs, query ->
            if (query.isBlank()) songs
            else songs.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.artist.contains(query, ignoreCase = true) 
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

    private val _folders = MutableStateFlow<Map<String, List<Song>>>(emptyMap())
    val folders: StateFlow<Map<String, List<Song>>> = _folders.asStateFlow()

    private val _favorites = MutableStateFlow<List<Song>>(emptyList())
    val favorites: StateFlow<List<Song>> = _favorites.asStateFlow()

    private val _recentlyAdded = MutableStateFlow<List<Song>>(emptyList())
    val recentlyAdded: StateFlow<List<Song>> = _recentlyAdded.asStateFlow()

    private val _mostPlayed = MutableStateFlow<List<Song>>(emptyList())
    val mostPlayed: StateFlow<List<Song>> = _mostPlayed.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())
    val recentlyPlayed: StateFlow<List<Song>> = _recentlyPlayed.asStateFlow()

    private val _workoutPlaylist = MutableStateFlow<List<Song>>(emptyList())
    val workoutPlaylist: StateFlow<List<Song>> = _workoutPlaylist.asStateFlow()

    private val _chillPlaylist = MutableStateFlow<List<Song>>(emptyList())
    val chillPlaylist: StateFlow<List<Song>> = _chillPlaylist.asStateFlow()

    private val _focusPlaylist = MutableStateFlow<List<Song>>(emptyList())
    val focusPlaylist: StateFlow<List<Song>> = _focusPlaylist.asStateFlow()

    private val _drivingPlaylist = MutableStateFlow<List<Song>>(emptyList())
    val drivingPlaylist: StateFlow<List<Song>> = _drivingPlaylist.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadData()
        observeFavorites()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            musicRepository.getFavoriteSongs().collect { favs ->
                _favorites.value = favs
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            
            // 1. Fetch songs first (fastest) - achieving "Instant UI"
            val loadedSongs = musicRepository.getSongs()
            _songs.value = loadedSongs
            
            // Group songs by folder
            _folders.value = loadedSongs.groupBy { song ->
                val lastSlashIndex = song.dataPath.lastIndexOf('/')
                if (lastSlashIndex != -1) {
                    song.dataPath.substring(0, lastSlashIndex)
                } else {
                    "Unknown Folder"
                }
            }
            
            // 2. Load other core data in parallel
            supervisorScope {
                val albumsDeferred = async { musicRepository.getAlbums() }
                val artistsDeferred = async { musicRepository.getArtists() }
                val recentlyAddedDeferred = async { musicRepository.getRecentlyAddedSongs() }
                val topPlayedDeferred = async { musicRepository.getTopPlayedSongs() }
                val recentlyPlayedDeferred = async { musicRepository.getRecentlyPlayedSongs() }
                
                // Once primary lists are ready, we can stop the main loader
                _isLoading.value = false
                
                _albums.value = albumsDeferred.await()
                _artists.value = artistsDeferred.await()
                _recentlyAdded.value = recentlyAddedDeferred.await()
                _mostPlayed.value = topPlayedDeferred.await()
                _recentlyPlayed.value = recentlyPlayedDeferred.await()
                
                // 3. Load mood playlists in background (using cached metadata)
                val workoutDeferred = async { generateMoodPlaylistUseCase.getWorkoutPlaylist() }
                val chillDeferred = async { generateMoodPlaylistUseCase.getChillPlaylist() }
                val focusDeferred = async { generateMoodPlaylistUseCase.getFocusPlaylist() }
                val drivingDeferred = async { generateMoodPlaylistUseCase.getDrivingPlaylist() }
                
                _workoutPlaylist.value = workoutDeferred.await()
                _chillPlaylist.value = chillDeferred.await()
                _focusPlaylist.value = focusDeferred.await()
                _drivingPlaylist.value = drivingDeferred.await()
            }
            
            // 4. Trigger background metadata scan if needed
            enqueueMetadataWork()
        }
    }

    private fun enqueueMetadataWork() {
        val workRequest = OneTimeWorkRequestBuilder<com.pralayakaveri.beatflow.data.worker.MetadataWorker>()
            .build()
        workManager.enqueue(workRequest)
    }
}
