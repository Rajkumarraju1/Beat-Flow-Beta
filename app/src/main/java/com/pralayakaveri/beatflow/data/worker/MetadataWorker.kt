package com.pralayakaveri.beatflow.data.worker

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pralayakaveri.beatflow.data.local.SongMetadataDao
import com.pralayakaveri.beatflow.data.local.SongMetadataEntity
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
    private val metadataDao: SongMetadataDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val allSongs = musicRepository.getSongs()
            val scannedIds = metadataDao.getAllScannedIds().toSet()
            
            val songsToScan = allSongs.filter { it.id !in scannedIds }
            
            if (songsToScan.isEmpty()) return@withContext Result.success()

            val retriever = MediaMetadataRetriever()
            
            songsToScan.forEach { song ->
                try {
                    val file = File(song.dataPath)
                    if (file.exists()) {
                        retriever.setDataSource(song.dataPath)
                        val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                        
                        val metadata = SongMetadataEntity(
                            songId = song.id,
                            genre = genre,
                            bpm = null, // Future enhancement
                            mood = null,
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
            Result.retry()
        }
    }
}
