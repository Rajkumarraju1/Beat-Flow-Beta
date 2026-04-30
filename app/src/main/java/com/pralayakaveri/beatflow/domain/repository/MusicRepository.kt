package com.pralayakaveri.beatflow.domain.repository

import com.pralayakaveri.beatflow.domain.model.Album
import com.pralayakaveri.beatflow.domain.model.Artist
import com.pralayakaveri.beatflow.domain.model.Song
import kotlinx.coroutines.flow.Flow

import kotlinx.coroutines.flow.StateFlow

interface MusicRepository {
    suspend fun getSongs(): List<Song>
    suspend fun getAlbums(): List<Album>
    suspend fun getArtists(): List<Artist>
    
    fun getFavoriteSongs(): Flow<List<Song>>
    fun getFavoriteIds(): Flow<Set<Long>>
    suspend fun toggleFavorite(songId: Long)
    fun isFavorite(songId: Long): Flow<Boolean>

    suspend fun incrementPlayCount(songId: Long)
    suspend fun getTopPlayedSongs(limit: Int = 20): List<Song>
    suspend fun getRecentlyPlayedSongs(limit: Int = 20): List<Song>
    suspend fun getRecentlyAddedSongs(limit: Int = 20): List<Song>
    
    fun getAllSongs(): Flow<List<Song>>
    suspend fun getForgottenSongs(limit: Int = 20): List<Song>
    suspend fun getHiddenGems(limit: Int = 20): List<Song>

    suspend fun getAllSongMetadata(): List<com.pralayakaveri.beatflow.data.local.SongMetadataEntity>

    fun getAllCustomArtworks(): Flow<Map<Long, String>>
    suspend fun saveCustomArtwork(songId: Long, uri: String)
    suspend fun removeCustomArtwork(songId: Long)

    fun getArtistImages(): Flow<Map<String, String>>
    suspend fun saveArtistImage(artistName: String, uri: String)
    suspend fun removeArtistImage(artistName: String)

    fun getPlaylists(): Flow<List<com.pralayakaveri.beatflow.domain.model.Playlist>>
    suspend fun createPlaylist(name: String): Long
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun addSongToPlaylist(playlistId: Long, songId: Long)
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)
    fun invalidateSongCache()
    suspend fun startSync(reason: com.pralayakaveri.beatflow.domain.engine.TriggerReason)

    fun getSortOrder(): Flow<com.pralayakaveri.beatflow.domain.model.SortOrder>
    suspend fun setSortOrder(sortOrder: com.pralayakaveri.beatflow.domain.model.SortOrder)

    fun getFilterPreferences(): Flow<com.pralayakaveri.beatflow.domain.util.FilterPreferences>
    suspend fun updateMinDurationEnabled(enabled: Boolean)
    suspend fun updateMinSizeEnabled(enabled: Boolean)
    suspend fun forceRescan()
    
    suspend fun saveLyrics(songId: Long, lyrics: String)
    suspend fun deleteLyrics(songId: Long)

    fun getUseReducedMotion(): Flow<Boolean>
    suspend fun setUseReducedMotion(enabled: Boolean)

    fun getIndexingState(): StateFlow<com.pralayakaveri.beatflow.domain.engine.IndexingState>
}
