package com.pralayakaveri.beatflow.data.local

import androidx.room.*

@Dao
interface GalaxyIndexDao {
    @Query("SELECT * FROM galaxy_index")
    suspend fun getAllNodes(): List<GalaxyIndexEntity>

    @Query("""
        SELECT * FROM galaxy_index 
        WHERE x BETWEEN :minX AND :maxX 
        AND y BETWEEN :minY AND :maxY
    """)
    suspend fun getNodesInViewport(minX: Float, maxX: Float, minY: Float, maxY: Float): List<GalaxyIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBulk(nodes: List<GalaxyIndexEntity>)

    @Query("DELETE FROM galaxy_index WHERE songId = :songId")
    suspend fun deleteNode(songId: Long)

    @Query("DELETE FROM galaxy_index")
    suspend fun clearIndex()
}

@Dao
interface GalaxyAnchorDao {
    @Query("SELECT * FROM galaxy_anchors")
    suspend fun getAllAnchors(): List<GalaxyAnchorEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAnchor(anchor: GalaxyAnchorEntity)

    @Query("DELETE FROM galaxy_anchors")
    suspend fun clearAnchors()
}
