package com.pralayakaveri.orbitmusic.di

import com.pralayakaveri.orbitmusic.data.engine.LibraryIndexingEngineImpl
import com.pralayakaveri.orbitmusic.domain.engine.LibraryIndexingEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Binds
    @Singleton
    abstract fun bindLibraryIndexingEngine(
        impl: LibraryIndexingEngineImpl
    ): LibraryIndexingEngine
}
