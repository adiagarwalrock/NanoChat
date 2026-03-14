package com.fcm.nanochat.inference

object GeneratedTextSanitizer {
    private val controlLineRegex = Regex(
        pattern = """(?i)^\s*(<\|assistant\|>|<\|user\|>|<\|system\|>|<\|im_start\|>|<\|im_end\|>|<\|eot_id\|>|<\|endoftext\|>|<assistant>|</assistant>|<user>|</user>|assistant\s*:|###\s*assistant\s*:|\[assistant])\s*$"""
    )
    private val leadingAssistantPrefixRegex = Regex(
        pattern = """(?i)^\s*((<\|assistant\|>|<assistant>|assistant\s*:|###\s*assistant\s*:|\[assistant])\s*)+"""
    )
    private val inlineControlTokenRegex = Regex(
        pattern = """(?i)\s*(<\|assistant\|>|<\|user\|>|<\|system\|>|<\|im_start\|>|<\|im_end\|>|<\|eot_id\|>|<\|endoftext\|>|<\|begin_of_text\|>|<s>|</s>|<assistant>|</assistant>|<user>|</user>)\s*"""
    )
    private val standaloneRoleLineRegex = Regex(
        pattern = """(?im)^\s*(assistant|user|system)\s*$"""
    )
    private val completeThinkBlockRegex = Regex(
        pattern = """(?is)<think>.*?</think>"""
    )

    fun sanitize(raw: String): String {
        if (raw.isBlank()) return ""

        val normalizedInput = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        val withoutThoughtTags = stripThinkingContent(normalizedInput)

        val trimmedLines = withoutThoughtTags
            .lines()
            .dropWhile { line ->
                val content = line.trim()
                content.isBlank() || controlLineRegex.matches(content)
            }

        var normalized = buildString {
            trimmedLines.forEach { line ->
                append(line)
                append('\n')
            }
        }.trimEnd()

        repeat(4) {
            val updated = normalized.replaceFirst(leadingAssistantPrefixRegex, "")
            if (updated == normalized) return@repeat
            normalized = updated
        }

        return normalized
            .replace(inlineControlTokenRegex, " ")
            .replace(standaloneRoleLineRegex, "")
            .replace(Regex("(?i)^\\s*assistant\\s*:\\s*"), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .replace(Regex("[ \\t]{2,}"), " ")
            .trim()
    }

    private fun stripThinkingContent(raw: String): String {
        var result = raw.replace(completeThinkBlockRegex, " ")

        val openThinkIndex = result.lastIndexOf("<think>", ignoreCase = true)
        if (openThinkIndex >= 0) {
            val closeThinkIndex = result.indexOf("</think>", startIndex = openThinkIndex)
            if (closeThinkIndex == -1) {
                result = result.substring(0, openThinkIndex)
            }
        }

        return result
    }
}
