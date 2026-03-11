package com.fcm.nanochat.inference

object RemoteApiUrlResolver {
    private const val CHAT_COMPLETIONS_SUFFIX = "chat/completions"

    fun chatCompletionsUrl(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return if (normalized.endsWith(CHAT_COMPLETIONS_SUFFIX)) {
            normalized
        } else {
            "$normalized/$CHAT_COMPLETIONS_SUFFIX"
        }
    }
}