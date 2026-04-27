package com.pralayakaveri.beatflow.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The Canonical Lifecycle Table.
 * Separates indexing and identity state from both MediaStore mirrors and Enrichment metadata.
 */
@Entity(tableName = "library_index")
data class LibraryIndexEntity(
    @PrimaryKey val songId: Long,
    val isOrphan: Boolean = false,
    val recoveryHash: String? = null, // Primary: Path + Size
    val secondaryHash: String? = null, // Secondary: Filename + Duration
    val firstSeenMissingAt: Long? = null,
    val lastSyncedAt: Long = System.currentTimeMillis()
)
