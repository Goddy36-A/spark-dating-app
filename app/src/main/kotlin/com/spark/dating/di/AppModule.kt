package com.spark.dating.di

import com.spark.dating.BuildConfig
import com.spark.dating.core.network.SupabaseAnonKey
import com.spark.dating.core.network.SupabaseUrl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @SupabaseUrl
    fun provideSupabaseUrl(): String = BuildConfig.SUPABASE_URL

    @Provides
    @Singleton
    @SupabaseAnonKey
    fun provideSupabaseAnonKey(): String = BuildConfig.SUPABASE_ANON_KEY
}
