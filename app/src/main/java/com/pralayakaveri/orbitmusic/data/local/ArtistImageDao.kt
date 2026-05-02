package com.pralayakaveri.orbitmusic.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistImageDao {
    @Query("SELECT * FROM artist_images")
    fun getAllArtistImages(): Flow<List<ArtistImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtistImage(entity: ArtistImageEntity)

    @Query("DELETE FROM artist_images WHERE artistName = :artistName")
    suspend fun deleteArtistImage(artistName: String)
}
