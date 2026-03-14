package com.fcm.nanochat.inference

import com.fcm.nanochat.model.ChatRole

enum class DownloadedPromptFamily {
    QWEN,
    DEEPSEEK,
    GEMMA,
    GENERIC
}

data class DownloadedPrompt(
    val family: DownloadedPromptFamily,
    val systemInstruction: String,
    val userMessage: String
)

object PromptFormatter {
    private const val DEFAULT_SYSTEM_PROMPT =
        "You are NanoChat, a concise and helpful local assistant."
    private const val DEEPSEEK_SYSTEM_PROMPT =
        "You are NanoChat. Be concise, and show reasoning only when needed."
    private const val GEMMA_SYSTEM_PROMPT =
        "You are NanoChat running locally on Gemma. Keep answers clear and actionable."

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
        return formatDownloadedPrompt(
            history = history,
            prompt = prompt,
            maxTurns = maxTurns,
            modelId = modelId
        ).userMessage
    }

    fun formatDownloadedPrompt(
        history: List<ChatTurn>,
        prompt: String,
        maxTurns: Int = 20,
        modelId: String? = null
    ): DownloadedPrompt {
        val family = detectDownloadedPromptFamily(modelId)
        val systemPrompt = when (family) {
            DownloadedPromptFamily.DEEPSEEK -> DEEPSEEK_SYSTEM_PROMPT
            DownloadedPromptFamily.GEMMA -> GEMMA_SYSTEM_PROMPT
            DownloadedPromptFamily.QWEN,
            DownloadedPromptFamily.GENERIC -> DEFAULT_SYSTEM_PROMPT
        }

        val userMessage = when (family) {
            DownloadedPromptFamily.QWEN, DownloadedPromptFamily.DEEPSEEK ->
                buildQwenUserMessage(prompt)

            DownloadedPromptFamily.GEMMA, DownloadedPromptFamily.GENERIC ->
                buildSpeakerContextMessage(
                    history = history,
                    prompt = prompt,
                    maxTurns = maxTurns
                )
        }

        return DownloadedPrompt(
            family = family,
            systemInstruction = systemPrompt,
            userMessage = userMessage
        )
    }

    fun detectDownloadedPromptFamily(modelId: String?): DownloadedPromptFamily {
        val normalizedModelId = modelId?.trim()?.lowercase().orEmpty()
        if (normalizedModelId.isBlank()) {
            return DownloadedPromptFamily.GENERIC
        }

        return when {
            "deepseek" in normalizedModelId -> DownloadedPromptFamily.DEEPSEEK
            "qwen" in normalizedModelId -> DownloadedPromptFamily.QWEN
            "gemma" in normalizedModelId -> DownloadedPromptFamily.GEMMA
            else -> DownloadedPromptFamily.GENERIC
        }
    }

    private fun buildQwenUserMessage(prompt: String): String {
        return prompt.trim()
    }

    private fun buildSpeakerContextMessage(
        history: List<ChatTurn>,
        prompt: String,
        maxTurns: Int
    ): String {
        val recentTurns = historyWindow(history, maxTurns)
        return buildString {
            recentTurns.forEach { turn ->
                append(if (turn.role == ChatRole.USER) "User: " else "Assistant: ")
                append(normalizedTurnContent(turn))
                append('\n')
            }
            append("User: ")
            append(prompt.trim())
        }.trim()
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
