package com.pralayakaveri.beatflow.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteSongEntity::class, 
        PlayCountEntity::class, 
        SongMetadataEntity::class, 
        CustomArtworkEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        ArtistImageEntity::class
    ], 
    version = 6, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao
    abstract fun playCountDao(): PlayCountDao
    abstract fun songMetadataDao(): SongMetadataDao
    abstract fun customArtworkDao(): CustomArtworkDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun artistImageDao(): ArtistImageDao
}
