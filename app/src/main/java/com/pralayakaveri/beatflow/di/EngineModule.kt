package com.pralayakaveri.beatflow.di

import com.pralayakaveri.beatflow.data.engine.LibraryIndexingEngineImpl
import com.pralayakaveri.beatflow.domain.engine.LibraryIndexingEngine
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
