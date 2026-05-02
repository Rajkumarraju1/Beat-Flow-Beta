package com.pralayakaveri.orbitmusic.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Galaxy Spatial Index.
 * Stores precomputed coordinates and cluster membership for the immersive 3D view.
 * Decouples spatial logic from both core reconciliation and UI rendering.
 */
@Entity(tableName = "galaxy_index")
data class GalaxyIndexEntity(
    @PrimaryKey val songId: Long,
    val artistId: Long,
    val x: Float,
    val y: Float,
    val z: Float = 0f, // Reserved for future depth parallax
    val clusterId: String, // Genre or Mood cluster
    val radius: Float, // Size/Importance of the star
    val lastUpdated: Long = System.currentTimeMillis()
)
