package com.tuckercr.zamzam.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tuckercr.zamzam.ListenerUiState
import com.tuckercr.zamzam.MicState
import com.tuckercr.zamzam.R
import com.tuckercr.zamzam.ui.theme.ZamZamTheme

@Composable
fun ListenerScreen(
    uiState: ListenerUiState,
    onWakeWordSelected: (String) -> Unit,
    onSettingsClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = onSettingsClicked,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                )
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(2f))

                MicStateImage(
                    micState = uiState.micState,
                    modifier = Modifier.size(128.dp),
                )

                Spacer(Modifier.height(24.dp))

                WakeWordDropdown(
                    currentWord = uiState.wakeWord,
                    words = uiState.dictionaryWords,
                    onWordSelected = onWakeWordSelected,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.weight(3f))
            }
        }
    }
}

@Composable
private fun MicStateImage(
    micState: MicState,
    modifier: Modifier = Modifier,
) {
    val drawableRes =
        when (micState) {
            MicState.DISABLED_NO_PERMISSION -> R.drawable.ic_mic_off_red_128dp
            MicState.OFF -> R.drawable.ic_mic_light_gray_128dp
            MicState.LISTENING -> R.drawable.ic_mic_gray_128dp
            MicState.SPEAKING -> R.drawable.ic_mic_green_128dp
        }
    Image(
        painter = painterResource(drawableRes),
        contentDescription = null,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WakeWordDropdown(
    currentWord: String,
    words: List<String>,
    onWordSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember(currentWord) { mutableStateOf(currentWord) }
    val filtered =
        remember(words, query) {
            if (query.isBlank()) {
                words
            } else {
                val lower = query.lowercase()
                words
                    .filter { it.contains(lower, ignoreCase = true) }
                    .sortedWith(compareBy { !it.lowercase().startsWith(lower) })
            }
        }

    Box(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    expanded = true
                },
                label = { Text("Wake word") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)

            )

            if (filtered.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    filtered.take(100).forEach { word ->
                        DropdownMenuItem(
                            text = { Text(word) },
                            onClick = {
                                onWordSelected(word)
                                query = word
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ListenerScreenPreview() {
    ZamZamTheme {
        ListenerScreen(
            uiState =
                ListenerUiState(
                    micState = MicState.LISTENING,
                    wakeWord = "Hotword",
                    dictionaryWords = listOf("Hotword", "Example", "Test"),
                ),
            onWakeWordSelected = {},
            onSettingsClicked = {},
        )
    }
}
