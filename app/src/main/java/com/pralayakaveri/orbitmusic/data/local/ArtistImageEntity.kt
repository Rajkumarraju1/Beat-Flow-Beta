package com.pralayakaveri.orbitmusic.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artist_images")
data class ArtistImageEntity(
    @PrimaryKey val artistName: String,
    val customUri: String
)
