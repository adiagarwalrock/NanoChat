package com.fcm.nanochat.inference

object RemoteApiUrlResolver {
    private const val CHAT_COMPLETIONS_SUFFIX = "chat/completions"
    private const val AUDIO_TRANSCRIPTIONS_SUFFIX = "audio/transcriptions"

    fun chatCompletionsUrl(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return if (normalized.endsWith(CHAT_COMPLETIONS_SUFFIX)) {
            normalized
        } else {
            "$normalized/$CHAT_COMPLETIONS_SUFFIX"
        }
    }

    fun audioTranscriptionsUrl(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return if (normalized.endsWith(AUDIO_TRANSCRIPTIONS_SUFFIX)) {
            normalized
        } else {
            "$normalized/$AUDIO_TRANSCRIPTIONS_SUFFIX"
        }
    }
}
