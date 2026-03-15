package com.fcm.nanochat.inference

data class ReasoningContent(
    val thinking: String?,
    val visible: String
)

object ReasoningContentParser {
    private val thinkBlockRegex = Regex("(?is)<think>(.*?)(?:</think>|$)")

    fun split(content: String): ReasoningContent {
        if (content.isBlank()) {
            return ReasoningContent(thinking = null, visible = "")
        }

        val normalized = content
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val thinkingParts = mutableListOf<String>()
        val visibleBuilder = StringBuilder()
        var cursor = 0

        thinkBlockRegex.findAll(normalized).forEach { match ->
            visibleBuilder.append(normalized.substring(cursor, match.range.first))

            val reasoning = match.groups[1]
                ?.value
                .orEmpty()
                .let { GeneratedTextSanitizer.sanitize(it, preserveThinkingBlocks = false).trim() }
            if (reasoning.isNotBlank()) {
                thinkingParts += reasoning
            }

            cursor = match.range.last + 1
        }

        if (cursor == 0) {
            return ReasoningContent(
                thinking = null,
                visible = GeneratedTextSanitizer.sanitize(normalized).trim()
            )
        }

        visibleBuilder.append(normalized.substring(cursor))
        val visible = GeneratedTextSanitizer.sanitize(visibleBuilder.toString(), preserveThinkingBlocks = false)
            .trim()

        return ReasoningContent(
            thinking = thinkingParts.joinToString(separator = "\n\n").ifBlank { null },
            visible = visible
        )
    }
}
