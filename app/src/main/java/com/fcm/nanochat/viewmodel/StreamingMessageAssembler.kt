package com.fcm.nanochat.viewmodel

import com.fcm.nanochat.inference.InferenceMode

class StreamingMessageAssembler {
    private val rawBuilder = StringBuilder()

    fun append(mode: InferenceMode, chunk: String): String {
        if (mode == InferenceMode.AICORE) rawBuilder.clear()
        rawBuilder.append(chunk)
        return rawBuilder.toString()
    }

    fun current(): String = rawBuilder.toString()
}
