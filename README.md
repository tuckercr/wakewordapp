# Hark

![Android CI](https://github.com/tuckercr/wakewordapp/actions/workflows/android.yml/badge.svg)
![ktlint](https://github.com/tuckercr/wakewordapp/actions/workflows/ktlint.yml/badge.svg)

Offline wake-word detection on Android — no network call ever leaves the device. Say a configured word and Hark fires a heads-up notification immediately, even with the screen off. [PocketSphinx](https://cmusphinx.github.io/) runs the CMU acoustic model entirely in-process, so detection works in airplane mode or anywhere else connectivity is unavailable.

The original use case was accessibility: alerting a hearing-impaired user the moment someone nearby says their name. I revisited the project to modernise the architecture, sharpen the implementation, and bring the code up to a standard I'd be comfortable shipping.

---

## Screenshots

<table>
<tr>
<td><img src="screenshots/main.png" width="180" alt="Main screen"/></td>
<td><img src="screenshots/triggered.png" width="180" alt="Wake word detected"/></td>
<td><img src="screenshots/fg_service.png" width="180" alt="Background service notification"/></td>
</tr>
<tr>
<td align="center">Main screen</td>
<td align="center">Wake word detected</td>
<td align="center">Background notification</td>
</tr>
</table>

---

## How the on-device recognition works

PocketSphinx bundles a pre-trained acoustic model (CMU US English, PTM variant) and a pronunciation dictionary of ~130k words. At runtime, Hark:

1. Syncs the bundled model assets to the app's files directory on first launch.
2. Configures a `SpeechRecognizer` with a keyphrase search and a sensitivity threshold (`1e-2×sensitivity`).
3. Starts continuous listening on the recogniser. Partial results stream in on every audio frame; when a partial result contains the target word, detection fires immediately without waiting for an utterance boundary.
4. On detection, the recogniser stops, clears its hypothesis buffer, and restarts — ready for the next trigger.

No audio ever leaves the device. The microphone feed is consumed entirely by PocketSphinx running in-process.

---

## Architecture

- **MVVM** — `ListenerViewModel` exposes a single `StateFlow<ListenerUiState>`. The Activity and Compose screens observe it; they never touch the recogniser directly.
- **Listener engine and foreground service** — `ListenerEngine` owns the recogniser and exposes its state as a `StateFlow`. `ListenerService` is a microphone foreground service that runs it, so listening continues with no Activity (for example after Android destroys it in the background) and the "listening" notification is never shown while nothing is listening. Android 14+ requires microphone foreground services to be started while the app is in the foreground, so the Activity starts the service from `onResume`, then it keeps running in the background.
- **After a reboot** — Android 11+ will not give a microphone service started from the background access to the microphone (and Android 15+ throws for `BOOT_COMPLETED`). So `BootReceiver` posts a "tap to start listening" notification, and tapping it opens the app and resumes listening from the foreground. Android 10 and earlier start the listener directly.
- **Hilt** — `ListenerEngine`, `ChimePlayer`, and `DictionaryRepository` are injected; `PreferencesManager` wraps a `DataStore<Preferences>` injected through `AppModule`.
- **DataStore** — wake word, sensitivity, detection action, and onboarding state survive process death.
- **Permission recovery** — if the user sets microphone permission to "Ask Every Time" and force-closes the app, `onResume` re-requests the permission once per Activity session using `ActivityResultContracts.RequestMultiplePermissions`, with a flag to prevent a loop when the dialog dismissal triggers another `onResume`.

---

## Tech Stack

| | |
|---|---|
| **Language** | Kotlin |
| **UI** | Jetpack Compose + Material 3 |
| **Architecture** | MVVM, `ViewModel` + `StateFlow` |
| **DI** | Hilt |
| **Persistence** | DataStore |
| **Background** | Foreground `Service`, microphone type |
| **Voice recognition** | PocketSphinx (on-device, no internet) |
| **CI** | GitHub Actions — lint, unit tests, debug APK |

---

## Testing

149 unit tests across 12 classes, all running on the JVM without Robolectric:

- **`ListenerViewModelTest`** (37) — mirroring the engine state into the UI, permission handling, sensitivity, alert settings, phrase normalization, and delegation to the engine
- **`ListenerEngineTest`** (24) — all `RecognitionListener` callbacks, detection and the re-arm cooldown, mute, and the start/stop lifecycle
- **`PreferencesManagerTest`** (21) — real `DataStore` backed by a temp file; covers read, write, overwrite, and clear for wake word, onboarding, sensitivity, alert sound and duration, and the detection action
- **`WakePhraseTest`** (15) — phrase normalization, per-word dictionary validation, the two-word limit, completions, and "did you mean" suggestions
- **`ListenerUiStateTest`** (11) — data class semantics and copy behaviour
- **`KeywordTuningTest`** (9) — the sensitivity table: strictness ordering, clamping, and the shift for two-word phrases
- **`AlertSettingsTest`** (8) — alert sound picking, duration limits, and the 5 second default
- **`NotificationUtilsTest`** (6) — vibration pattern, notification IDs
- **`MainActivityTest`** (5) — when the listener service should start (wake word known, permissions granted, not muted)
- **`BootReceiverTest`** (8) — after a reboot: a resume notification on Android 11+ (including the 14+ case), a direct start before that with a fallback if Android refuses, and the default wake word
- **`BrandColorsTest`** (3) — fails if hex colors appear in drawables or Kotlin outside `colors.xml`, so the palette stays in one place
- **`MicStateTest`** (2) — enum completeness

Key testing choices:

- The recogniser itself needs the native PocketSphinx library, so `ListenerEngine` is tested around it: callbacks are invoked directly through the `internal` `recognitionListener`, and the start/stop lifecycle is exercised without a microphone permission.
- `ContextCompat.checkSelfPermission` routes through `context.checkPermission(permission, pid, uid)` on the JVM (SDK\_INT = 0 path). Stubbing `checkPermission` on the mock `Application` gives full permission control without `mockkStatic`.
- `returnDefaultValues = true` makes Android stub methods return 0/null instead of throwing, so coroutines run to completion.
- `shouldStartListenerService`, `WakePhrase`, `KeywordTuning`, and `BootReceiver.startIfWakeWordConfigured` are pure or `internal` so tests call them directly instead of going through Android components.

CI runs `./gradlew lint test build` and `./gradlew ktlintCheck` on every push and pull request.

---

## Permissions

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Microphone input for wake-word detection |
| `POST_NOTIFICATIONS` | Heads-up alert when the word is heard |
| `FOREGROUND_SERVICE` | Run the listener as a foreground service |
| `FOREGROUND_SERVICE_MICROPHONE` | Keep the listener alive in the background |
| `VIBRATE` | Vibrate when the word is heard |
| `RECEIVE_BOOT_COMPLETED` | Restart the listener after the device reboots |
