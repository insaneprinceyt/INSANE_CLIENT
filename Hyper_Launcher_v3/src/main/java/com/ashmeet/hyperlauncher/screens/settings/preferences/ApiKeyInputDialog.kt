package com.ashmeet.hyperlauncher.screens.settings.preferences

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.ashmeet.hyperlauncher.components.HyperAlertDialog
import com.ashmeet.hyperlauncher.components.HyperOutlinedTextField
import com.ashmeet.hyperlauncher.utils.translation.translatedText

/** API keys stay masked while editing and are never included in the setting summary. */
@Composable
fun ApiKeyInputDialog(
    title: String,
    description: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    HyperAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(translatedText(title)) },
        text = {
            HyperOutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(translatedText("API key")) },
                supportingText = { Text(translatedText(description)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                singleLine = true
            )
        },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { onConfirm(text) },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = onDismiss
    )
}
