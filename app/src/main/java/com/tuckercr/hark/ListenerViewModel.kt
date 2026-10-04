package com.tuckercr.hark

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorPrivacyManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuckercr.hark.prefs.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListenerUiState(
    val micState: MicState = MicState.OFF,
    val wakeWord: String = "",
    val sensitivity: Int = ListenerViewModel.DEFAULT_SENSITIVITY,
    val wakeWordTriggered: String? = null,
    val dictionaryWords: List<String> = emptyList(),
    val isMicrophonePermissionGranted: Boolean = false,
    val isMicrophonePrivacyEnabled: Boolean = false,
    val supportsMicrophoneToggle: Boolean = false,
    val detectionAction: DetectionAction = DetectionAction.Default,
    val alertSettings: AlertSettings = AlertSettings(),
    val isMuted: Boolean = false,
)

@HiltViewModel
class ListenerViewModel @Inject constructor(
    private val application: Application,
    private val preferencesManager: PreferencesManager,
    private val chimePlayer: ChimePlayer,
    private val dictionaryRepository: DictionaryRepository,
    private val engine: ListenerEngine,
    @SuppressLint("NewApi") private val sensorPrivacyManager: SensorPrivacyManager?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ListenerUiState())
    val uiState: StateFlow<ListenerUiState> = _uiState.asStateFlow()

    val onboardingCompleteFlow: StateFlow<Boolean?> =
        preferencesManager.onboardingCompleteFlow
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var engineState = EngineState()

    init {
        checkPermissions()
        viewModelScope.launch {
            engine.state.collect {
                engineState = it
                applyEngineState()
            }
        }
        viewModelScope.launch {
            preferencesManager.wakeWordFlow.collectLatest { word ->
                val wakeWord = word ?: application.getString(R.string.default_wake_word)
                if (_uiState.value.wakeWord != wakeWord) {
                    _uiState.update { it.copy(wakeWord = wakeWord) }
                    Log.d(TAG, "wakeWord updated to $wakeWord")
                }
            }
        }
        viewModelScope.launch {
            preferencesManager.sensitivityFlow.collectLatest { saved ->
                saved?.let { value ->
                    _uiState.update { it.copy(sensitivity = value.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)) }
                }
            }
        }
        viewModelScope.launch {
            preferencesManager.detectionActionFlow.collectLatest { action ->
                _uiState.update { it.copy(detectionAction = action) }
            }
        }
        viewModelScope.launch {
            preferencesManager.alertSettingsFlow.collectLatest { settings ->
                _uiState.update { it.copy(alertSettings = settings) }
            }
        }
        loadDictionaryWords()
    }

    /** Mirrors the engine into the UI state. Without permission the mic is shown as disabled. */
    private fun applyEngineState() {
        _uiState.update {
            it.copy(
                micState = if (it.isMicrophonePermissionGranted) engineState.micState else MicState.DISABLED_NO_PERMISSION,
                wakeWordTriggered = engineState.wakeWordTriggered,
                isMuted = engineState.isMuted,
            )
        }
    }

    fun checkPermissions() {
        val context = application

        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotificationPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        // Checking isSensorPrivacyEnabled requires the restricted OBSERVE_SENSOR_PRIVACY permission.
        // We can use AudioManager as a fallback to check if the microphone is software-muted.
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        val isMuted = audioManager?.isMicrophoneMute ?: false

        var supportsToggle = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sensorPrivacyManager?.let {
                supportsToggle = it.supportsSensorToggle(SensorPrivacyManager.Sensors.MICROPHONE)
            }
        }

        _uiState.update {
            it.copy(
                isMicrophonePermissionGranted = hasMicPermission && hasNotificationPermission,
                isMicrophonePrivacyEnabled = isMuted,
                supportsMicrophoneToggle = supportsToggle,
            )
        }
        applyEngineState()
    }

    private fun loadDictionaryWords() {
        viewModelScope.launch(Dispatchers.IO) {
            val words = dictionaryRepository.loadList()
            _uiState.update { it.copy(dictionaryWords = words) }
        }
    }

    /** Re-checks permissions and lets a running engine recover (for example after a grant). */
    fun setup() {
        checkPermissions()
        engine.refresh()
    }

    fun setSensitivity(value: Int) {
        val clamped = value.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
        if (_uiState.value.sensitivity == clamped) return
        _uiState.update { it.copy(sensitivity = clamped) }
        // The engine follows the saved value and restarts the recognizer with it.
        viewModelScope.launch { preferencesManager.setSensitivity(clamped) }
    }

    /**
     * Mute stops listening right now and keeps it stopped (including across onResume) until unmuted.
     * It is deliberately not persisted: a fresh launch listens again.
     */
    fun setMuted(muted: Boolean) {
        engine.setMuted(muted)
    }

    fun setWakeWord(word: String) {
        val phrase = WakePhrase.normalize(word)
        if (phrase.isEmpty()) return
        viewModelScope.launch {
            preferencesManager.updateWakeWord(phrase)
        }
    }

    fun setAlertSound(sound: AlertSound) {
        viewModelScope.launch { preferencesManager.setAlertSound(sound) }
    }

    fun setAlertDuration(duration: AlertDuration) {
        viewModelScope.launch { preferencesManager.setAlertDuration(duration) }
    }

    /** Plays the alert with the current settings so the user can hear what a detection sounds like. */
    fun previewAlert(settings: AlertSettings = _uiState.value.alertSettings) {
        chimePlayer.play(settings)
    }

    fun stopAlert() {
        chimePlayer.stop()
    }

    fun clearWakeWordTriggered() {
        engine.clearTriggered()
    }

    fun setDetectionAction(action: DetectionAction) {
        viewModelScope.launch {
            preferencesManager.setDetectionAction(action)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferencesManager.setOnboardingComplete(true)
        }
    }

    companion object {
        private const val TAG = "ListenerViewModel"
        const val MIN_SENSITIVITY = KeywordTuning.MIN_SENSITIVITY
        const val MAX_SENSITIVITY = KeywordTuning.MAX_SENSITIVITY
        const val DEFAULT_SENSITIVITY = KeywordTuning.DEFAULT_SENSITIVITY
    }
}
