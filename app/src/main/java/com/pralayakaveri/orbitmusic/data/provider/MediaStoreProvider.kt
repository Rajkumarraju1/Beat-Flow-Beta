package com.pralayakaveri.orbitmusic.data.provider

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.pralayakaveri.orbitmusic.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MediaStoreProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    data class MediaStoreHeadless(
        val id: Long,
        val path: String,
        val size: Long,
        val dateModified: Long
    )

    suspend fun getHeadlessSongs(
        minDur: Long,
        minSize: Long,
        durEnabled: Boolean,
        sizeEnabled: Boolean
    ): List<MediaStoreHeadless> = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            android.util.Log.w("MediaStoreProvider", "getHeadlessSongs: Missing permissions")
            return@withContext emptyList()
        }
        val result = mutableListOf<MediaStoreHeadless>()
        
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val duration = cursor.getLong(durationCol)
                val size = cursor.getLong(sizeCol)
                
                if (!shouldIncludeTrack(duration, size, minDur, minSize, durEnabled, sizeEnabled)) continue

                result.add(
                    MediaStoreHeadless(
                        id = cursor.getLong(idCol),
                        path = cursor.getString(dataCol) ?: "",
                        size = size,
                        dateModified = cursor.getLong(dateCol)
                    )
                )
            }
        }
        result
    }

    suspend fun getAllSongs(
        minDur: Long,
        minSize: Long,
        durEnabled: Boolean,
        sizeEnabled: Boolean
    ): List<Song> = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            android.util.Log.w("MediaStoreProvider", "getAllSongs: Missing permissions")
            return@withContext emptyList()
        }
        val songs = mutableListOf<Song>()
        
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.IS_MUSIC
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrderLegacy = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.util.Log.e("MediaStoreProvider", "getAllSongs: Executing MODERN path (API 26+) on Version: ${com.pralayakaveri.orbitmusic.BuildConfig.VERSION_CODE}")
            val queryArgs = android.os.Bundle().apply {
                putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Audio.Media.TITLE))
                putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
            }
            context.contentResolver.query(collection, projection, queryArgs, null)
        } else {
            android.util.Log.e("MediaStoreProvider", "getAllSongs: Executing LEGACY path (API < 26) on Version: ${com.pralayakaveri.orbitmusic.BuildConfig.VERSION_CODE}")
            context.contentResolver.query(collection, projection, selection, null, sortOrderLegacy)
        }

        cursor?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val isMusicColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC)

            while (cursor.moveToNext()) {
                if (cursor.getInt(isMusicColumn) == 0) continue

                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                
                if (!shouldIncludeTrack(duration, size, minDur, minSize, durEnabled, sizeEnabled)) continue

                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Cosmic Signal"
                val artist = cursor.getString(artistColumn) ?: "Stellar Resonance"
                val artistId = cursor.getLong(artistIdColumn)
                val album = cursor.getString(albumColumn) ?: "The Singularity"
                val albumId = cursor.getLong(albumIdColumn)
                val dataPath = cursor.getString(dataColumn) ?: ""
                val trackNumber = cursor.getInt(trackColumn)

                val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                
                val artworkUri = Uri.parse("content://media/external/audio/albumart")
                val albumArtUri = ContentUris.withAppendedId(artworkUri, albumId)

                songs.add(
                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        artistId = artistId,
                        album = album,
                        albumId = albumId,
                        duration = duration,
                        dataPath = dataPath,
                        trackNumber = trackNumber,
                        genre = null,
                        uri = contentUri,
                        albumArtUri = albumArtUri
                    )
                )
            }
        }
        songs
    }

    suspend fun getRecentlyAddedSongs(
        limit: Int,
        minDur: Long,
        minSize: Long,
        durEnabled: Boolean,
        sizeEnabled: Boolean
    ): List<Song> = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            android.util.Log.w("MediaStoreProvider", "getRecentlyAddedSongs: Missing permissions")
            return@withContext emptyList()
        }
        val songs = mutableListOf<Song>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.IS_MUSIC
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortColumn = MediaStore.Audio.Media.DATE_ADDED
        
        val cursor = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.util.Log.e("MediaStoreProvider", "getRecentlyAddedSongs: Executing MODERN path (API 26+) on Version: ${com.pralayakaveri.orbitmusic.BuildConfig.VERSION_CODE}")
            val queryArgs = android.os.Bundle().apply {
                putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(sortColumn))
                putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                // Increase limit slightly to account for non-music files that will be filtered in memory
                putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit + 20)
            }
            context.contentResolver.query(collection, projection, queryArgs, null)
        } else {
            android.util.Log.e("MediaStoreProvider", "getRecentlyAddedSongs: Executing LEGACY path (API < 26) on Version: ${com.pralayakaveri.orbitmusic.BuildConfig.VERSION_CODE}")
            val sortOrderLegacy = "$sortColumn DESC"
            context.contentResolver.query(collection, projection, selection, null, sortOrderLegacy)
        }

        cursor?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)

            while (cursor.moveToNext()) {
                val duration = cursor.getLong(durationCol)
                val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                
                if (!shouldIncludeTrack(duration, size, minDur, minSize, durEnabled, sizeEnabled)) continue

                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

                songs.add(
                    Song(
                        id = id, title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown", artistId = cursor.getLong(artistIdCol),
                        album = cursor.getString(albumCol) ?: "Unknown", albumId = albumId,
                        duration = duration, dataPath = cursor.getString(dataCol) ?: "",
                        trackNumber = cursor.getInt(trackCol), genre = null, 
                        uri = contentUri, albumArtUri = albumArtUri
                    )
                )

                if (songs.size >= limit) break
            }
        }
        songs
    }

    suspend fun getSongsByIds(
        ids: List<Long>,
        minDur: Long,
        minSize: Long,
        durEnabled: Boolean,
        sizeEnabled: Boolean
    ): List<Song> = withContext(Dispatchers.IO) {
        if (ids.isEmpty() || !hasPermission()) {
            android.util.Log.w("MediaStoreProvider", "getSongsByIds: Empty IDs or Missing permissions")
            return@withContext emptyList()
        }
        
        val songs = mutableListOf<Song>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.SIZE
        )

        val idList = ids.joinToString(",")
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media._ID} IN ($idList)"

        context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)

            while (cursor.moveToNext()) {
                val duration = cursor.getLong(durationCol)
                val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                
                if (!shouldIncludeTrack(duration, size, minDur, minSize, durEnabled, sizeEnabled)) continue

                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                songs.add(
                    Song(
                        id = id,
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown",
                        artistId = cursor.getLong(artistIdCol),
                        album = cursor.getString(albumCol) ?: "Unknown",
                        albumId = albumId,
                        duration = cursor.getLong(durationCol),
                        dataPath = cursor.getString(dataCol) ?: "",
                        trackNumber = cursor.getInt(trackCol),
                        genre = null,
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                    )
                )
            }
        }
        songs
    }

    private fun shouldIncludeTrack(
        durationMs: Long?,
        sizeBytes: Long?,
        minDurationMs: Long,
        minSizeBytes: Long,
        filterShortEnabled: Boolean,
        filterTinyEnabled: Boolean
    ): Boolean {
        val d = durationMs ?: 0L
        val s = sizeBytes ?: 0L

        if (filterShortEnabled && d < minDurationMs) return false
        if (filterTinyEnabled && s < minSizeBytes) return false
        return true
    }
}
