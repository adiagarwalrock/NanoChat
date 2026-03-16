package com.fcm.nanochat.inference

object GeneratedTextSanitizer {
    private const val MAX_ASSISTANT_PREFIX_STRIPS = 4

    private val controlLineRegex =
        Regex(
            pattern =
                """(?i)^\s*(<\|assistant\|>|<\|user\|>|<\|system\|>|<\|im_start\|>|<\|im_end\|>|<\|eot_id\|>|<\|endoftext\|>|<assistant>|</assistant>|<user>|</user>|assistant\s*:|###\s*assistant\s*:|\[assistant])\s*$"""
        )

    private val leadingAssistantPrefixRegex =
        Regex(
            pattern =
                """(?i)^\s*((<\|assistant\|>|<assistant>|assistant\s*:|###\s*assistant\s*:|\[assistant])[ \t]*)+"""
        )

    private val inlineControlTokenRegex =
        Regex(
            pattern =
                """(?i)[ \t]*(<\|assistant\|>|<\|user\|>|<\|system\|>|<\|im_start\|>|<\|im_end\|>|<\|eot_id\|>|<\|endoftext\|>|<\|begin_of_text\|>|<s>|</s>|<assistant>|</assistant>|<user>|</user>)[ \t]*"""
        )

    private val standaloneRoleLineRegex =
        Regex(pattern = """(?im)^\s*(assistant|user|system)\s*$""")

    private val completeThinkBlockRegex = Regex(pattern = """(?is)<think>.*?</think>""")

    private val leadingAssistantLabelRegex = Regex("(?i)^\\s*assistant\\s*:[ \t]*")
    private val excessiveNewlineRegex = Regex("\\n{4,}")

    fun sanitize(raw: String, preserveThinkingBlocks: Boolean = true): String {
        if (raw.isBlank()) return ""

        val normalizedInput = raw.replace("\r\n", "\n").replace('\r', '\n')
        val thinkingNormalized =
            if (preserveThinkingBlocks) normalizedInput
            else stripThinkingContent(normalizedInput)

        val lines = thinkingNormalized.lines()
        var normalized =
            lines
                .dropWhile { it.isBlank() || controlLineRegex.matches(it.trim()) }
                .joinToString("\n")

        repeat(MAX_ASSISTANT_PREFIX_STRIPS) {
            val updated = normalized.replaceFirst(leadingAssistantPrefixRegex, "")
            if (updated == normalized) return@repeat
            normalized = updated
        }

        return normalized
            .replace(inlineControlTokenRegex, " ")
            .replace(standaloneRoleLineRegex, "")
            .replace(leadingAssistantLabelRegex, "")
            .replace(excessiveNewlineRegex, "\n\n")
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
