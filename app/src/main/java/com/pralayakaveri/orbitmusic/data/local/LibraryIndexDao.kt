package com.pralayakaveri.orbitmusic.data.local

import androidx.room.*

@Dao
interface LibraryIndexDao {
    @Query("SELECT * FROM library_index")
    suspend fun getAllIndexEntries(): List<LibraryIndexEntity>

    @Query("SELECT * FROM library_index WHERE songId = :songId")
    suspend fun getIndexEntry(songId: Long): LibraryIndexEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entry: LibraryIndexEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBulk(entries: List<LibraryIndexEntity>)

    @Query("UPDATE library_index SET isOrphan = :isOrphan, firstSeenMissingAt = :missingAt WHERE songId = :songId")
    suspend fun updateOrphanStatus(songId: Long, isOrphan: Boolean, missingAt: Long?)

    @Query("SELECT * FROM library_index WHERE recoveryHash = :hash LIMIT 1")
    suspend fun findByRecoveryHash(hash: String): LibraryIndexEntity?

    @Query("SELECT * FROM library_index WHERE secondaryHash = :hash LIMIT 1")
    suspend fun findBySecondaryHash(hash: String): LibraryIndexEntity?

    @Query("DELETE FROM library_index WHERE songId = :songId")
    suspend fun deleteEntry(songId: Long)

    @Query("DELETE FROM library_index")
    suspend fun deleteAll()
}
