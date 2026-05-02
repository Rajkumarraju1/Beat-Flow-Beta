package com.pralayakaveri.orbitmusic.di

import com.pralayakaveri.orbitmusic.data.engine.GalaxyIndexModuleImpl
import com.pralayakaveri.orbitmusic.data.engine.SearchIndexModuleImpl
import com.pralayakaveri.orbitmusic.domain.engine.LibraryIndexModule
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class IndexingModule {

    @Binds
    @Singleton
    @IntoSet
    abstract fun bindSearchIndexModule(
        searchIndexModuleImpl: SearchIndexModuleImpl
    ): LibraryIndexModule

    @Binds
    @Singleton
    @IntoSet
    abstract fun bindGalaxyIndexModule(
        galaxyIndexModuleImpl: GalaxyIndexModuleImpl
    ): LibraryIndexModule
}
