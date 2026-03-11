package com.fcm.nanochat.inference

object InferenceClientSelector {
    fun select(
        mode: InferenceMode,
        local: InferenceClient,
        remote: InferenceClient
    ): InferenceClient {
        return when (mode) {
            InferenceMode.AICORE -> local
            InferenceMode.REMOTE -> remote
        }
    }
}
