package com.fcm.nanochat.models.runtime

interface LocalModelRuntime {
    fun generate(prompt: String): String
    fun close()
}
