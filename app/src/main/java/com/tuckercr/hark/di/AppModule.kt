package com.tuckercr.hark.di

import android.content.Context
import android.hardware.SensorPrivacyManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.tuckercr.hark.prefs.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val PREFERENCE_NAME = "com.tuckercr.zamzam.prefs"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = PREFERENCE_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, PREFERENCE_NAME))
    },
)

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context,
    ): PreferencesManager = PreferencesManager(context.dataStore)

    @android.annotation.SuppressLint("NewApi")
    @Provides
    @Singleton
    fun provideSensorPrivacyManager(
        @ApplicationContext context: Context,
    ): SensorPrivacyManager? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(SensorPrivacyManager::class.java)
        } else {
            null
        }
}
