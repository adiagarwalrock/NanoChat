package com.fcm.nanochat.models.allowlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowlistParserTest {
    @Test
    fun `parse maps gallery style JSON to domain models`() {
        val json = """
            {
              "models": [
                {
                  "name": "Qwen2.5-1.5B-Instruct",
                  "modelId": "litert-community/Qwen2.5-1.5B-Instruct",
                  "modelFile": "Qwen2.5-1.5B-Instruct.litertlm",
                  "description": "Test model",
                  "sizeInBytes": 1200,
                  "minDeviceMemoryInGb": 6,
                  "commitHash": "abc123",
                  "defaultConfig": {
                    "topK": 20,
                    "topP": 0.8,
                    "temperature": 0.7,
                    "maxTokens": 1024,
                    "accelerators": "gpu,cpu"
                  },
                  "taskTypes": ["llm_chat"],
                  "bestForTaskTypes": ["llm_chat"]
                }
              ]
            }
        """.trimIndent()

        val parsed = AllowlistParser.parse(
            rawJson = json,
            fallbackVersion = "1_0_10",
            sourceType = AllowlistSourceType.BUNDLED
        )

        assertEquals("1_0_10", parsed.version)
        assertEquals(1, parsed.models.size)

        val model = parsed.models.first()
        assertEquals("litert-community/qwen2.5-1.5b-instruct", model.id)
        assertEquals("litert-community/Qwen2.5-1.5B-Instruct", model.modelId)
        assertEquals("Qwen2.5-1.5B-Instruct.litertlm", model.modelFile)
        assertEquals("litert-lm", model.backendType)
        assertEquals("litert-community/Qwen2.5-1.5B-Instruct", model.sourceRepo)
        assertFalse(model.requiresHfToken)
        assertTrue(model.recommendedForChat)
        assertFalse(model.supportsThinking)
        assertTrue(model.downloadUrl.contains("/resolve/abc123/"))
    }

    @Test
    fun `parse infers thinking support from metadata when field is absent`() {
        val json = """
            {
              "models": [
                {
                  "name": "DeepSeek-R1-Distill-Qwen-1.5B",
                  "modelId": "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
                  "modelFile": "DeepSeek-R1-Distill-Qwen-1.5B.litertlm",
                  "description": "Reasoning-focused local model optimized for analysis.",
                  "sizeInBytes": 1200,
                  "minDeviceMemoryInGb": 6,
                  "defaultConfig": {
                    "topK": 20,
                    "topP": 0.8,
                    "temperature": 0.7,
                    "maxTokens": 1024,
                    "accelerators": "gpu,cpu"
                  },
                  "taskTypes": ["llm_chat"],
                  "bestForTaskTypes": ["llm_chat"],
                  "supportedUseCases": ["chat", "analysis"]
                }
              ]
            }
        """.trimIndent()

        val parsed = AllowlistParser.parse(
            rawJson = json,
            fallbackVersion = "1_0_10",
            sourceType = AllowlistSourceType.BUNDLED
        )

        assertTrue(parsed.models.first().supportsThinking)
    }
}
