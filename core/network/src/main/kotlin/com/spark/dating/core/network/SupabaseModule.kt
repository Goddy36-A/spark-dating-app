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
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

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
        // Default client timeouts are too tight for photo uploads on slower/cellular
        // connections and were causing socket timeouts on Storage uploads.
        httpConfig {
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
            }
        }
        install(Auth) {
            // Store session via platform default (Android EncryptedSharedPreferences)
            autoSaveToStorage = true
            autoLoadFromStorage = true
            // Must match the intent-filter data android:scheme/android:host
            // in AndroidManifest.xml, or the Google OAuth redirect back into
            // the app never completes.
            scheme = "com.spark.dating"
            host = "auth-callback"
        }
        install(Postgrest)
        install(Realtime) {
            reconnectDelay = 5.seconds
        }
        install(Storage)
        install(Functions)
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
