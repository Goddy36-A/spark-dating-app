package com.spark.dating.core.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// ── DataStore ─────────────────────────────────────────────────────────────────

private val Context.preferencesDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "spark_preferences")

private object PreferenceKeys {
    val DARK_THEME = booleanPreferencesKey("dark_theme")
    val NOTIFICATION_NEW_MATCH = booleanPreferencesKey("notif_new_match")
    val NOTIFICATION_NEW_MESSAGE = booleanPreferencesKey("notif_new_message")
    val NOTIFICATION_LIKES = booleanPreferencesKey("notif_likes")
    val LAST_KNOWN_USER_ID = stringPreferencesKey("last_user_id")
}

class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.preferencesDataStore

    val darkTheme: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.DARK_THEME] ?: false
    }

    val notifNewMatch: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.NOTIFICATION_NEW_MATCH] ?: true
    }

    val notifNewMessage: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.NOTIFICATION_NEW_MESSAGE] ?: true
    }

    val notifLikes: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.NOTIFICATION_LIKES] ?: false    // premium only
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.DARK_THEME] = enabled }
    }

    suspend fun setNotifNewMatch(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.NOTIFICATION_NEW_MATCH] = enabled }
    }

    suspend fun setNotifNewMessage(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.NOTIFICATION_NEW_MESSAGE] = enabled }
    }

    suspend fun setNotifLikes(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.NOTIFICATION_LIKES] = enabled }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

// ── Room — cached profiles for offline ───────────────────────────────────────

@Entity(tableName = "cached_profiles")
data class CachedProfileEntity(
    @PrimaryKey val id: String,
    val firstName: String,
    val age: Int,
    val primaryPhotoUrl: String?,
    val bio: String,
    val occupation: String,
    val distanceKm: Double?,
    val cachedAt: Long = System.currentTimeMillis(),
)

@androidx.room.Dao
interface CachedProfileDao {
    @androidx.room.Query("SELECT * FROM cached_profiles ORDER BY cachedAt DESC LIMIT 50")
    fun getAll(): Flow<List<CachedProfileEntity>>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<CachedProfileEntity>)

    @androidx.room.Query("DELETE FROM cached_profiles WHERE cachedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @androidx.room.Query("DELETE FROM cached_profiles")
    suspend fun clearAll()
}

@Database(
    entities = [CachedProfileEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SparkDatabase : RoomDatabase() {
    abstract fun cachedProfileDao(): CachedProfileDao
}

// ── DI ────────────────────────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SparkDatabase =
        Room.databaseBuilder(context, SparkDatabase::class.java, "spark.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideCachedProfileDao(db: SparkDatabase): CachedProfileDao = db.cachedProfileDao()

    @Provides
    @Singleton
    fun providePreferencesRepository(@ApplicationContext context: Context): PreferencesRepository =
        PreferencesRepository(context)
}
