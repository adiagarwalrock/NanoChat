package com.fcm.nanochat.inference

object GeneratedTextSanitizer {
    private const val MAX_ASSISTANT_PREFIX_STRIPS = 4
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
    private val leadingAssistantLabelRegex = Regex("(?i)^\\s*assistant\\s*:\\s*")
    private val excessiveNewlineRegex = Regex("\\n{3,}")
    private val repeatedHorizontalWhitespaceRegex = Regex("[ \\t]{2,}")

    fun sanitize(raw: String): String {
        if (raw.isBlank()) return ""

        val normalizedInput = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        val withoutThoughtTags = stripThinkingContent(normalizedInput)

        var normalized = withoutThoughtTags
            .lines()
            .dropWhile { line ->
                val content = line.trim()
                content.isBlank() || controlLineRegex.matches(content)
            }
            .joinToString(separator = "\n")
            .trimEnd()

        var stripCount = 0
        while (stripCount < MAX_ASSISTANT_PREFIX_STRIPS) {
            val updated = normalized.replaceFirst(leadingAssistantPrefixRegex, "")
            if (updated == normalized) break
            normalized = updated
            stripCount += 1
        }

        return normalized
            .replace(inlineControlTokenRegex, " ")
            .replace(standaloneRoleLineRegex, "")
            .replace(leadingAssistantLabelRegex, "")
            .replace(excessiveNewlineRegex, "\n\n")
            .replace(repeatedHorizontalWhitespaceRegex, " ")
            .trim()
    }

    private fun stripThinkingContent(raw: String): String {
        var result = raw.replace(completeThinkBlockRegex, " ")

        val openThinkIndex = result.lastIndexOf("<think>", ignoreCase = true)
        if (openThinkIndex < 0) {
            return result
        }

        val closeThinkIndex = result.indexOf("</think>", startIndex = openThinkIndex)
        if (closeThinkIndex == -1) {
            result = result.substring(0, openThinkIndex)
        }

        return result
    }
}
