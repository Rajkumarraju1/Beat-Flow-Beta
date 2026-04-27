package com.pralayakaveri.beatflow.data.worker

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pralayakaveri.beatflow.data.local.SongMetadataDao
import com.pralayakaveri.beatflow.data.local.SongMetadataEntity
import com.pralayakaveri.beatflow.domain.engine.LibraryIndexingEngine
import com.pralayakaveri.beatflow.domain.repository.MusicRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class MetadataWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val musicRepository: MusicRepository,
    private val metadataDao: SongMetadataDao,
    private val indexingEngine: LibraryIndexingEngine
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // 1. Ensure Room Index is reconciled with MediaStore first
            indexingEngine.startSync()

            // 2. Identify songs that need deep metadata extraction (Genre, BPM, Mood)
            val allSongs = musicRepository.getSongs()
            val allMetadata = metadataDao.getAllMetadata().associateBy { it.songId }
            
            // Scan if no metadata exists OR if never successfully scanned for deep tags
            val songsToScan = allSongs.filter { song ->
                val meta = allMetadata[song.id]
                meta == null || meta.genre == null 
            }
            
            if (songsToScan.isEmpty()) return@withContext Result.success()

            val retriever = MediaMetadataRetriever()
            
            songsToScan.forEach { song ->
                try {
                    val file = File(song.dataPath)
                    if (file.exists()) {
                        retriever.setDataSource(song.dataPath)
                        val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                        
                        val existing = allMetadata[song.id]
                        
                        val metadata = (existing ?: SongMetadataEntity(
                            songId = song.id,
                            genre = genre,
                            bpm = null,
                            mood = null,
                            lastScanned = System.currentTimeMillis()
                        )).copy(
                            genre = genre, 
                            lastScanned = System.currentTimeMillis()
                        )
                        
                        metadataDao.insertOrUpdate(metadata)
                    }
                } catch (e: Exception) {
                    // Skip individual file errors
                }
            }
            
            retriever.release()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("MetadataWorker", "Metadata scan failed", e)
            Result.retry()
        }
    }
}
