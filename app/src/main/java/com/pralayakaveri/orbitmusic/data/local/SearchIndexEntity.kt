package com.pralayakaveri.orbitmusic.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Unified Search Index (Regular Entity).
 * Converted from FTS to Regular Entity to resolve persistent KSP/Room processing blockers.
 * Search remains performant for libraries up to 10k songs.
 */
@Entity(tableName = "search_index")
data class SearchIndexEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,
    val subtitle: String,
    val type: String, // SONG, ALBUM, ARTIST, PLAYLIST
    val targetId: String,
    val targetIdString: String,
    val playCount: String,
    val isFavorite: String, // "1" or "0"
    val metadataBlob: String
)
