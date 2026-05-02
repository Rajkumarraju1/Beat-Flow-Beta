package com.pralayakaveri.orbitmusic.data.engine

import com.pralayakaveri.orbitmusic.data.local.*
import com.pralayakaveri.orbitmusic.domain.engine.LibraryIndexModule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchIndexModuleImpl @Inject constructor(
    private val searchIndexDao: SearchIndexDao,
    private val librarySongDao: LibrarySongDao,
    private val favoritesDao: FavoritesDao,
    private val playCountDao: PlayCountDao,
    private val playlistDao: PlaylistDao
) : LibraryIndexModule {
    override val id: String = "SearchIndexModule"

    private val moduleScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _trigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        moduleScope.launch {
            // DEBOUNCED INDEXING: Collect bursts and rebuild after 1s of silence.
            _trigger.debounce(1000L).collect {
                updateIndex()
            }
        }
    }

    override suspend fun onReconciliationComplete(allSongs: List<com.pralayakaveri.orbitmusic.domain.model.Song>) {
        _trigger.tryEmit(Unit)
    }

    private suspend fun updateIndex() = withContext(Dispatchers.IO) {
        android.util.Log.d("SearchIndexModule", "Starting unified index update (FTS5)...")
        
        try {
            val songs = librarySongDao.getAllSongsSingle()
            val playlists = playlistDao.getAllPlaylistsSingle()
            val favorites = favoritesDao.getAllFavoritesSingle().map { it.id }.toSet()
            val playCounts = playCountDao.getAllPlayCounts().associateBy { it.songId }
            
            val searchEntries = mutableListOf<SearchIndexEntity>()
            
            // 1. Index Songs
            songs.forEach { song ->
                searchEntries.add(SearchIndexEntity(
                    id = 0,
                    title = song.title,
                    subtitle = song.artist,
                    type = "SONG",
                    targetId = song.id.toString(),
                    targetIdString = "",
                    playCount = (playCounts[song.id]?.playCount ?: 0).toString(),
                    isFavorite = if (song.id in favorites) "1" else "0",
                    metadataBlob = "${song.title} ${song.artist} ${song.album}"
                ))
            }
            
            // 2. Index Artists
            val artists = songs.groupBy { it.artist }
            artists.forEach { (artistName, artistSongs) ->
                val artistId = artistSongs.first().artistId
                searchEntries.add(SearchIndexEntity(
                    id = 0,
                    title = artistName,
                    subtitle = "${artistSongs.size} songs",
                    type = "ARTIST",
                    targetId = artistId.toString(),
                    targetIdString = "",
                    playCount = "0",
                    isFavorite = "0",
                    metadataBlob = artistName
                ))
            }
            
            // 3. Index Albums
            val albums = songs.groupBy { it.albumId }
            albums.forEach { (albumId, albumSongs) ->
                val first = albumSongs.first()
                searchEntries.add(SearchIndexEntity(
                    id = 0,
                    title = first.album,
                    subtitle = first.artist,
                    type = "ALBUM",
                    targetId = albumId.toString(),
                    targetIdString = "",
                    playCount = "0",
                    isFavorite = "0",
                    metadataBlob = "${first.album} ${first.artist}"
                ))
            }

            // 4. Index Playlists
            playlists.forEach { playlist ->
                searchEntries.add(SearchIndexEntity(
                    id = 0,
                    title = playlist.name,
                    subtitle = "Playlist",
                    type = "PLAYLIST",
                    targetId = playlist.id.toString(),
                    targetIdString = "",
                    playCount = "0",
                    isFavorite = "0",
                    metadataBlob = playlist.name
                ))
            }
            
            // Atomic Re-indexing
            searchIndexDao.clearIndex()
            searchIndexDao.insertOrUpdateBulk(searchEntries)
            
            android.util.Log.i("SearchIndexModule", "Search index updated: ${searchEntries.size} entities across 4 types.")
        } catch (e: Exception) {
            android.util.Log.e("SearchIndexModule", "Search indexing failed", e)
        }
    }
}
