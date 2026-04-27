package com.pralayakaveri.beatflow.domain.usecase

import android.media.MediaMetadataRetriever
import com.pralayakaveri.beatflow.data.local.PlayCountDao
import com.pralayakaveri.beatflow.domain.model.Song
import com.pralayakaveri.beatflow.domain.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class GenerateMoodPlaylistUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playCountDao: PlayCountDao
) {
    suspend fun getWorkoutPlaylist(): List<Song> = withContext(Dispatchers.IO) {
        val allSongs = musicRepository.getSongs()
        val playCounts = playCountDao.getTopPlayedSongs(200).associateBy { it.songId }
        val metadataMap = musicRepository.getAllSongMetadata().associateBy { it.songId }
        
        allSongs.filter { song ->
            val genre = metadataMap[song.id]?.genre?.lowercase() ?: ""
            val isWorkoutGenre = genre.contains("rock") || genre.contains("electronic") || 
                                 genre.contains("dance") || genre.contains("pop") || genre.contains("metal")
            val isShortOrMedium = song.duration in 120_000..360_000 // 2 to 6 mins
            val isPlayedOften = (playCounts[song.id]?.playCount ?: 0) > 0
            
            isWorkoutGenre && isShortOrMedium && isPlayedOften
        }.shuffled().take(20)
    }

    suspend fun getChillPlaylist(): List<Song> = withContext(Dispatchers.IO) {
        val allSongs = musicRepository.getSongs()
        val metadataMap = musicRepository.getAllSongMetadata().associateBy { it.songId }
        
        allSongs.filter { song ->
            val genre = metadataMap[song.id]?.genre?.lowercase() ?: ""
            val title = song.title.lowercase()
            val isChillGenre = genre.contains("jazz") || genre.contains("acoustic") || 
                               genre.contains("lofi") || genre.contains("r&b") || genre.contains("chill")
            val isChillTitle = title.contains("lofi") || title.contains("chill") || title.contains("acoustic")
            
            isChillGenre || isChillTitle
        }.shuffled().take(20)
    }

    suspend fun getFocusPlaylist(): List<Song> = withContext(Dispatchers.IO) {
        val allSongs = musicRepository.getSongs()
        val metadataMap = musicRepository.getAllSongMetadata().associateBy { it.songId }
        
        allSongs.filter { song ->
            val genre = metadataMap[song.id]?.genre?.lowercase() ?: ""
            val title = song.title.lowercase()
            val isFocusGenre = genre.contains("classical") || genre.contains("instrumental") || 
                               genre.contains("ambient") || genre.contains("soundtrack")
            val isFocusTitle = title.contains("study") || title.contains("focus") || title.contains("instrumental")
            
            val isLongEnough = song.duration > 180_000 // > 3 mins
            
            (isFocusGenre || isFocusTitle) && isLongEnough
        }.shuffled().take(20)
    }

    suspend fun getDrivingPlaylist(): List<Song> = withContext(Dispatchers.IO) {
        val allSongs = musicRepository.getSongs()
        val playCounts = playCountDao.getTopPlayedSongs(100).associateBy { it.songId }
        val metadataMap = musicRepository.getAllSongMetadata().associateBy { it.songId }
        
        allSongs.filter { song ->
            val genre = metadataMap[song.id]?.genre?.lowercase() ?: ""
            val isDrivingGenre = genre.contains("rock") || genre.contains("pop") || 
                                 genre.contains("hiphop") || genre.contains("rap") || genre.contains("country")
                                 
            val isPlayedOften = (playCounts[song.id]?.playCount ?: 0) >= 1
            
            isDrivingGenre && isPlayedOften
        }.sortedByDescending { playCounts[it.id]?.playCount ?: 0 }.take(20)
    }
}
