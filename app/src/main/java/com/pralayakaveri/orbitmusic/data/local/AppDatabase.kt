package com.pralayakaveri.orbitmusic.data.local

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
        ArtistImageEntity::class,
        LibrarySongEntity::class,
        LibraryIndexEntity::class,
        SearchIndexEntity::class,
        GalaxyIndexEntity::class,
        GalaxyAnchorEntity::class
    ], 
    version = 12, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao
    abstract fun playCountDao(): PlayCountDao
    abstract fun songMetadataDao(): SongMetadataDao
    abstract fun customArtworkDao(): CustomArtworkDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun artistImageDao(): ArtistImageDao
    abstract fun librarySongDao(): LibrarySongDao
    abstract fun libraryIndexDao(): LibraryIndexDao
    abstract fun searchIndexDao(): SearchIndexDao
    abstract fun galaxyIndexDao(): GalaxyIndexDao
    abstract fun galaxyAnchorDao(): GalaxyAnchorDao

    companion object {
        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Create LibrarySong table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `library_songs` (
                        `id` INTEGER NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `artist` TEXT NOT NULL, 
                        `artistId` INTEGER NOT NULL, 
                        `album` TEXT NOT NULL, 
                        `albumId` INTEGER NOT NULL, 
                        `duration` INTEGER NOT NULL, 
                        `dataPath` TEXT NOT NULL, 
                        `trackNumber` INTEGER NOT NULL, 
                        `uriString` TEXT NOT NULL, 
                        `albumArtUriString` TEXT, 
                        PRIMARY KEY(`id`)
                    )
                """)
                
                // 2. Create LibraryIndex table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `library_index` (
                        `songId` INTEGER NOT NULL, 
                        `isOrphan` INTEGER NOT NULL, 
                        `recoveryHash` TEXT NOT NULL, 
                        `secondaryHash` TEXT, 
                        `firstSeenMissingAt` INTEGER, 
                        `lastSyncedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`songId`)
                    )
                """)
                
                // 3. Hydrate Mirror from existing metadata (Best effort)
                db.execSQL("""
                    INSERT OR IGNORE INTO library_songs (id, title, artist, artistId, album, albumId, duration, dataPath, trackNumber, uriString)
                    SELECT songId, title, artist, 0, album, 0, 0, '', 0, '' FROM song_metadata
                """)
            }
        }

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `search_index` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `subtitle` TEXT NOT NULL, 
                        `type` TEXT NOT NULL, 
                        `targetId` TEXT NOT NULL, 
                        `targetIdString` TEXT NOT NULL, 
                        `playCount` TEXT NOT NULL, 
                        `isFavorite` TEXT NOT NULL, 
                        `metadataBlob` TEXT NOT NULL
                    )
                """)
            }
        }

        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `galaxy_index` (
                        `songId` INTEGER NOT NULL, 
                        `artistId` INTEGER NOT NULL, 
                        `x` REAL NOT NULL, 
                        `y` REAL NOT NULL, 
                        `z` REAL NOT NULL, 
                        `clusterId` TEXT NOT NULL, 
                        `radius` REAL NOT NULL, 
                        `lastUpdated` INTEGER NOT NULL, 
                        PRIMARY KEY(`songId`)
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `galaxy_anchors` (
                        `artistId` INTEGER NOT NULL, 
                        `x` REAL NOT NULL, 
                        `y` REAL NOT NULL, 
                        `clusterId` TEXT NOT NULL, 
                        PRIMARY KEY(`artistId`)
                    )
                """)
            }
        }

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `song_metadata` ADD COLUMN `lyrics` TEXT")
            }
        }
    }
}
