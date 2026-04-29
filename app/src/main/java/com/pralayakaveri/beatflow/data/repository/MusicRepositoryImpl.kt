package com.pralayakaveri.beatflow.data.repository

import android.net.Uri
import com.pralayakaveri.beatflow.data.local.*
import com.pralayakaveri.beatflow.data.provider.MediaStoreProvider
import com.pralayakaveri.beatflow.domain.engine.IndexingMode
import com.pralayakaveri.beatflow.domain.engine.LibraryIndexingEngine
import com.pralayakaveri.beatflow.domain.engine.TriggerReason
import com.pralayakaveri.beatflow.domain.model.Album
import com.pralayakaveri.beatflow.domain.model.Artist
import com.pralayakaveri.beatflow.domain.model.Song
import com.pralayakaveri.beatflow.domain.model.SortOrder
import com.pralayakaveri.beatflow.domain.repository.MusicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val mediaStoreProvider: MediaStoreProvider,
    private val favoritesDao: FavoritesDao,
    private val playCountDao: PlayCountDao,
    private val metadataDao: SongMetadataDao,
    private val customArtworkDao: CustomArtworkDao,
    private val playlistDao: PlaylistDao,
    private val artistImageDao: ArtistImageDao,
    private val librarySongDao: LibrarySongDao,
    private val indexingEngine: LibraryIndexingEngine,
    private val libraryPreferencesManager: LibraryPreferencesManager,
    private val filterPreferencesManager: com.pralayakaveri.beatflow.domain.util.FilterPreferencesManager
) : MusicRepository {

    private var cachedSongs: List<Song> = emptyList()
    private var cachedAlbums: List<Album> = emptyList()
    private var cachedArtists: List<Artist> = emptyList()

    override suspend fun getSongs(): List<Song> = withContext(Dispatchers.IO) {
        if (cachedSongs.isNotEmpty()) return@withContext cachedSongs
        
        val songs = when (indexingEngine.mode) {
            IndexingMode.LEGACY, IndexingMode.SHADOW -> {
                mediaStoreProvider.getAllSongs()
            }
            IndexingMode.ACTIVE -> {
                val songsWithIndex = librarySongDao.getAllSongsWithIndexSingle()
                if (songsWithIndex.isEmpty()) {
                    // Fallback to MediaStore if DB is empty, but trigger sync in background
                    indexingEngine.startSync(TriggerReason.INITIAL_SCAN)
                    mediaStoreProvider.getAllSongs()
                } else {
                    songsWithIndex.map { it.toDomain() }
                }
            }
        }
        cachedSongs = songs
        songs
    }

    override suspend fun getAlbums(): List<Album> = withContext(Dispatchers.IO) {
        if (cachedAlbums.isNotEmpty()) return@withContext cachedAlbums
        val songs = getSongs()
        val albums = songs.groupBy { it.albumId }.map { (albumId, albumSongs) ->
            val firstSong = albumSongs.first()
            Album(
                id = albumId,
                title = firstSong.album,
                artist = firstSong.artist,
                artistId = firstSong.artistId,
                songCount = albumSongs.size,
                year = 0,
                albumArtUri = firstSong.albumArtUri
            )
        }.sortedBy { it.title }
        cachedAlbums = albums
        albums
    }

    override suspend fun getArtists(): List<Artist> = withContext(Dispatchers.IO) {
        if (cachedArtists.isNotEmpty()) return@withContext cachedArtists
        val songs = getSongs()
        val artists = songs.groupBy { it.artistId }.map { (artistId, artistSongs) ->
            val firstSong = artistSongs.first()
            Artist(
                id = artistId,
                name = firstSong.artist,
                songCount = artistSongs.size,
                albumCount = artistSongs.distinctBy { it.albumId }.size
            )
        }.sortedBy { it.name }
        cachedArtists = artists
        artists
    }

    override fun getFavoriteSongs(): Flow<List<Song>> {
        return combine(
            favoritesDao.getAllFavorites(),
            getAllSongs()
        ) { favoriteEntities, allSongs ->
            val songsMap = allSongs.associateBy { it.id }
            favoriteEntities.mapNotNull { entity ->
                songsMap[entity.id]
            }
        }
    }

    override fun getFavoriteIds(): Flow<Set<Long>> {
        return favoritesDao.getAllFavoriteIds().map { it.toSet() }
    }

    override suspend fun toggleFavorite(songId: Long) {
        val isFav = favoritesDao.isFavoriteSingle(songId)
        if (isFav) {
            favoritesDao.removeFavorite(FavoriteSongEntity(songId, System.currentTimeMillis()))
        } else {
            favoritesDao.addFavorite(FavoriteSongEntity(songId, System.currentTimeMillis()))
        }
    }

    override fun isFavorite(songId: Long): Flow<Boolean> {
        return favoritesDao.isFavorite(songId)
    }

    override suspend fun incrementPlayCount(songId: Long) {
        val existing = playCountDao.getPlayCountById(songId)
        val newCount = (existing?.playCount ?: 0) + 1
        playCountDao.insertOrUpdate(
            PlayCountEntity(
                songId = songId,
                playCount = newCount,
                lastPlayed = System.currentTimeMillis()
            )
        )
    }

    override suspend fun getTopPlayedSongs(limit: Int): List<Song> {
        val topEntities = playCountDao.getTopPlayedSongs(limit)
        val allSongs = getSongs()
        val songsMap = allSongs.associateBy { it.id }
        return topEntities.mapNotNull { entity ->
            songsMap[entity.songId]
        }
    }

    override suspend fun getRecentlyPlayedSongs(limit: Int): List<Song> {
        val recentEntities = playCountDao.getRecentlyPlayedSongs(limit)
        val allSongs = getSongs()
        val songsMap = allSongs.associateBy { it.id }
        return recentEntities.mapNotNull { entity ->
            songsMap[entity.songId]
        }
    }

    override suspend fun getRecentlyAddedSongs(limit: Int): List<Song> {
        return mediaStoreProvider.getRecentlyAddedSongs(limit)
    }

    override fun getAllSongs(): Flow<List<Song>> {
        val songsFlow = when (indexingEngine.mode) {
            IndexingMode.LEGACY, IndexingMode.SHADOW -> mediaStoreProvider.getAllSongsFlow()
            IndexingMode.ACTIVE -> librarySongDao.getAllSongs().map { entities ->
                entities.map { it.toDomain() }
            }
        }

        return combine(
            songsFlow,
            libraryPreferencesManager.sortOrderFlow,
            playCountDao.getAllPlayCountsFlow()
        ) { songs, sortOrder, playCounts ->
            when (sortOrder) {
                SortOrder.TITLE -> songs.sortedBy { it.title.lowercase() }
                SortOrder.ARTIST -> songs.sortedBy { it.artist.lowercase() }
                SortOrder.RECENTLY_ADDED -> songs.sortedByDescending { it.id }
                SortOrder.MOST_PLAYED -> {
                    val countMap = playCounts.associate { it.songId to it.playCount }
                    songs.sortedByDescending { countMap[it.id] ?: 0 }
                }
            }
        }
    }

    override suspend fun getForgottenSongs(limit: Int): List<Song> {
        val allSongs = getSongs()
        val playCounts = playCountDao.getAllPlayCounts().associateBy { it.songId }
        
        return allSongs.filter { song ->
            val pc = playCounts[song.id]?.playCount ?: 0
            pc <= 1
        }.take(limit)
    }

    override suspend fun getHiddenGems(limit: Int): List<Song> {
        val favorites = getFavoriteSongs().first()
        val playCounts = playCountDao.getAllPlayCounts().associateBy { it.songId }

        return favorites.filter { song ->
            val pc = playCounts[song.id]?.playCount ?: 0
            pc < 5
        }.take(limit)
    }

    override suspend fun getAllSongMetadata(): List<SongMetadataEntity> {
        return metadataDao.getAllMetadata()
    }

    override fun getAllCustomArtworks(): Flow<Map<Long, String>> {
        return customArtworkDao.getAllCustomArtworks().map { list ->
            list.associate { it.songId to it.customUri }
        }
    }

    override suspend fun saveCustomArtwork(songId: Long, uri: String) {
        customArtworkDao.insertCustomArtwork(CustomArtworkEntity(songId, uri))
    }

    override suspend fun removeCustomArtwork(songId: Long) {
        customArtworkDao.removeCustomArtwork(songId)
    }

    override fun getPlaylists(): Flow<List<com.pralayakaveri.beatflow.domain.model.Playlist>> {
        return combine(
            playlistDao.getAllPlaylists(),
            playlistDao.getAllPlaylistSongs(),
            getAllSongs()
        ) { playlistEntities, crossRefs, allSongs ->
            val songMap = allSongs.associateBy { it.id }
            val crossRefsByPlaylistId = crossRefs.groupBy { it.playlistId }

            playlistEntities.map { entity ->
                val refsForThisPlaylist = crossRefsByPlaylistId[entity.id] ?: emptyList()
                val sortedRefs = refsForThisPlaylist.sortedBy { it.addedAt }
                val songsInPlaylist = sortedRefs.mapNotNull { ref -> songMap[ref.songId] }
                
                com.pralayakaveri.beatflow.domain.model.Playlist(
                    id = entity.id,
                    name = entity.name,
                    songs = songsInPlaylist
                )
            }
        }
    }

    override suspend fun createPlaylist(name: String): Long {
        return playlistDao.insertPlaylist(PlaylistEntity(name = name))
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    override suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        playlistDao.insertSongToPlaylist(PlaylistSongCrossRef(playlistId, songId))
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    override fun getArtistImages(): Flow<Map<String, String>> {
        return artistImageDao.getAllArtistImages().map { list ->
            list.associate { it.artistName to it.customUri }
        }
    }

    override suspend fun saveArtistImage(artistName: String, uri: String) {
        artistImageDao.insertArtistImage(ArtistImageEntity(artistName, uri))
    }

    override suspend fun removeArtistImage(artistName: String) {
        artistImageDao.deleteArtistImage(artistName)
    }

    override fun invalidateSongCache() {
        cachedSongs = emptyList()
        cachedAlbums = emptyList()
        cachedArtists = emptyList()
    }

    override suspend fun startSync(reason: TriggerReason) {
        indexingEngine.startSync(TriggerReason.INITIAL_SCAN)
    }

    override fun getSortOrder(): Flow<SortOrder> = libraryPreferencesManager.sortOrderFlow

    override suspend fun setSortOrder(sortOrder: SortOrder) {
        libraryPreferencesManager.setSortOrder(sortOrder)
    }

    override fun getFilterPreferences(): Flow<com.pralayakaveri.beatflow.domain.util.FilterPreferences> = 
        filterPreferencesManager.filterFlow

    override suspend fun updateMinDurationEnabled(enabled: Boolean) {
        filterPreferencesManager.updateMinDurationEnabled(enabled)
    }

    override suspend fun updateMinSizeEnabled(enabled: Boolean) {
        filterPreferencesManager.updateMinSizeEnabled(enabled)
    }

    override suspend fun forceRescan() {
        invalidateSongCache()
        librarySongDao.deleteAll()
        startSync(TriggerReason.MANUAL_TRIGGER)
    }

    override suspend fun saveLyrics(songId: Long, lyrics: String) {
        metadataDao.updateLyrics(songId, lyrics)
    }

    override suspend fun deleteLyrics(songId: Long) {
        metadataDao.updateLyrics(songId, null)
    }

    override fun getUseReducedMotion(): Flow<Boolean> = libraryPreferencesManager.useReducedMotion

    override suspend fun setUseReducedMotion(enabled: Boolean) {
        libraryPreferencesManager.setUseReducedMotion(enabled)
    }

    private fun LibrarySongEntity.toDomain(): Song {
        return Song(
            id = id,
            title = title,
            artist = artist,
            artistId = artistId,
            album = album,
            albumId = albumId,
            duration = duration,
            dataPath = dataPath,
            trackNumber = trackNumber,
            genre = null,
            uri = Uri.parse(uriString),
            albumArtUri = albumArtUriString?.let { Uri.parse(it) }
        )
    }
}
