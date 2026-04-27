package com.pralayakaveri.beatflow.data.repository

import android.net.Uri
import com.pralayakaveri.beatflow.data.local.*
import com.pralayakaveri.beatflow.data.provider.MediaStoreProvider
import com.pralayakaveri.beatflow.domain.engine.IndexingMode
import com.pralayakaveri.beatflow.domain.engine.LibraryIndexingEngine
import com.pralayakaveri.beatflow.domain.model.Album
import com.pralayakaveri.beatflow.domain.model.Artist
import com.pralayakaveri.beatflow.domain.model.Song
import com.pralayakaveri.beatflow.domain.repository.MusicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
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
    private val indexingEngine: LibraryIndexingEngine
) : MusicRepository {

    private var cachedSongs: List<Song> = emptyList()

    override suspend fun getSongs(): List<Song> {
        return when (indexingEngine.mode) {
            IndexingMode.LEGACY, IndexingMode.SHADOW -> {
                if (cachedSongs.isEmpty()) {
                    cachedSongs = mediaStoreProvider.getAllSongs()
                }
                cachedSongs
            }
            IndexingMode.ACTIVE -> {
                val songsWithIndex = librarySongDao.getAllSongsWithIndexSingle()
                if (songsWithIndex.isEmpty()) {
                    indexingEngine.startSync()
                    mediaStoreProvider.getAllSongs()
                } else {
                    songsWithIndex.map { it.toDomain() }
                }
            }
        }
    }

    override suspend fun getAlbums(): List<Album> {
        val songs = getSongs()
        return songs.groupBy { it.albumId }.map { (albumId, albumSongs) ->
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
    }

    override suspend fun getArtists(): List<Artist> {
        val songs = getSongs()
        return songs.groupBy { it.artistId }.map { (artistId, artistSongs) ->
            val firstSong = artistSongs.first()
            Artist(
                id = artistId,
                name = firstSong.artist,
                songCount = artistSongs.size,
                albumCount = artistSongs.distinctBy { it.albumId }.size
            )
        }.sortedBy { it.name }
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
        return when (indexingEngine.mode) {
            IndexingMode.LEGACY, IndexingMode.SHADOW -> mediaStoreProvider.getAllSongsFlow()
            IndexingMode.ACTIVE -> librarySongDao.getAllSongs().map { entities ->
                entities.map { it.toDomain() }
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
    }

    override suspend fun startSync(reason: com.pralayakaveri.beatflow.domain.engine.TriggerReason) {
        indexingEngine.startSync(reason)
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
