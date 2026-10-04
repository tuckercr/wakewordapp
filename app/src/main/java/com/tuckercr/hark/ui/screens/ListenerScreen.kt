package com.tuckercr.hark.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tuckercr.hark.ListenerUiState
import com.tuckercr.hark.MicState
import com.tuckercr.hark.R
import com.tuckercr.hark.WakePhrase
import com.tuckercr.hark.ui.theme.HarkTheme

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
        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
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
    val colors = MaterialTheme.colorScheme
    val (drawableRes, tint) =
        when (micState) {
            MicState.DISABLED_NO_PERMISSION -> R.drawable.ic_mic_off_128dp to colors.error
            MicState.OFF -> R.drawable.ic_mic_128dp to colors.onSurfaceVariant.copy(alpha = 0.35f)
            MicState.LISTENING -> R.drawable.ic_mic_128dp to colors.onSurfaceVariant
            MicState.SPEAKING -> R.drawable.ic_mic_128dp to colors.primary
        }
    Image(
        painter = painterResource(drawableRes),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
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
    val focusManager = LocalFocusManager.current
    val dictionary = remember(words) { words.toHashSet() }
    val dictionaryReady = words.isNotEmpty()
    val validation = remember(query, dictionary) { WakePhrase.validate(query, dictionary) }
    val completions = remember(words, query) { WakePhrase.completions(query, words) }

    fun commitIfValid() {
        val valid = validation as? WakePhrase.Validation.Valid ?: return
        if (valid.phrase != currentWord) onWordSelected(valid.phrase)
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
                label = { Text(stringResource(R.string.wake_word_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                isError =
                    dictionaryReady &&
                        (
                            validation is WakePhrase.Validation.UnknownWords ||
                                validation is WakePhrase.Validation.TooManyWords
                        ),
                supportingText = {
                    Text(
                        wakeWordSupportingText(
                            validation = validation,
                            currentWord = currentWord,
                            dictionaryReady = dictionaryReady,
                            words = words,
                        ),
                    )
                },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            commitIfValid()
                            expanded = false
                            focusManager.clearFocus()
                        },
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) commitIfValid() }
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            )

            if (completions.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    completions.forEach { word ->
                        DropdownMenuItem(
                            text = { Text(word) },
                            onClick = {
                                val updated = WakePhrase.replaceLastWord(query, word)
                                query = updated
                                expanded = false
                                (WakePhrase.validate(updated, dictionary) as? WakePhrase.Validation.Valid)
                                    ?.let { if (it.phrase != currentWord) onWordSelected(it.phrase) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun wakeWordSupportingText(
    validation: WakePhrase.Validation,
    currentWord: String,
    dictionaryReady: Boolean,
    words: List<String>,
): String =
    when {
        !dictionaryReady -> stringResource(R.string.wake_word_hint)
        validation is WakePhrase.Validation.Empty -> stringResource(R.string.wake_word_hint)
        validation is WakePhrase.Validation.TooManyWords ->
            stringResource(R.string.wake_word_too_many, WakePhrase.MAX_WORDS, currentWord)
        validation is WakePhrase.Validation.UnknownWords -> {
            val unknown = validation.words.joinToString(", ") { "\u201c$it\u201d" }
            val suggestions =
                remember(validation, words) {
                    validation.words
                        .flatMap { WakePhrase.suggestionsFor(it, words) }
                        .distinct()
                        .take(3)
                }
            if (suggestions.isEmpty()) {
                stringResource(R.string.wake_word_unknown, unknown, currentWord)
            } else {
                stringResource(
                    R.string.wake_word_unknown_suggest,
                    unknown,
                    suggestions.joinToString(", "),
                    currentWord,
                )
            }
        }
        validation is WakePhrase.Validation.Valid && validation.phrase == currentWord ->
            stringResource(R.string.wake_word_listening, currentWord)
        validation is WakePhrase.Validation.Valid ->
            stringResource(R.string.wake_word_press_done, validation.phrase)
        else -> ""
    }

@Preview(showBackground = true)
@Composable
private fun ListenerScreenPreview() {
    HarkTheme {
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
