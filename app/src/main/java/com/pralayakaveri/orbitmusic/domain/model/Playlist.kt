package com.pralayakaveri.orbitmusic.domain.model

data class Playlist(
    val id: Long,
    val name: String,
    val songs: List<Song>
)
