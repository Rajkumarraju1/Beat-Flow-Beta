package com.pralayakaveri.beatflow.di

import android.content.Context
import androidx.room.Room
import com.pralayakaveri.beatflow.data.local.AppDatabase
import com.pralayakaveri.beatflow.data.local.FavoritesDao
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
            "beatflow_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideFavoritesDao(database: AppDatabase): FavoritesDao {
        return database.favoritesDao()
    }

    @Provides
    fun providePlayCountDao(database: AppDatabase): com.pralayakaveri.beatflow.data.local.PlayCountDao {
        return database.playCountDao()
    }

    @Provides
    fun provideSongMetadataDao(database: AppDatabase): com.pralayakaveri.beatflow.data.local.SongMetadataDao {
        return database.songMetadataDao()
    }

    @Provides
    fun provideCustomArtworkDao(database: AppDatabase): com.pralayakaveri.beatflow.data.local.CustomArtworkDao {
        return database.customArtworkDao()
    }

    @Provides
    fun providePlaylistDao(database: AppDatabase): com.pralayakaveri.beatflow.data.local.PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    fun provideArtistImageDao(database: AppDatabase): com.pralayakaveri.beatflow.data.local.ArtistImageDao {
        return database.artistImageDao()
    }
}
