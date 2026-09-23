package com.tuckercr.hark.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

class PreferencesManager(
    private val dataStore: DataStore<Preferences>,
) {
    object PreferencesKeys {
        val WAKE_WORD = stringPreferencesKey("wake_word")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    val wakeWordFlow: Flow<String?> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[PreferencesKeys.WAKE_WORD]
            }

    val onboardingCompleteFlow: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[PreferencesKeys.ONBOARDING_COMPLETE] ?: false
            }

    suspend fun updateWakeWord(wakeWord: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WAKE_WORD] = wakeWord
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETE] = complete
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
