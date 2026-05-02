package com.pralayakaveri.orbitmusic.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomArtworkDao {
    @Query("SELECT * FROM custom_artwork")
    fun getAllCustomArtworks(): Flow<List<CustomArtworkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomArtwork(entity: CustomArtworkEntity)

    @Query("DELETE FROM custom_artwork WHERE songId = :songId")
    suspend fun removeCustomArtwork(songId: Long)
}
