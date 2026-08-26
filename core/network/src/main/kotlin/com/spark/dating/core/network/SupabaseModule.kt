package com.spark.dating.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.client.plugins.logging.LogLevel
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(
        @SupabaseUrl supabaseUrl: String,
        @SupabaseAnonKey supabaseAnonKey: String,
    ): SupabaseClient = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseAnonKey,
    ) {
        install(Auth) {
            // Store session via platform default (Android EncryptedSharedPreferences)
            autoSaveToStorage = true
            autoLoadFromStorage = true
        }
        install(Postgrest)
        install(Realtime) {
            reconnectDelay = 5_000L
        }
        install(Storage)
        install(Functions)

        // Ktor HTTP client config
        httpConfig {
            engine {
                // OkHttp is used — configured in build.gradle
            }
        }
    }

    @Provides
    @Singleton
    fun provideAuth(client: SupabaseClient): Auth = client.auth

    @Provides
    @Singleton
    fun providePostgrest(client: SupabaseClient): Postgrest = client.postgrest

    @Provides
    @Singleton
    fun provideRealtime(client: SupabaseClient): Realtime = client.realtime

    @Provides
    @Singleton
    fun provideStorage(client: SupabaseClient): Storage = client.storage

    @Provides
    @Singleton
    fun provideFunctions(client: SupabaseClient): Functions = client.functions
}
