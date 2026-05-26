package com.tuckercr.zamzam

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.hardware.SensorPrivacyManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuckercr.zamzam.prefs.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cmu.pocketsphinx.Assets
import edu.cmu.pocketsphinx.Hypothesis
import edu.cmu.pocketsphinx.RecognitionListener
import edu.cmu.pocketsphinx.SpeechRecognizer
import edu.cmu.pocketsphinx.SpeechRecognizerSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

enum class MicState { DISABLED_NO_PERMISSION, LISTENING, SPEAKING, OFF }

data class ListenerUiState(
    val micState: MicState = MicState.OFF,
    val wakeWord: String = "",
    val sensitivity: Int = ListenerViewModel.DEFAULT_SENSITIVITY,
    val wakeWordTriggered: String? = null,
    val dictionaryWords: List<String> = emptyList(),
    val isMicrophonePermissionGranted: Boolean = false,
    val isMicrophonePrivacyEnabled: Boolean = false,
    val supportsMicrophoneToggle: Boolean = false,
)

@HiltViewModel
class ListenerViewModel
@Inject
constructor(
    private val application: Application,
    private val preferencesManager: PreferencesManager,
    @SuppressLint("NewApi") private val sensorPrivacyManager: SensorPrivacyManager?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ListenerUiState())
    val uiState: StateFlow<ListenerUiState> = _uiState.asStateFlow()

    private val _chimeEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val chimeEvent: SharedFlow<Unit> = _chimeEvent.asSharedFlow()

    private var recognizer: SpeechRecognizer? = null
    private var setupJob: Job? = null

    private val recognitionListener =
        object : RecognitionListener {
            override fun onBeginningOfSpeech() {
                _uiState.update { it.copy(micState = MicState.SPEAKING) }
            }

            override fun onEndOfSpeech() {
                _uiState.update { it.copy(micState = MicState.LISTENING) }
            }

            override fun onPartialResult(hypothesis: Hypothesis?) {
                hypothesis ?: return
                val text = hypothesis.hypstr ?: return
                if (_uiState.value.wakeWordTriggered != null) return
                val wakeWord = _uiState.value.wakeWord
                if (text == wakeWord || text.contains(wakeWord)) {
                    _uiState.update { it.copy(wakeWordTriggered = text, micState = MicState.OFF) }
                    _chimeEvent.tryEmit(Unit)
                    // Stop the recognizer so no further partial results fire until setup() is called again
                    viewModelScope.launch(Dispatchers.IO) {
                        recognizer?.teardown()
                        recognizer = null
                    }
                }
            }

            override fun onResult(hypothesis: Hypothesis?) {
                _uiState.update { it.copy(micState = MicState.LISTENING) }
            }

            override fun onError(e: Exception) {
                Log.e(TAG, "onError()", e)
            }

            override fun onTimeout() {}
        }

    init {
        checkPermissions()
        viewModelScope.launch {
            preferencesManager.wakeWordFlow.collectLatest { word ->
                val wakeWord = word ?: application.getString(R.string.default_wake_word)
                if (_uiState.value.wakeWord != wakeWord) {
                    _uiState.update { it.copy(wakeWord = wakeWord) }
                    Log.d(TAG, "wakeword updated to $wakeWord")
                    shutdownRecognizer()
                    setup()
                }
            }
        }
        loadDictionaryWords()
    }

    fun checkPermissions() {
        val hasPermission =
            ContextCompat.checkSelfPermission(
                application,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED

        // TODO This is always false
        val isPrivacyEnabled = false
        var supportsToggle = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sensorPrivacyManager?.let {
                supportsToggle = it.supportsSensorToggle(SensorPrivacyManager.Sensors.MICROPHONE)
                // Removed isSensorPrivacyEnabled check as it requires restricted permission android.permission.OBSERVE_SENSOR_PRIVACY
            }
        }

        _uiState.update {
            it.copy(
                isMicrophonePermissionGranted = hasPermission,
                isMicrophonePrivacyEnabled = isPrivacyEnabled,
                supportsMicrophoneToggle = supportsToggle,
            )
        }
    }

    private fun loadDictionaryWords() {
        viewModelScope.launch(Dispatchers.IO) {
            val words = DictionaryRepository.loadList(application)
            _uiState.update { it.copy(dictionaryWords = words) }
        }
    }

    fun setup() {
        checkPermissions()
        if (!_uiState.value.isMicrophonePermissionGranted) {
            _uiState.update { it.copy(micState = MicState.DISABLED_NO_PERMISSION) }
            return
        }

        val wakeWord = _uiState.value.wakeWord
        if (wakeWord.isBlank()) {
            Log.w(TAG, "setup: wake word is empty, skipping")
            return
        }

        val threshold = ("1.e-" + 2 * _uiState.value.sensitivity).toFloat()
        Log.d(TAG, "setup: wakeWord=$wakeWord threshold=$threshold")

        setupJob?.cancel()
        setupJob =
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    recognizer?.teardown()
                    recognizer = null
                }
                try {
                    val newRecognizer =
                        withContext(Dispatchers.IO) {
                            val assets = Assets(application)
                            val assetsDir = assets.syncAssets()
                            SpeechRecognizerSetup
                                .defaultSetup()
                                .setAcousticModel(File(assetsDir, "models/en-us-ptm"))
                                .setDictionary(File(assetsDir, "models/lm/words.dic"))
                                .setKeywordThreshold(threshold)
                                .recognizer
                        }
                    newRecognizer.addKeyphraseSearch(HOT_WORD_SEARCH, wakeWord)
                    newRecognizer.addListener(recognitionListener)
                    newRecognizer.startListening(HOT_WORD_SEARCH)
                    recognizer = newRecognizer
                    _uiState.update { it.copy(micState = MicState.LISTENING) }
                    Log.d(TAG, "setup: listening for \"$wakeWord\"")
                } catch (e: IOException) {
                    Log.e(TAG, "setup() failed", e)
                    _uiState.update { it.copy(micState = MicState.DISABLED_NO_PERMISSION) }
                }
            }
    }

    fun shutdownRecognizer() {
        setupJob?.cancel()
        recognizer?.teardown()
        recognizer = null
        _uiState.update { it.copy(micState = MicState.OFF, wakeWordTriggered = null) }
    }

    fun setSensitivity(value: Int) {
        if (_uiState.value.sensitivity == value) return
        _uiState.update { it.copy(sensitivity = value) }
        shutdownRecognizer()
        setup()
    }

    fun setWakeWord(word: String) {
        viewModelScope.launch {
            preferencesManager.updateWakeWord(word)
        }
    }

    fun clearWakeWordTriggered() {
        _uiState.update { it.copy(wakeWordTriggered = null) }
    }

    override fun onCleared() {
        shutdownRecognizer()
        super.onCleared()
    }

    private fun SpeechRecognizer.teardown() {
        removeListener(recognitionListener)
        cancel()
        stop()
        shutdown()
    }

    companion object {
        private const val TAG = "ListenerViewModel"
        private const val HOT_WORD_SEARCH = "HOT_WORD_SEARCH"
        const val DEFAULT_SENSITIVITY = 3
    }
}
