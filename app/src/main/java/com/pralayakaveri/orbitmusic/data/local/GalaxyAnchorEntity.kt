package com.pralayakaveri.orbitmusic.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Galaxy Anchors.
 * Stores fixed positions for Artist Solar Systems to ensure topological stability.
 * Even if songs are added/removed, the Artist's "Sun" remains at these coordinates.
 */
@Entity(tableName = "galaxy_anchors")
data class GalaxyAnchorEntity(
    @PrimaryKey val artistId: Long,
    val x: Float,
    val y: Float,
    val clusterId: String
)
