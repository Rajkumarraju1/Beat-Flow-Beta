package com.pralayakaveri.beatflow.domain.util

import android.net.Uri
import android.util.Log
import com.pralayakaveri.beatflow.domain.model.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

object LyricsParser {
    
    init {
        // Disable annoying jaudiotagger console spam
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    suspend fun getLyricsForSong(uri: Uri): List<LyricLine> = withContext(Dispatchers.IO) {
        try {
            val file = File(uri.path ?: return@withContext emptyList())
            if (!file.exists()) return@withContext emptyList()

            // 1. Check for a sibling .lrc file first (e.g. song.mp3 -> song.lrc)
            val lrcFile = File(file.parent, file.nameWithoutExtension + ".lrc")
            if (lrcFile.exists()) {
                val lrcLines = LrcParser.parseLrcFile(lrcFile)
                if (lrcLines.isNotEmpty()) return@withContext lrcLines
            }

            // 2. Try falling back to ID3v2 UNSYNCEDLYRICS / SYLT embed via JAudioTagger
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag

            if (tag != null) {
                // Try synced lyrics (SYLT) first if we supported them (advanced)
                // For now we look for generic embedded lyrics (USLT or standard Lyrics field)
                val embeddedLyrics = tag.getFirst(FieldKey.LYRICS)
                
                if (embeddedLyrics.isNotBlank()) {
                    // This parse mechanism checks if the embedded lyrics are LRC formatted
                    val parsed = LrcParser.parseLrcContent(embeddedLyrics)
                    if (parsed.isNotEmpty()) return@withContext parsed
                    
                    // Otherwise it's probably just plain text (unsynced) without timestamps
                    // We can map it loosely, or return it as single block
                    return@withContext embeddedLyrics.lines()
                        .filter { it.trim().isNotEmpty() }
                        .mapIndexed { index, text ->
                            // Fake 3-second spacing for plain text lyrics (just so it's readable)
                            LyricLine(startTimeMs = (index * 3000L), text = text.trim())
                        }
                }
            }
        } catch (e: Exception) {
            Log.e("LyricsParser", "Failed to parse lyrics for $uri", e)
        }
        
        emptyList()
    }
}
