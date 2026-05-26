package com.tuckercr.zamzam.di

import android.content.Context
import android.hardware.SensorPrivacyManager
import com.tuckercr.zamzam.prefs.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context,
    ): PreferencesManager = PreferencesManager(context)

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
