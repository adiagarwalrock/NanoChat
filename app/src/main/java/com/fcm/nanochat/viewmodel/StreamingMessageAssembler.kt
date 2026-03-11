package com.fcm.nanochat.viewmodel

import com.fcm.nanochat.inference.InferenceMode

class StreamingMessageAssembler {
    private val builder = StringBuilder()

    fun append(mode: InferenceMode, chunk: String): String {
        return when (mode) {
            InferenceMode.REMOTE -> {
                builder.append(chunk)
                builder.toString()
            }

            InferenceMode.AICORE -> {
                builder.clear()
                builder.append(chunk)
                builder.toString()
            }
        }
    }

    fun current(): String = builder.toString()
}
