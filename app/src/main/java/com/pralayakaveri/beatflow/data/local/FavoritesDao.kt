package com.pralayakaveri.beatflow.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {
    @Query("SELECT * FROM favorites ORDER BY dateAdded DESC")
    fun getAllFavorites(): Flow<List<FavoriteSongEntity>>

    @Query("SELECT * FROM favorites")
    suspend fun getAllFavoritesSingle(): List<FavoriteSongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteSongEntity)

    @Delete
    suspend fun removeFavorite(favorite: FavoriteSongEntity)
    
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :songId)")
    fun isFavorite(songId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :songId)")
    suspend fun isFavoriteSingle(songId: Long): Boolean
}
