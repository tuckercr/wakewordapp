package com.tuckercr.hark.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tuckercr.hark.AlertDuration
import com.tuckercr.hark.AlertSettings
import com.tuckercr.hark.AlertSound
import com.tuckercr.hark.DetectionAction
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
        val SENSITIVITY = intPreferencesKey("sensitivity")
        val ALERT_SOUND = stringPreferencesKey("alert_sound")
        val ALERT_DURATION = stringPreferencesKey("alert_duration")
        val DETECTION_ACTION_TYPE = stringPreferencesKey("detection_action_type")
        val DETECTION_ACTION_PACKAGE = stringPreferencesKey("detection_action_package")
        val DETECTION_ACTION_NAME = stringPreferencesKey("detection_action_name")
    }

    private val safeData: Flow<Preferences> =
        dataStore.data.catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }

    val wakeWordFlow: Flow<String?> = safeData.map { it[PreferencesKeys.WAKE_WORD] }

    val sensitivityFlow: Flow<Int?> = safeData.map { it[PreferencesKeys.SENSITIVITY] }

    /** Absent key means the default alarm tone, an empty string means silent, anything else is a URI. */
    val alertSettingsFlow: Flow<AlertSettings> =
        safeData.map { prefs ->
            val sound =
                when (val stored = prefs[PreferencesKeys.ALERT_SOUND]) {
                    null -> AlertSound.Default
                    "" -> AlertSound.Silent
                    else -> AlertSound.Custom(stored)
                }
            AlertSettings(sound, AlertDuration.fromName(prefs[PreferencesKeys.ALERT_DURATION]))
        }

    val onboardingCompleteFlow: Flow<Boolean> =
        safeData.map { it[PreferencesKeys.ONBOARDING_COMPLETE] ?: false }

    val detectionActionFlow: Flow<DetectionAction> =
        safeData.map { prefs ->
            when (prefs[PreferencesKeys.DETECTION_ACTION_TYPE]) {
                ACTION_TYPE_LAUNCH_APP -> {
                    val pkg = prefs[PreferencesKeys.DETECTION_ACTION_PACKAGE]
                    val name = prefs[PreferencesKeys.DETECTION_ACTION_NAME]
                    if (pkg != null && name != null) DetectionAction.LaunchApp(pkg, name) else DetectionAction.Default
                }
                else -> DetectionAction.Default
            }
        }

    suspend fun updateWakeWord(wakeWord: String) {
        dataStore.edit { it[PreferencesKeys.WAKE_WORD] = wakeWord }
    }

    suspend fun setSensitivity(sensitivity: Int) {
        dataStore.edit { it[PreferencesKeys.SENSITIVITY] = sensitivity }
    }

    suspend fun setAlertSound(sound: AlertSound) {
        dataStore.edit { prefs ->
            when (sound) {
                is AlertSound.Default -> prefs.remove(PreferencesKeys.ALERT_SOUND)
                is AlertSound.Silent -> prefs[PreferencesKeys.ALERT_SOUND] = ""
                is AlertSound.Custom -> prefs[PreferencesKeys.ALERT_SOUND] = sound.uri
            }
        }
    }

    suspend fun setAlertDuration(duration: AlertDuration) {
        dataStore.edit { it[PreferencesKeys.ALERT_DURATION] = duration.name }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[PreferencesKeys.ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setDetectionAction(action: DetectionAction) {
        dataStore.edit { prefs ->
            when (action) {
                is DetectionAction.Default -> {
                    prefs[PreferencesKeys.DETECTION_ACTION_TYPE] = ACTION_TYPE_DEFAULT
                    prefs.remove(PreferencesKeys.DETECTION_ACTION_PACKAGE)
                    prefs.remove(PreferencesKeys.DETECTION_ACTION_NAME)
                }
                is DetectionAction.LaunchApp -> {
                    prefs[PreferencesKeys.DETECTION_ACTION_TYPE] = ACTION_TYPE_LAUNCH_APP
                    prefs[PreferencesKeys.DETECTION_ACTION_PACKAGE] = action.packageName
                    prefs[PreferencesKeys.DETECTION_ACTION_NAME] = action.appName
                }
            }
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private const val ACTION_TYPE_DEFAULT = "default"
        private const val ACTION_TYPE_LAUNCH_APP = "launch_app"
    }
}
