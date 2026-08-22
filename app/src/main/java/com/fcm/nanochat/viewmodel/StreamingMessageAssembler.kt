package com.fcm.nanochat.viewmodel

import com.fcm.nanochat.inference.GeneratedTextSanitizer
import com.fcm.nanochat.inference.InferenceMode

class StreamingMessageAssembler {
    private val rawBuilder = StringBuilder()

    fun append(mode: InferenceMode, chunk: String): String {
        if (mode == InferenceMode.AICORE) rawBuilder.clear()
        rawBuilder.append(chunk)

        // Control markers usually appear only at the start/end of a generation. Avoid running
        // the full sanitizer for every normal token while still preventing template leakage.
        val mayContainControlMarker = chunk.indexOf('<') >= 0 || chunk.contains("|>")
        val mayContainLeadingRole = rawBuilder.length <= MAX_ROLE_PREFIX_LENGTH &&
                rawBuilder.toString().trimStart().startsWith("Assistant:", ignoreCase = true)
        if (mayContainControlMarker || mayContainLeadingRole) {
            val sanitized = GeneratedTextSanitizer.sanitize(rawBuilder.toString())
            rawBuilder.clear()
            rawBuilder.append(sanitized)
        }
        return rawBuilder.toString()
    }

    fun current(): String = rawBuilder.toString()

    private companion object {
        const val MAX_ROLE_PREFIX_LENGTH = 64
    }
}
