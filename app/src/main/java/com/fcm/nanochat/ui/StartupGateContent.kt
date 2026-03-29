package com.fcm.nanochat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.fcm.nanochat.model.ModelCardUi

@Composable
fun StartupGateContent(
    gemmaTermsAccepted: Boolean?,
    onboardingDownloadPromptSeen: Boolean?,
    isModelLibraryLoaded: Boolean,
    hasInstalledModels: Boolean,
    onboardingModel: ModelCardUi?,
    onAccepted: () -> Unit,
    onContinueOnboarding: () -> Unit,
    onOpenModelManagement: () -> Unit,
    onDismissOnboarding: () -> Unit,
    appContent: @Composable () -> Unit
) {
    when {
        gemmaTermsAccepted == null || onboardingDownloadPromptSeen == null -> Unit
        !gemmaTermsAccepted -> GemmaTermsScreen(onAccepted = onAccepted)
        !onboardingDownloadPromptSeen && !isModelLibraryLoaded -> Unit
        !onboardingDownloadPromptSeen && hasInstalledModels -> {
            LaunchedEffect(Unit) {
                onDismissOnboarding()
            }
            appContent()
        }

        !onboardingDownloadPromptSeen ->
            OnboardingDownloadScreen(
                model = onboardingModel,
                onContinue = onContinueOnboarding,
                onOpenModelManagement = onOpenModelManagement,
                onDismiss = onDismissOnboarding
            )

        else -> appContent()
    }
}
