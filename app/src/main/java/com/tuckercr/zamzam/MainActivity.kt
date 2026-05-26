package com.tuckercr.zamzam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tuckercr.zamzam.ui.screens.ListenerScreen
import com.tuckercr.zamzam.ui.screens.OnboardingScreen
import com.tuckercr.zamzam.ui.screens.SettingsScreen
import com.tuckercr.zamzam.ui.screens.WakeWordDetectedScreen
import com.tuckercr.zamzam.ui.theme.ZamZamTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: ListenerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val openHotWordDetected = intent?.getBooleanExtra(EXTRA_OPEN_HOT_WORD_DETECTED, false) == true

        setContent {
            ZamZamTheme {
                ZamZamApp(
                    viewModel = viewModel,
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

    override fun onDestroy() {
        startService(ListenerService.createStopForegroundIntent(this))
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN_HOT_WORD_DETECTED = "open_hw_detected"
    }
}

@Composable
private fun ZamZamApp(
    viewModel: ListenerViewModel,
    startOnDetectedScreen: Boolean,
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val onboardingComplete by viewModel.onboardingCompleteFlow.collectAsStateWithLifecycle()

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
                    viewModel.completeOnboarding()
                    viewModel.setup()
                    navController.navigate("listener") {
                        popUpTo("onboarding") { inclusive = true }
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
                    viewModel.clearWakeWordTriggered()
                    navController.popBackStack()
                    viewModel.setup()
                },
            )
        }
    }
}
