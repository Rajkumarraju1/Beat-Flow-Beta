package com.pralayakaveri.beatflow.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_songs")
data class LibrarySongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val dataPath: String,
    val trackNumber: Int,
    val uriString: String,
    val albumArtUriString: String?
)

fun LibrarySongWithIndex.toDomain(): com.pralayakaveri.beatflow.domain.model.Song {
    return com.pralayakaveri.beatflow.domain.model.Song(
        id = song.id,
        title = song.title,
        artist = song.artist,
        artistId = song.artistId,
        album = song.album,
        albumId = song.albumId,
        duration = song.duration,
        dataPath = song.dataPath,
        trackNumber = song.trackNumber,
        genre = null,
        uri = android.net.Uri.parse(song.uriString),
        albumArtUri = song.albumArtUriString?.let { android.net.Uri.parse(it) },
        isUnavailable = index?.isOrphan ?: false
    )
}
