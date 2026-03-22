package com.fcm.nanochat.ui

import androidx.compose.runtime.Composable

@Composable
fun StartupGateContent(
    gemmaTermsAccepted: Boolean?,
    onAccepted: () -> Unit,
    appContent: @Composable () -> Unit
) {
    when (gemmaTermsAccepted) {
        null -> Unit
        false -> GemmaTermsScreen(onAccepted = onAccepted)
        true -> appContent()
    }
}
