package com.tuckercr.hark

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.tuckercr.hark.prefs.PreferencesManager
import edu.cmu.pocketsphinx.Assets
import edu.cmu.pocketsphinx.Hypothesis
import edu.cmu.pocketsphinx.RecognitionListener
import edu.cmu.pocketsphinx.SpeechRecognizer
import edu.cmu.pocketsphinx.SpeechRecognizerSetup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class MicState { DISABLED_NO_PERMISSION, LISTENING, SPEAKING, OFF }

/** What the engine is doing right now. The UI observes this; it never touches the recognizer. */
data class EngineState(
    val micState: MicState = MicState.OFF,
    val wakeWordTriggered: String? = null,
    val isMuted: Boolean = false,
)

/**
 * Owns the speech recognizer. It lives for the whole process and is driven by [ListenerService],
 * so listening continues with no Activity (after Android destroys it, or when the service is
 * restarted) and the "Listening" notification is never shown while nothing is listening.
 */
class ListenerEngine(
    private val application: Application,
    private val preferencesManager: PreferencesManager,
    private val chimePlayer: ChimePlayer,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private data class Config(
        val wakeWord: String,
        val sensitivity: Int,
    )

    private var recognizer: SpeechRecognizer? = null
    private var runJob: Job? = null
    private var setupJob: Job? = null
    private var config = Config("", KeywordTuning.DEFAULT_SENSITIVITY)
    private var alertSettings = AlertSettings()
    private var detectionAction: DetectionAction = DetectionAction.Default
    private var lastDetectionAt: Long? = null

    /** True between [start] and [stop], whether or not the preference flows are still emitting. */
    var isRunning: Boolean = false
        private set

    internal val recognitionListener =
        object : RecognitionListener {
            override fun onBeginningOfSpeech() {
                _state.update { it.copy(micState = MicState.SPEAKING) }
            }

            override fun onEndOfSpeech() {
                _state.update { it.copy(micState = MicState.LISTENING) }
            }

            override fun onPartialResult(hypothesis: Hypothesis?) {
                hypothesis ?: return
                val text = hypothesis.hypstr ?: return
                val wakeWord = config.wakeWord
                if (wakeWord.isEmpty() || !(text == wakeWord || text.contains(wakeWord))) return
                // The decoder can report the same utterance repeatedly, and a headless listener must
                // not stay deaf until someone dismisses a screen, so re-arm by time instead.
                val last = lastDetectionAt
                val time = now()
                if (last != null && time - last < COOLDOWN_MS) return
                lastDetectionAt = time

                _state.update { it.copy(wakeWordTriggered = text, micState = MicState.OFF) }
                chimePlayer.play(alertSettings)
                vibrateForWakeWord()
                postWakeWordNotification()
                // Stop and start again to clear the hypothesis buffer for the next detection
                recognizer?.stop()
                recognizer?.startListening(HOT_WORD_SEARCH)
            }

            override fun onResult(hypothesis: Hypothesis?) {
                _state.update { it.copy(micState = MicState.LISTENING) }
            }

            override fun onError(e: Exception) {
                Log.e(TAG, "onError()", e)
            }

            override fun onTimeout() {}
        }

    /** Starts listening and keeps following the saved wake word, sensitivity, alert and action. */
    fun start() {
        if (isRunning) return
        isRunning = true
        runJob =
            scope.launch {
                launch { preferencesManager.detectionActionFlow.collect { detectionAction = it } }
                launch { preferencesManager.alertSettingsFlow.collect { alertSettings = it } }
                combine(preferencesManager.wakeWordFlow, preferencesManager.sensitivityFlow) { word, sensitivity ->
                    Config(
                        word ?: application.getString(R.string.default_wake_word),
                        (sensitivity ?: KeywordTuning.DEFAULT_SENSITIVITY)
                            .coerceIn(KeywordTuning.MIN_SENSITIVITY, KeywordTuning.MAX_SENSITIVITY),
                    )
                }.distinctUntilChanged().collect {
                    config = it
                    Log.d(TAG, "config: wakeWord=${it.wakeWord} sensitivity=${it.sensitivity}")
                    reapply()
                }
            }
    }

    /** Stops listening. Mute is kept, so a later [start] still respects it. */
    fun stop() {
        isRunning = false
        runJob?.cancel()
        runJob = null
        setupJob?.cancel()
        setupJob = null
        recognizer?.teardown()
        recognizer = null
        chimePlayer.stop()
        _state.update { it.copy(micState = MicState.OFF) }
    }

    /** Recovers a running engine that has no recognizer, for example after a permission was granted. */
    fun refresh() {
        if (isRunning && recognizer == null && setupJob?.isActive != true) reapply()
    }

    /** Mute stops listening right now. It is not persisted: a fresh launch listens again. */
    fun setMuted(muted: Boolean) {
        if (_state.value.isMuted == muted) return
        _state.update { it.copy(isMuted = muted) }
        if (muted) chimePlayer.stop()
        if (isRunning) reapply()
    }

    fun clearTriggered() {
        chimePlayer.stop()
        _state.update { it.copy(wakeWordTriggered = null) }
    }

    private fun reapply() {
        setupJob?.cancel()
        setupJob = scope.launch { applyRecognizer() }
    }

    private suspend fun applyRecognizer() {
        withContext(ioDispatcher) {
            recognizer?.teardown()
            recognizer = null
        }
        if (_state.value.isMuted) {
            _state.update { it.copy(micState = MicState.OFF) }
            return
        }
        val hasMic =
            ContextCompat.checkSelfPermission(application, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            _state.update { it.copy(micState = MicState.DISABLED_NO_PERMISSION) }
            return
        }
        val wakeWord = config.wakeWord
        if (wakeWord.isBlank()) {
            Log.w(TAG, "wake word is empty, skipping")
            return
        }
        val tuning = KeywordTuning.forSensitivity(config.sensitivity, WakePhrase.words(wakeWord).size)
        Log.d(TAG, "setup: wakeWord=$wakeWord tuning=$tuning")
        try {
            val newRecognizer =
                withContext(ioDispatcher) {
                    val assetsDir = Assets(application).syncAssets()
                    SpeechRecognizerSetup
                        .defaultSetup()
                        .setAcousticModel(File(assetsDir, "models/en-us-ptm"))
                        .setDictionary(File(assetsDir, "models/lm/words.dic"))
                        .setKeywordThreshold(tuning.threshold)
                        .setFloat("-kws_plp", tuning.phoneLoopProbability.toDouble())
                        .setInteger("-kws_delay", tuning.delayFrames)
                        .recognizer
                }
            newRecognizer.addKeyphraseSearch(HOT_WORD_SEARCH, wakeWord)
            newRecognizer.addListener(recognitionListener)
            newRecognizer.startListening(HOT_WORD_SEARCH)
            recognizer = newRecognizer
            _state.update { it.copy(micState = MicState.LISTENING) }
            Log.d(TAG, "setup: listening for \"$wakeWord\"")
        } catch (e: IOException) {
            Log.e(TAG, "setup() failed", e)
            _state.update { it.copy(micState = MicState.DISABLED_NO_PERMISSION) }
        }
    }

    private fun vibrateForWakeWord() {
        val pattern = NotificationUtils.VIBRATION_PATTERN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            application
                .getSystemService(VibratorManager::class.java)
                ?.defaultVibrator
                ?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            (application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
                ?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            (application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
                ?.vibrate(pattern, -1)
        }
    }

    private fun postWakeWordNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(application, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = application.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        NotificationUtils.initChannels(application)
        nm.notify(
            NotificationUtils.NOTIFICATION_ID_HOT_WORD,
            NotificationUtils.createHotWordNotification(application, detectionAction),
        )
    }

    private fun SpeechRecognizer.teardown() {
        removeListener(recognitionListener)
        cancel()
        stop()
        shutdown()
    }

    companion object {
        private const val TAG = "ListenerEngine"
        private const val HOT_WORD_SEARCH = "HOT_WORD_SEARCH"

        /** Detections closer together than this are treated as one. */
        internal const val COOLDOWN_MS = 10_000L
    }
}
