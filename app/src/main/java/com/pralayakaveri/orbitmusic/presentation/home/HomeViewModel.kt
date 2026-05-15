package com.pralayakaveri.orbitmusic.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.pralayakaveri.orbitmusic.domain.model.Album
import com.pralayakaveri.orbitmusic.domain.model.Artist
import com.pralayakaveri.orbitmusic.domain.model.Song
import com.pralayakaveri.orbitmusic.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.pralayakaveri.orbitmusic.presentation.util.ScrollResetSignal
import com.pralayakaveri.orbitmusic.domain.util.SearchUtils
import javax.inject.Inject

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val generateMoodPlaylistUseCase: com.pralayakaveri.orbitmusic.domain.usecase.GenerateMoodPlaylistUseCase,
    private val workManager: WorkManager,
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

    val songs: StateFlow<List<Song>> = musicRepository.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val sortOrder: StateFlow<com.pralayakaveri.orbitmusic.domain.model.SortOrder> = musicRepository.getSortOrder()
        .stateIn(viewModelScope, SharingStarted.Lazily, com.pralayakaveri.orbitmusic.domain.model.SortOrder.TITLE)

    fun setSortOrder(order: com.pralayakaveri.orbitmusic.domain.model.SortOrder, onSortOrderChanged: () -> Unit = {}) {
        viewModelScope.launch {
            if (sortOrder.value != order) {
                musicRepository.setSortOrder(order)
                onSortOrderChanged()
            }
        }
    }

    fun getFilteredSongs(searchQuery: StateFlow<String>): StateFlow<List<Song>> = 
        combine(songs, searchQuery) { songs, query ->
            if (query.isBlank()) songs
            else {
                val normalizedQuery = SearchUtils.normalize(query)
                songs.filter { 
                    SearchUtils.normalize(it.title).contains(normalizedQuery) || 
                    SearchUtils.normalize(it.artist).contains(normalizedQuery) ||
                    SearchUtils.normalize(it.album).contains(normalizedQuery)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val folders: StateFlow<Map<String, List<Song>>> = songs
        .map { list -> list.groupBy { it.dataPath.substringBeforeLast("/", "Unknown") } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val albumSongsMap: StateFlow<Map<Long, List<Song>>> = songs
        .map { list -> list.groupBy { it.albumId } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val artistSongsMap: StateFlow<Map<String, List<Song>>> = songs
        .map { list -> list.groupBy { it.artist } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val favorites: StateFlow<List<Song>> = musicRepository.getFavoriteSongs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

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

    private val _startupPhase = MutableStateFlow(0) // 0: Idle, 1: UI Ready, 2: Secondary Loading, 3: Settled
    val startupPhase = _startupPhase.asStateFlow()

    init {
        // Data loading is triggered by UI after permission gate passes
    }

    private var isPrimaryLoaded = false
    private var isSecondaryLoaded = false

    fun loadData() {
        if (isPrimaryLoaded || !hasPermission()) return
        
        viewModelScope.launch {
            android.util.Log.d("StartupTimeline", "[PHASE 1] Initializing Primary Data (Songs/Favorites)")
            _isLoading.value = true
            isPrimaryLoaded = true
            _startupPhase.value = 1
            
            // Phase 1: High-Priority UI lists only
            supervisorScope {
                val recentlyAddedDeferred = async { musicRepository.getRecentlyAddedSongs() }
                _recentlyAdded.value = recentlyAddedDeferred.await()
                _isLoading.value = false
                android.util.Log.d("StartupTimeline", "[PHASE 1] Primary UI Content Ready")
            }
        }
    }

    /**
     * Triggered by the UI once the first composition is stable and rendered.
     */
    fun onUiSettled() {
        if (isSecondaryLoaded || !isPrimaryLoaded) return
        
        viewModelScope.launch {
            android.util.Log.d("StartupTimeline", "[PHASE 2] UI Settled. Starting Secondary Indexing.")
            isSecondaryLoaded = true
            _startupPhase.value = 2
            
            supervisorScope {
                // Phase 2: Heavier collections (Albums/Artists)
                val albumsDeferred = async { musicRepository.getAlbums() }
                val artistsDeferred = async { musicRepository.getArtists() }
                val topPlayedDeferred = async { musicRepository.getTopPlayedSongs() }
                val recentlyPlayedDeferred = async { musicRepository.getRecentlyPlayedSongs() }
                
                _albums.value = albumsDeferred.await()
                _artists.value = artistsDeferred.await()
                _mostPlayed.value = topPlayedDeferred.await()
                _recentlyPlayed.value = recentlyPlayedDeferred.await()
                
                android.util.Log.d("StartupTimeline", "[PHASE 2] Secondary Collections Ready")

                // Phase 3: Background heavy work
                _startupPhase.value = 3
                android.util.Log.d("StartupTimeline", "[PHASE 3] Starting Background Work (Moods/Metadata)")
                
                val workoutDeferred = async { generateMoodPlaylistUseCase.getWorkoutPlaylist() }
                val chillDeferred = async { generateMoodPlaylistUseCase.getChillPlaylist() }
                val focusDeferred = async { generateMoodPlaylistUseCase.getFocusPlaylist() }
                val drivingDeferred = async { generateMoodPlaylistUseCase.getDrivingPlaylist() }
                
                _workoutPlaylist.value = workoutDeferred.await()
                _chillPlaylist.value = chillDeferred.await()
                _focusPlaylist.value = focusDeferred.await()
                _drivingPlaylist.value = drivingDeferred.await()
            }
            
            // Final Step: Trigger the worker after a slight delay to ensure UI thread is completely idle
            kotlinx.coroutines.delay(1000)
            enqueueMetadataWork()
            android.util.Log.d("StartupTimeline", "[PHASE 3] Startup Orchestration COMPLETE")
        }
    }

    private fun enqueueMetadataWork() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<com.pralayakaveri.orbitmusic.data.worker.MetadataWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            "metadata_sync",
            androidx.work.ExistingWorkPolicy.KEEP,
            workRequest
        )
    }
}
