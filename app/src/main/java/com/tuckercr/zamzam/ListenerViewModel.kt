package com.tuckercr.zamzam

import android.Manifest
import android.app.Application
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.tuckercr.zamzam.ListenerFragment.MicState
import com.tuckercr.zamzam.prefs.PrefsManager
import com.tuckercr.zamzam.prefs.PrefsManager.Companion.instance
import edu.cmu.pocketsphinx.*
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

class ListenerViewModel(application: Application) : AndroidViewModel(application),
    OnSharedPreferenceChangeListener {
    private var mIsInitialized = false
    val sensitivity = ObservableInt(DEFAULT_SENSITIVITY)
    val sensitivityAsString = ObservableField(DEFAULT_SENSITIVITY.toString())
    val micState: ObservableInt = ObservableInt(MicState.OFF)
    val wakeWordTriggered = ObservableField<String?>()
    val wakeWord = ObservableField(
        instance!!.getString(
            PrefsManager.KEY_WAKE_WORD,
            getApplication<Application>().getString(R.string.default_wake_word)
        )
    )
    var recognizer: SpeechRecognizer? = null
        private set
    private var mDictionaryMap: MutableLiveData<Map<String, List<String>>>? = null
    private var mDictionaryList: MutableLiveData<List<String>>? = null
    private val mRecognizerListener: RecognitionListener = object : RecognitionListener {
        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech()")
            toggleMicIcon(MicState.SPEAKING)
        }

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech()")
            toggleMicIcon(MicState.LISTENING)
        }

        override fun onPartialResult(hypothesis: Hypothesis?) {

            if (hypothesis == null) {
                return
            }

            val text = hypothesis.hypstr
            Log.d(
                TAG, String.format(
                    "onPartialResult: hypothesis string: %s, prob=[%d], bestScore=[%d]",
                    text, hypothesis.prob, hypothesis.bestScore
                )
            )
            val wakeWord = wakeWord.get()
            if (wakeWord == null) {
                Log.e(TAG, "onPartialResult: wakeword is null")
                return
            }
            if (text == wakeWord) {
                wakeWordTriggered(text)
            } else {
                // Could this happen?
                // A: Yes, I've seen stuff like "sami sami sami" in it
                Log.e(TAG, "onPartialResult: unexpected hypothesis string: $text")
                if (text.contains(wakeWord)) {
                    wakeWordTriggered(text)

                    // TODO currently we shutdown after this but it might be necessary to cancel
                    //  when later we don't
                    // mRecognizer.cancel();
                }
            }
        }

        override fun onResult(hypothesis: Hypothesis?) {
            if (hypothesis == null) {
                Log.e(TAG, "on Result: null")
                return
            }
            Log.d(TAG, "on Result: " + hypothesis.hypstr + " : " + hypothesis.bestScore)
            toggleMicIcon(MicState.LISTENING)
        }

        override fun onError(e: Exception) {
            Log.e(TAG, "onError()", e)
        }

        override fun onTimeout() {
            Log.d(TAG, "onTimeout()")
        }
    }

    val dictionaryList: LiveData<List<String>>
        get() {
            Log.d(TAG, "getDictionaryList() called")
            if (mDictionaryList == null) {
                mDictionaryList = MutableLiveData()
                loadDictionaryList()
            }
            return mDictionaryList!!
        }

    private fun loadDictionaryList() {
        Log.d(TAG, "loadDictionaryList() called")

        // do async operation to get dictionary
        val service = Executors.newSingleThreadExecutor()
        service.submit {
            Log.d(TAG, "loadDictionaryList: loading...")
            val list = DictionaryRepository.loadList(getApplication())
            Log.d(TAG, "loadDictionaryList: list loaded.")
            mDictionaryList!!.postValue(list)
        }
    }

    private fun toggleMicIcon(@MicState state: Int) {
        micState.set(state)
    }

    private fun wakeWordTriggered(wakeWord: String) {
        Log.d(TAG, "onPartialResult: matched wake word... toggling icon")
        toggleMicIcon(MicState.LISTENING)
        wakeWordTriggered.set(wakeWord)
    }

    override fun onCleared() {
        shutdownRecognizer()
        instance!!.unregisterListener(this)
        super.onCleared()
    }

    fun setSensitivity(newVal: Int) {
        if (sensitivity.get() == newVal) {
            return
        }
        sensitivity.set(newVal)
        sensitivityAsString.set(sensitivity.toString())
        shutdownRecognizer()
        setup()
    }

    /**
     * Setup the Recognizer with a sensitivity value in the range [1..100]
     * Where 1 means no false alarms but many true matches might be missed.
     * and 100 most of the words will be correctly detected, but you will have many false alarms.
     */
    @Synchronized
    fun setup() {

        // TODO Make sure this isn't called twice unnecessarily
        if (mIsInitialized) {
            Log.w(TAG, "setup() called - already initialized (this is ok if wake word changed)")
        } else {
            Log.d(TAG, "setup() called")
            mIsInitialized = true
        }
        val permissionCheck =
            ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "setup() called - no record audio permission")
            toggleMicIcon(MicState.DISABLED_NO_PERMISSION)
            return
        }

        // Load the wake word from shared prefs
        var wakeWordString = wakeWord.get()
        if (wakeWordString == null || wakeWordString.isEmpty()) {
            // TODO don't default to sami
            wakeWordString = instance!!.getString(
                PrefsManager.KEY_WAKE_WORD,
                getApplication<Application>().getString(R.string.default_wake_word)
            )
            if (wakeWordString == null) {
                // TODO abort?
                Log.e(TAG, "setup: unexpected null wakeWord!")
            }
            wakeWord.set(wakeWordString)
        }
        val keywordThreshold = ("1.e-" + 2 * sensitivity.get()).toFloat()
        Log.d(
            TAG,
            "setup: wake word is: " + wakeWordString + ", sensitivity is: " + keywordThreshold + " [" + sensitivity.get() + "]"
        )
        try {
            if (recognizer != null) {
                // Investigate if seen
                Log.e(
                    TAG,
                    "setup: warning already running: " + recognizer!!.searchName,
                    RuntimeException()
                )
                shutdownRecognizer()
            }
            val assets = Assets(getApplication())
            val assetsDir = assets.syncAssets()
            val speechRecognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(File(assetsDir, "models/en-us-ptm"))
                .setDictionary(File(assetsDir, "models/lm/words.dic"))
                .setKeywordThreshold(keywordThreshold) // Uncomment for a lot of raw logging (takes up a lot of space on the device)
                // .setRawLogDir(assetsDir)
                .recognizer
            speechRecognizer.addKeyphraseSearch(HOT_WORD_SEARCH, wakeWordString)
            speechRecognizer.addListener(mRecognizerListener)
            speechRecognizer.startListening(HOT_WORD_SEARCH)
            recognizer = speechRecognizer
            Log.d(TAG, "setup: listening...")
            toggleMicIcon(MicState.LISTENING)
        } catch (e: IOException) {
            Log.e(TAG, "Caught: $e")
            toggleMicIcon(MicState.DISABLED_NO_PERMISSION)
        }
    }

    @Synchronized
    fun shutdownRecognizer() {
        if (recognizer == null) {
            Log.e(TAG, "shutdownRecognizer: already shut down")
            return
        }
        Log.d(TAG, "shutdownRecognizer() - shutting down...")
        recognizer!!.removeListener(mRecognizerListener)
        val cancelResult = recognizer!!.cancel()
        Log.d(TAG, "shutdownRecognizer() - cancel returned: $cancelResult")
        recognizer!!.stop()
        recognizer!!.shutdown()
        recognizer = null
        toggleMicIcon(MicState.OFF)
        if (wakeWordTriggered.get() != null) {
            wakeWordTriggered.set("")
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == PrefsManager.KEY_WAKE_WORD) {
            val value = instance!!.getString(
                PrefsManager.KEY_WAKE_WORD,
                getApplication<Application>().getString(R.string.default_wake_word)
            )
            Log.d(TAG, "onSharedPreferenceChanged: $key = $value")
            if (TextUtils.equals(wakeWord.get(), value)) {
                Log.d(TAG, "onSharedPreferenceChanged: NO CHANGE")
                return
            }
            wakeWord.set(value)
            shutdownRecognizer()
            setup()
        }
    }

    companion object {
        private const val TAG = "ListenerViewModel"
        private const val HOT_WORD_SEARCH = "HOT_WORD_SEARCH"
        private const val DEFAULT_SENSITIVITY = 3
    }

    init {
        instance!!.registerListener(this)
    }
}