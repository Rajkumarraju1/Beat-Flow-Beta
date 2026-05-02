package com.pralayakaveri.orbitmusic.di

import android.content.Context
import androidx.room.Room
import com.pralayakaveri.orbitmusic.data.local.AppDatabase
import com.pralayakaveri.orbitmusic.data.local.FavoritesDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "orbitmusic_db"
        )
        .addMigrations(
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12
        )
        .build()
    }

    @Provides
    fun provideFavoritesDao(database: AppDatabase): FavoritesDao {
        return database.favoritesDao()
    }

    @Provides
    fun providePlayCountDao(database: AppDatabase): com.pralayakaveri.orbitmusic.data.local.PlayCountDao {
        return database.playCountDao()
    }

    @Provides
    fun provideSongMetadataDao(database: AppDatabase): com.pralayakaveri.orbitmusic.data.local.SongMetadataDao {
        return database.songMetadataDao()
    }

    @Provides
    fun provideCustomArtworkDao(database: AppDatabase): com.pralayakaveri.orbitmusic.data.local.CustomArtworkDao {
        return database.customArtworkDao()
    }

    @Provides
    fun providePlaylistDao(database: AppDatabase): com.pralayakaveri.orbitmusic.data.local.PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    fun provideArtistImageDao(database: AppDatabase): com.pralayakaveri.orbitmusic.data.local.ArtistImageDao {
        return database.artistImageDao()
    }

    @Provides
    fun provideLibrarySongDao(database: AppDatabase): com.pralayakaveri.orbitmusic.data.local.LibrarySongDao {
        return database.librarySongDao()
    }

    @Provides
    fun provideLibraryIndexDao(database: AppDatabase): com.pralayakaveri.orbitmusic.data.local.LibraryIndexDao {
        return database.libraryIndexDao()
    }

    @Provides
    fun provideSearchIndexDao(database: AppDatabase): com.pralayakaveri.orbitmusic.data.local.SearchIndexDao {
        return database.searchIndexDao()
    }

    @Provides
    fun provideGalaxyIndexDao(database: AppDatabase): com.pralayakaveri.orbitmusic.data.local.GalaxyIndexDao {
        return database.galaxyIndexDao()
    }

    @Provides
    fun provideGalaxyAnchorDao(database: AppDatabase): com.pralayakaveri.orbitmusic.data.local.GalaxyAnchorDao {
        return database.galaxyAnchorDao()
    }
}
