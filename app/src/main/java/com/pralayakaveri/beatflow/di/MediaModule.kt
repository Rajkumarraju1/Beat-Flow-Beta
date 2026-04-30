package com.pralayakaveri.beatflow.di

import android.content.Context
import com.pralayakaveri.beatflow.domain.repository.MusicRepository
import com.pralayakaveri.beatflow.domain.util.PlaybackSessionManager
import com.pralayakaveri.beatflow.service.MusicController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideMusicController(
        @ApplicationContext context: Context,
        musicRepository: MusicRepository,
        sessionManager: PlaybackSessionManager
    ): MusicController {
        android.util.Log.d("MediaModule", "Hilt providing MusicController instance")
        return MusicController.getInstance(context, musicRepository, sessionManager)
    }
}
