package com.fcm.nanochat.inference

data class ReasoningContent(
    val thinking: String?,
    val visible: String
)

object ReasoningContentParser {
    private val thinkBlockRegex = Regex("(?is)<think>(.*?)(?:</think>|$)")

    fun split(content: String): ReasoningContent {
        if (content.isBlank()) return ReasoningContent(null, "")

        if (!content.contains("<think>", ignoreCase = true)) {
            return ReasoningContent(null, GeneratedTextSanitizer.sanitize(content, false).trim())
        }

        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        val thinkingParts = mutableListOf<String>()
        val visibleBuilder = StringBuilder()
        var cursor = 0

        thinkBlockRegex.findAll(normalized).forEach { match ->
            visibleBuilder.append(normalized.substring(cursor, match.range.first))
            val reasoning =
                GeneratedTextSanitizer.sanitize(match.groups[1]?.value.orEmpty(), false).trim()
            if (reasoning.isNotBlank()) thinkingParts += reasoning
            cursor = match.range.last + 1
        }

        visibleBuilder.append(normalized.substring(cursor))
        val visible = GeneratedTextSanitizer.sanitize(visibleBuilder.toString(), false).trim()

        return ReasoningContent(
            thinkingParts.joinToString("\n\n").ifBlank { null },
            visible
        )
    }
}
