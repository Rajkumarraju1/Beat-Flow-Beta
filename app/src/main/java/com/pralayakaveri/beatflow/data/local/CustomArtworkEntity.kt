package com.pralayakaveri.beatflow.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_artwork")
data class CustomArtworkEntity(
    @PrimaryKey val songId: Long,
    val customUri: String
)
