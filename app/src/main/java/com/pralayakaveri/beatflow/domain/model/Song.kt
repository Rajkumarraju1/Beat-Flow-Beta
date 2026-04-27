package com.pralayakaveri.beatflow.domain.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val dataPath: String,
    val trackNumber: Int,
    val genre: String?,
    val uri: Uri,
    val albumArtUri: Uri?,
    val isUnavailable: Boolean = false
)
