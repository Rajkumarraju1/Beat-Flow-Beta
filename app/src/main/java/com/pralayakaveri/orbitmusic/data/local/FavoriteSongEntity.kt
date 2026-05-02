package com.pralayakaveri.orbitmusic.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteSongEntity(
    @PrimaryKey val id: Long,
    val dateAdded: Long
)
