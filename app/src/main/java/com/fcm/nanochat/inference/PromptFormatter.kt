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
        maxTurns: Int = 20,
        modelId: String? = null
    ): String {
        val normalizedModelId = modelId?.trim()?.lowercase().orEmpty()
        if (usesSpeakerPrompt(normalizedModelId)) {
            return flattenForInstructionTunedModel(history, prompt, maxTurns)
        }

        val recentTurns = historyWindow(history, maxTurns)
        val conversation = buildString {
            recentTurns.forEach { turn ->
                val tag = if (turn.role == ChatRole.USER) USER_TAG else ASSISTANT_TAG
                append(tag)
                append('\n')
                append(normalizedTurnContent(turn))
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

    private fun flattenForInstructionTunedModel(
        history: List<ChatTurn>,
        prompt: String,
        maxTurns: Int
    ): String {
        val recentTurns = historyWindow(history, maxTurns)
        return buildString {
            append("You are NanoChat, a concise and helpful local assistant.")
            append('\n')
            recentTurns.forEach { turn ->
                append(if (turn.role == ChatRole.USER) "User: " else "Assistant: ")
                append(normalizedTurnContent(turn))
                append('\n')
            }
            append("User: ")
            append(prompt.trim())
            append('\n')
            append("Assistant:")
        }.trim()
    }

    private fun usesSpeakerPrompt(normalizedModelId: String): Boolean {
        if (normalizedModelId.isBlank()) return false
        return "deepseek" in normalizedModelId || "qwen" in normalizedModelId
    }

    private fun normalizedTurnContent(turn: ChatTurn): String {
        val raw = turn.content.trim()
        if (turn.role == ChatRole.USER) {
            return raw
        }

        val sanitized = GeneratedTextSanitizer.sanitize(raw)
        return sanitized.ifBlank { raw }
    }
}
