package com.pralayakaveri.beatflow.data.local

import androidx.room.*

@Dao
interface SongMetadataDao {
    @Query("SELECT * FROM song_metadata")
    suspend fun getAllMetadata(): List<SongMetadataEntity>

    @Query("SELECT * FROM song_metadata WHERE songId = :songId")
    suspend fun getMetadataById(songId: Long): SongMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: SongMetadataEntity)

    @Query("SELECT songId FROM song_metadata")
    suspend fun getAllScannedIds(): List<Long>

    @Query("DELETE FROM song_metadata WHERE songId = :songId")
    suspend fun deleteMetadata(songId: Long)
}
