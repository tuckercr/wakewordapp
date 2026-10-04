package com.tuckercr.hark.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.IntentCompat
import com.tuckercr.hark.AlertDuration
import com.tuckercr.hark.AlertSettings
import com.tuckercr.hark.AlertSound
import com.tuckercr.hark.DetectionAction
import com.tuckercr.hark.ListenerViewModel
import com.tuckercr.hark.R
import com.tuckercr.hark.ui.theme.HarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sensitivity: Int,
    onSensitivityChanged: (Int) -> Unit,
    detectionAction: DetectionAction,
    onDetectionActionChanged: (DetectionAction) -> Unit,
    alertSettings: AlertSettings,
    onAlertSoundChanged: (AlertSound) -> Unit,
    onAlertDurationChanged: (AlertDuration) -> Unit,
    onPreviewAlert: () -> Unit,
    onStopAlert: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }

    // A test sound must not keep playing after leaving this screen.
    DisposableEffect(Unit) { onDispose { onStopAlert() } }

    val soundPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val picked =
                    result.data?.let {
                        IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                    }
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString()
                onAlertSoundChanged(AlertSound.fromPickedUri(picked?.toString(), defaultUri))
            }
        }

    if (showPicker) {
        AppPickerDialog(
            current = detectionAction,
            onDismiss = { showPicker = false },
            onSelect = {
                onDetectionActionChanged(it)
                showPicker = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_sensitivity_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.settings_sensitivity_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(24.dp))

            var sliderValue by remember(sensitivity) { mutableFloatStateOf(sensitivity.toFloat()) }

            Text(
                text = "${stringResource(R.string.seek_bar_title)} $sensitivity",
                style = MaterialTheme.typography.bodyMedium,
            )

            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onSensitivityChanged(sliderValue.toInt()) },
                valueRange = ListenerViewModel.MIN_SENSITIVITY.toFloat()..ListenerViewModel.MAX_SENSITIVITY.toFloat(),
                steps = ListenerViewModel.MAX_SENSITIVITY - ListenerViewModel.MIN_SENSITIVITY - 1,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(32.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.settings_action_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = when (val a = detectionAction) {
                    is DetectionAction.Default -> stringResource(R.string.settings_action_notification)
                    is DetectionAction.LaunchApp -> stringResource(R.string.settings_action_open_app, a.appName)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedButton(onClick = { showPicker = true }) {
                Text(stringResource(R.string.settings_action_choose))
            }

            Spacer(Modifier.height(32.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.settings_alert_sound_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = alertSoundLabel(alertSettings.sound),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val context = LocalContext.current
                OutlinedButton(onClick = { soundPicker.launch(ringtonePickerIntent(context, alertSettings.sound)) }) {
                    Text(stringResource(R.string.settings_alert_sound_choose))
                }
                OutlinedButton(onClick = onPreviewAlert) {
                    Text(stringResource(R.string.settings_alert_sound_test))
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.settings_alert_duration_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(Modifier.height(4.dp))

            AlertDuration.entries.forEach { duration ->
                DurationRow(
                    label = stringResource(duration.labelRes()),
                    selected = alertSettings.duration == duration,
                    onClick = { onAlertDurationChanged(duration) },
                )
            }
        }
    }
}

@Composable
private fun alertSoundLabel(sound: AlertSound): String {
    val context = LocalContext.current
    return when (sound) {
        is AlertSound.Default -> stringResource(R.string.settings_alert_sound_default)
        is AlertSound.Silent -> stringResource(R.string.settings_alert_sound_silent)
        is AlertSound.Custom ->
            remember(sound.uri) {
                runCatching { RingtoneManager.getRingtone(context, Uri.parse(sound.uri))?.getTitle(context) }.getOrNull()
            } ?: stringResource(R.string.settings_alert_sound_custom)
    }
}

private fun ringtonePickerIntent(
    context: Context,
    current: AlertSound,
): Intent {
    val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    val existing =
        when (current) {
            is AlertSound.Default -> defaultUri
            is AlertSound.Silent -> null
            is AlertSound.Custom -> Uri.parse(current.uri)
        }
    return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.settings_alert_sound_title))
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultUri)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
    }
}

private fun AlertDuration.labelRes(): Int =
    when (this) {
        AlertDuration.ONCE -> R.string.settings_duration_once
        AlertDuration.SECONDS_5 -> R.string.settings_duration_5s
        AlertDuration.SECONDS_10 -> R.string.settings_duration_10s
        AlertDuration.SECONDS_30 -> R.string.settings_duration_30s
        AlertDuration.UNTIL_DISMISSED -> R.string.settings_duration_until_dismissed
    }

@Composable
private fun DurationRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AppPickerDialog(
    current: DetectionAction,
    onDismiss: () -> Unit,
    onSelect: (DetectionAction) -> Unit,
) {
    val context = LocalContext.current
    val apps =
        remember {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

            @Suppress("DEPRECATION")
            val resolved =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    pm.queryIntentActivities(intent, 0)
                }
            resolved
                .map { ri -> ri.activityInfo.packageName to ri.loadLabel(pm).toString() }
                .filter { (pkg, _) -> pkg != context.packageName }
                .sortedBy { (_, name) -> name.lowercase() }
        }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_action_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    item {
                        AppRow(
                            name = stringResource(R.string.settings_action_notification),
                            selected = current is DetectionAction.Default,
                            onClick = { onSelect(DetectionAction.Default) },
                        )
                    }
                    items(apps) { (pkg, name) ->
                        AppRow(
                            name = name,
                            selected = current is DetectionAction.LaunchApp && current.packageName == pkg,
                            onClick = { onSelect(DetectionAction.LaunchApp(pkg, name)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    HarkTheme {
        SettingsScreen(
            sensitivity = 3,
            onSensitivityChanged = {},
            detectionAction = DetectionAction.Default,
            onDetectionActionChanged = {},
            alertSettings = AlertSettings(),
            onAlertSoundChanged = {},
            onAlertDurationChanged = {},
            onPreviewAlert = {},
            onStopAlert = {},
            onBack = {},
        )
    }
}
