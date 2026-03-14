package com.fcm.nanochat.inference

import com.fcm.nanochat.data.ThinkingEffort
import com.fcm.nanochat.model.ChatRole

enum class DownloadedPromptFamily {
    QWEN,
    DEEPSEEK,
    GEMMA,
    PHI,
    GENERIC
}

data class DownloadedPrompt(
        val family: DownloadedPromptFamily,
        val systemInstruction: String,
        val userMessage: String
)

object PromptFormatter {
    private const val DEFAULT_SYSTEM_PROMPT =
            "You are NanoChat, a helpful local assistant. Reply in clean Markdown and keep numbered or bulleted lists on separate lines."

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
            promptFamily: String? = null,
            thinkingEffort: ThinkingEffort = ThinkingEffort.MEDIUM,
            supportsThinking: Boolean = false
    ): String {
        return formatDownloadedPrompt(
                        history = history,
                        prompt = prompt,
                        maxTurns = maxTurns,
                        promptFamily = promptFamily,
                        thinkingEffort = thinkingEffort,
                        supportsThinking = supportsThinking
                )
                .userMessage
    }

    fun formatDownloadedPrompt(
            history: List<ChatTurn>,
            prompt: String,
            maxTurns: Int = 20,
            promptFamily: String? = null,
            thinkingEffort: ThinkingEffort = ThinkingEffort.MEDIUM,
            supportsThinking: Boolean = false
    ): DownloadedPrompt {
        val family = promptFamily.toPromptFamily()
        val systemPrompt =
                applyThinkingInstruction(
                        systemPrompt = DEFAULT_SYSTEM_PROMPT,
                        effort = thinkingEffort,
                        supportsThinking = supportsThinking
                )

        // LiteRT-LM applies the model's native chat template automatically.
        // NanoChat only supplies a normalized user payload and optional system instruction.
        val userMessage = buildTranscriptMessage(history, prompt, maxTurns)

        return DownloadedPrompt(
                family = family,
                systemInstruction = systemPrompt,
                userMessage = userMessage
        )
    }

    private fun buildTranscriptMessage(
            history: List<ChatTurn>,
            prompt: String,
            maxTurns: Int
    ): String {
        val recentTurns = historyWindow(history, maxTurns)
        if (recentTurns.isEmpty()) {
            return prompt.trim()
        }

        return buildString {
                    append("Conversation so far:\n")
                    recentTurns.forEach { turn ->
                        append(if (turn.role == ChatRole.USER) "User: " else "Assistant: ")
                        append(normalizedTurnContent(turn))
                        append("\n")
                    }
                    append("\nLatest user message:\n")
                    append(prompt.trim())
                }
                .trim()
    }

    private fun normalizedTurnContent(turn: ChatTurn): String {
        val raw = turn.content.trim()
        if (turn.role == ChatRole.USER) {
            return raw
        }

        val sanitized = GeneratedTextSanitizer.sanitize(raw, preserveThinkingBlocks = false)
        return sanitized.ifBlank { raw }
    }

    private fun String?.toPromptFamily(): DownloadedPromptFamily {
        val normalized = this?.trim()?.lowercase().orEmpty()
        return when {
            normalized.contains("deepseek") -> DownloadedPromptFamily.DEEPSEEK
            normalized.contains("qwen") -> DownloadedPromptFamily.QWEN
            normalized.contains("gemma") -> DownloadedPromptFamily.GEMMA
            normalized.contains("phi") -> DownloadedPromptFamily.PHI
            normalized.isBlank() -> DownloadedPromptFamily.GENERIC
            else -> DownloadedPromptFamily.GENERIC
        }
    }

    internal fun applyThinkingInstruction(
            systemPrompt: String,
            effort: ThinkingEffort,
            supportsThinking: Boolean
    ): String {
        if (!supportsThinking) return systemPrompt

        val suffix =
                when (effort) {
                    ThinkingEffort.NONE -> {
                        " Do not include a <think> block or hidden reasoning in the response."
                    }
                    ThinkingEffort.LOW -> {
                        " If reasoning helps, keep it brief and place it inside a short <think>...</think> block before the final answer."
                    }
                    ThinkingEffort.MEDIUM -> {
                        " When reasoning helps, place it inside a <think>...</think> block before the final answer."
                    }
                    ThinkingEffort.HIGH -> {
                        " When reasoning helps, place a detailed <think>...</think> block before the final answer."
                    }
                }
        return (systemPrompt + suffix).trim()
    }
}
