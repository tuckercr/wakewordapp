package com.tuckercr.zamzam

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tuckercr.zamzam.prefs.PreferencesManager
import com.tuckercr.zamzam.ui.screens.ListenerScreen
import com.tuckercr.zamzam.ui.screens.OnboardingScreen
import com.tuckercr.zamzam.ui.screens.SettingsScreen
import com.tuckercr.zamzam.ui.screens.WakeWordDetectedScreen
import com.tuckercr.zamzam.ui.theme.ZamZamTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var chimePlayer: ChimePlayer
    private val viewModel: ListenerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val openHotWordDetected = intent?.getBooleanExtra(EXTRA_OPEN_HOT_WORD_DETECTED, false) == true

        setContent {
            ZamZamTheme {
                ZamZamApp(
                    viewModel = viewModel,
                    chimePlayer = chimePlayer,
                    startOnDetectedScreen = openHotWordDetected,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermissions()
        viewModel.setup()
        // Must start the microphone FGS while the activity is resumed — Android 14+ blocks
        // startForeground(type=microphone) unless the process is in PROCESS_STATE_TOP.
        val wakeWord = viewModel.uiState.value.wakeWord
        val hasPermission =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        if (wakeWord.isNotBlank() && hasPermission) {
            startForegroundService(ListenerService.createStartForegroundIntent(this, wakeWord))
        }
    }

    override fun onPause() {
        super.onPause()
        // Foreground service keeps running — it was started in onResume() while foreground
    }

    override fun onDestroy() {
        startService(ListenerService.createStopForegroundIntent(this))
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN_HOT_WORD_DETECTED = "open_hw_detected"
    }
}

@Suppress("DEPRECATION")
private fun vibrateForWakeWord(context: Context) {
    val pattern = NotificationUtils.VIBRATION_PATTERN
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(VibratorManager::class.java)
        vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    } else {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(pattern, -1)
    }
}

private fun postHotWordNotification(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    NotificationUtils.initChannels(context)
    nm.notify(NotificationUtils.NOTIFICATION_ID_HOT_WORD, NotificationUtils.createHotWordNotification(context))
}

@Composable
private fun ZamZamApp(
    viewModel: ListenerViewModel,
    chimePlayer: ChimePlayer,
    startOnDetectedScreen: Boolean,
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesManager = remember { PreferencesManager(context) }

    val onboardingComplete by preferencesManager.onboardingCompleteFlow
        .collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(Unit) {
        viewModel.chimeEvent.collect {
            chimePlayer.play()
            vibrateForWakeWord(context)
            postHotWordNotification(context)
        }
    }

    val wakeWordTriggered = uiState.wakeWordTriggered
    LaunchedEffect(wakeWordTriggered) {
        if (wakeWordTriggered != null) {
            navController.navigate("detected") { launchSingleTop = true }
        }
    }

    // Wait for initial value from DataStore
    if (onboardingComplete == null) return

    val startDestination =
        when {
            startOnDetectedScreen -> "detected"
            onboardingComplete == false -> "onboarding"
            else -> "listener"
        }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("onboarding") {
            OnboardingScreen(
                onComplete = {
                    scope.launch {
                        preferencesManager.setOnboardingComplete(true)
                        viewModel.setup()
                        navController.navigate("listener") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                },
            )
        }
        composable("listener") {
            ListenerScreen(
                uiState = uiState,
                onWakeWordSelected = viewModel::setWakeWord,
                onSettingsClicked = { navController.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(
                sensitivity = uiState.sensitivity,
                onSensitivityChanged = viewModel::setSensitivity,
                onBack = { navController.popBackStack() },
            )
        }
        composable("detected") {
            WakeWordDetectedScreen(
                onDismiss = {
                    chimePlayer.stop()
                    viewModel.clearWakeWordTriggered()
                    navController.popBackStack()
                    viewModel.setup()
                },
            )
        }
    }
}
