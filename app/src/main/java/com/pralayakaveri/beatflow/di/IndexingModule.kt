package com.pralayakaveri.beatflow.di

import com.pralayakaveri.beatflow.data.engine.GalaxyIndexModuleImpl
import com.pralayakaveri.beatflow.data.engine.SearchIndexModuleImpl
import com.pralayakaveri.beatflow.domain.engine.LibraryIndexModule
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
