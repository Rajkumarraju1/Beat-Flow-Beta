package com.pralayakaveri.beatflow.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Enrichment Metadata Table.
 * Stores deep-scanned or user-provided mood, genre, and BPM data.
 */
@Entity(tableName = "song_metadata")
data class SongMetadataEntity(
    @PrimaryKey val songId: Long,
    val genre: String?,
    val bpm: Int?,
    val mood: String?,
    val lastScanned: Long
)
