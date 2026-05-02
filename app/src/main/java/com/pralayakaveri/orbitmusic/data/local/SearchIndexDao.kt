package com.pralayakaveri.orbitmusic.data.local

import androidx.room.*

@Dao
interface SearchIndexDao {
    /**
     * Performs a global search using FTS5 and BM25 ranking.
     * Higher playCounts and Favorite status boost the result's relevance.
     */
    @Query("""
        SELECT *
        FROM search_index 
        WHERE metadataBlob LIKE '%' || :query || '%'
        ORDER BY title ASC
        LIMIT :limit
    """)
    suspend fun search(query: String, limit: Int = 50): List<SearchIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBulk(entries: List<SearchIndexEntity>)

    @Query("DELETE FROM search_index WHERE targetId = :id AND type = :type")
    suspend fun deleteEntry(id: Long, type: String)

    @Query("DELETE FROM search_index")
    suspend fun clearIndex()
}
