package com.fcm.nanochat.inference

import com.fcm.nanochat.model.ChatRole

object PromptFormatter {
    private const val USER_TAG = "<|user|>"
    private const val ASSISTANT_TAG = "<|assistant|>"

    fun historyWindow(history: List<ChatTurn>, maxTurns: Int): List<ChatTurn> {
        if (history.size <= maxTurns) return history
        return history.takeLast(maxTurns)
    }

    fun flattenForAicore(history: List<ChatTurn>, prompt: String, maxTurns: Int = 10): String {
        val recentTurns = historyWindow(history, maxTurns)
        val conversation = buildString {
            append("You are NanoChat running on Gemini Nano.\n")
            recentTurns.forEach { turn ->
                val speaker = if (turn.role == ChatRole.USER) "User" else "Assistant"
                append(speaker)
                append(": ")
                append(turn.content.trim())
                append('\n')
            }
            append("User: ")
            append(prompt.trim())
            append('\n')
            append("Assistant:")
        }
        return conversation.trim()
    }

    fun flattenForDownloadedModel(
        history: List<ChatTurn>,
        prompt: String,
        maxTurns: Int = 20
    ): String {
        val recentTurns = historyWindow(history, maxTurns)
        val conversation = buildString {
            recentTurns.forEach { turn ->
                val tag = if (turn.role == ChatRole.USER) USER_TAG else ASSISTANT_TAG
                append(tag)
                append('\n')
                append(turn.content.trim())
                append('\n')
            }
            append(USER_TAG)
            append('\n')
            append(prompt.trim())
            append('\n')
            append(ASSISTANT_TAG)
            append('\n')
        }
        return conversation.trimEnd()
    }
}
