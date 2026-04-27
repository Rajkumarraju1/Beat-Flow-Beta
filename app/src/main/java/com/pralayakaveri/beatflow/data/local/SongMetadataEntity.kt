package com.pralayakaveri.beatflow.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "song_metadata")
data class SongMetadataEntity(
    @PrimaryKey val songId: Long,
    val genre: String?,
    val bpm: Int?,
    val mood: String?,
    val lastScanned: Long
)
