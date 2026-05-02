package com.pralayakaveri.orbitmusic.domain.util

import com.pralayakaveri.orbitmusic.domain.model.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object LrcParser {
    
    // Regex to match [mm:ss.xx] or [mm:ss:xx] or [mm:ss.xxx]
    private val timeTagRegex = Regex("""\[(\d+):(\d{2})[.:](\d+)]""")

    suspend fun parseLrcFile(file: File): List<LyricLine> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        
        parseLrcContent(file.readText())
    }

    fun isLrcFormat(content: String): Boolean {
        return timeTagRegex.containsMatchIn(content)
    }

    fun parsePlainText(content: String, durationMs: Long): List<LyricLine> {
        val lines = content.lines().filter { it.trim().isNotEmpty() }
        if (lines.isEmpty()) return emptyList()
        
        val interval = if (durationMs > 0) durationMs / lines.size else 3000L
        return lines.mapIndexed { index, text ->
            LyricLine(
                startTimeMs = index * interval,
                text = text.trim()
            )
        }
    }

    fun parseLrcContent(content: String): List<LyricLine> {
        val lines = content.lines()
        val lyricLines = mutableListOf<LyricLine>()
        var offsetMs = 0L

        // Regex for offset tag [offset: +/-ms]
        val offsetRegex = Regex("""\[offset:\s*([-+]?\d+)]""")

        for (line in lines) {
            val trimLine = line.trim()
            if (trimLine.isEmpty()) continue

            // Check for offset tag
            val offsetMatch = offsetRegex.find(trimLine)
            if (offsetMatch != null) {
                offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                continue // Skip to next line if it's just an offset tag (usually in header)
            }

            // Find all time tags in the line (e.g., [00:12.34][00:15.67] lyrics here)
            val timeMatches = timeTagRegex.findAll(trimLine).toList()
            
            if (timeMatches.isNotEmpty()) {
                // Extract the actual lyric text by removing the tags
                val lyricText = trimLine.replace(timeTagRegex, "").trim()
                
                // For each time tag attached to this line, create a LyricLine entry
                for (match in timeMatches) {
                    val minutes = match.groupValues[1].toLong()
                    val seconds = match.groupValues[2].toLong()
                    val fractionStr = match.groupValues[3]
                    
                    // Normalize milliseconds correctly based on digit count
                    val milliseconds = when (fractionStr.length) {
                        1 -> fractionStr.toLong() * 100
                        2 -> fractionStr.toLong() * 10
                        3 -> fractionStr.toLong()
                        else -> fractionStr.take(3).toLong() // Truncate to precision
                    }

                    val totalTimeMs = (minutes * 60 * 1000) + (seconds * 1000) + milliseconds - offsetMs
                    lyricLines.add(LyricLine(totalTimeMs, lyricText))
                }
            }
        }

        // Sort by time just in case tags were out of order
        return lyricLines.sortedBy { it.startTimeMs }
    }
}
