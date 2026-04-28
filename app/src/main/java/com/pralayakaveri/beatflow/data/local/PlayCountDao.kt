package com.pralayakaveri.beatflow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
@Dao
interface PlayCountDao {
    @Query("SELECT * FROM play_counts WHERE songId = :songId")
    suspend fun getPlayCountById(songId: Long): PlayCountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(playCountEntity: PlayCountEntity)

    @Query("SELECT * FROM play_counts ORDER BY playCount DESC LIMIT :limit")
    suspend fun getTopPlayedSongs(limit: Int): List<PlayCountEntity>

    @Query("SELECT * FROM play_counts ORDER BY lastPlayed DESC LIMIT :limit")
    suspend fun getRecentlyPlayedSongs(limit: Int): List<PlayCountEntity>

    @Query("SELECT * FROM play_counts")
    suspend fun getAllPlayCounts(): List<PlayCountEntity>

    @Query("SELECT * FROM play_counts")
    fun getAllPlayCountsFlow(): kotlinx.coroutines.flow.Flow<List<PlayCountEntity>>
}
