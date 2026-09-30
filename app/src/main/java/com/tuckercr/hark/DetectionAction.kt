package com.tuckercr.hark

sealed class DetectionAction {
    /** Tap notification → open Hark's detected screen (default). */
    data object Default : DetectionAction()

    /** Tap notification → launch a specific installed app. */
    data class LaunchApp(val packageName: String, val appName: String) : DetectionAction()
}
