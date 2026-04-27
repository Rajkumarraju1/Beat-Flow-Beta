package com.pralayakaveri.beatflow.domain.util

fun String.cleanSongTitle(): String {
    // 1. Remove anything in square brackets
    var cleaned = this.replace(Regex("\\[.*?\\]"), "")
    
    // 2. Remove leading track numbers (e.g., "02 - ", "1. ")
    cleaned = cleaned.replace(Regex("^\\d+\\s*[\\-\\.]\\s*"), "")
    
    // 3. Remove known website tags (case insensitive)
    // Matches "SenSongs", "SenSongsMp3", "www.SenSongsMp3.co", etc.
    cleaned = cleaned.replace(Regex("(?i)(-|::)?\\s*(www\\.)?sensongs(mp3)?(\\.(com|co|in))?"), "")
    
    // 4. Remove standalone "::" and extra hyphens
    cleaned = cleaned.replace(Regex("::"), "")
    
    // 5. Clean up extra spaces or trailing separators
    cleaned = cleaned.trim()
    cleaned = cleaned.replace(Regex("^[\\s\\-]+|[\\s\\-]+$"), "")
    cleaned = cleaned.replace(Regex("\\s{2,}"), " ")
    
    // Fallback if we accidentally stripped everything
    if (cleaned.isEmpty()) return this 
    
    return cleaned
}
