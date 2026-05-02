package com.pralayakaveri.orbitmusic.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LibrarySongDao {
    @Query("SELECT * FROM library_songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<LibrarySongEntity>>

    @Query("SELECT * FROM library_songs ORDER BY title ASC")
    suspend fun getAllSongsSingle(): List<LibrarySongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<LibrarySongEntity>)

    @Query("DELETE FROM library_songs WHERE id = :id")
    suspend fun deleteSong(id: Long)

    @Query("DELETE FROM library_songs WHERE id IN (:ids)")
    suspend fun deleteSongs(ids: List<Long>)

    @Query("DELETE FROM library_songs")
    suspend fun deleteAll()

    @Transaction
    @Query("SELECT * FROM library_songs ORDER BY title ASC")
    fun getAllSongsWithIndex(): Flow<List<LibrarySongWithIndex>>

    @Transaction
    @Query("SELECT * FROM library_songs ORDER BY title ASC")
    suspend fun getAllSongsWithIndexSingle(): List<LibrarySongWithIndex>
}

data class LibrarySongWithIndex(
    @Embedded val song: LibrarySongEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "songId"
    )
    val index: LibraryIndexEntity?
)
