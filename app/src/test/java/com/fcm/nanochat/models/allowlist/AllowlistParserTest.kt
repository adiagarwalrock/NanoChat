package com.fcm.nanochat.models.allowlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertNull(model.downloadRepo)
        assertNull(model.downloadPath)
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

    @Test
    fun `parse uses explicit mirror download repo and path`() {
        val json = """
            {
              "models": [
                {
                  "name": "Gemma3-1B-IT",
                  "modelId": "adiagarwal/nanochat-models/Gemma3-1B-IT",
                  "modelFile": "gemma3-1b-it-int4.litertlm",
                  "sizeInBytes": 1200,
                  "minDeviceMemoryInGb": 6,
                  "sourceRepo": "litert-community/Gemma3-1B-IT",
                  "downloadRepo": "adiagarwal/nanochat-models",
                  "downloadPath": "Gemma3-1B-IT/gemma3-1b-it-int4.litertlm"
                }
              ]
            }
        """.trimIndent()

        val parsed = AllowlistParser.parse(
            rawJson = json,
            fallbackVersion = "2_0_3",
            sourceType = AllowlistSourceType.BUNDLED
        )

        val model = parsed.models.first()
        assertEquals("adiagarwal/nanochat-models", model.downloadRepo)
        assertEquals("Gemma3-1B-IT/gemma3-1b-it-int4.litertlm", model.downloadPath)
        assertEquals(
            "https://huggingface.co/adiagarwal/nanochat-models/resolve/main/Gemma3-1B-IT/gemma3-1b-it-int4.litertlm?download=true",
            model.downloadUrl
        )
    }

    @Test
    fun `parse keeps explicit download URL override`() {
        val json = """
            {
              "models": [
                {
                  "name": "Model",
                  "modelId": "org/model",
                  "modelFile": "model.litertlm",
                  "downloadRepo": "ignored/repo",
                  "downloadPath": "ignored/path/model.litertlm",
                  "downloadUrl": "https://cdn.example.com/model.litertlm",
                  "sizeInBytes": 1200,
                  "minDeviceMemoryInGb": 6
                }
              ]
            }
        """.trimIndent()

        val parsed = AllowlistParser.parse(
            rawJson = json,
            fallbackVersion = "2_0_3",
            sourceType = AllowlistSourceType.BUNDLED
        )

        assertEquals("https://cdn.example.com/model.litertlm", parsed.models.first().downloadUrl)
    }

    @Test
    fun `enabled models in bundled asset have valid offline mirror metadata`() {
        val file = java.io.File("src/main/assets/model_allowlist_2_0_3.json")
        assertTrue("Allowlist file must exist at ${file.absolutePath}", file.exists())

        val json = file.readText()
        val parsed = AllowlistParser.parse(
            rawJson = json,
            fallbackVersion = "2_0_3",
            sourceType = AllowlistSourceType.BUNDLED
        )

        val enabledModels = parsed.models.filter { it.enabled }
        assertTrue("There must be at least one enabled model", enabledModels.isNotEmpty())

        for (model in enabledModels) {
            assertFalse(
                "modelFile must be basename-only for ${model.name}",
                model.modelFile.contains('/')
            )
            assertFalse(
                "modelFile must be basename-only for ${model.name}",
                model.modelFile.contains('\\')
            )
            assertTrue(
                "downloadRepo must be present for ${model.name}",
                !model.downloadRepo.isNullOrBlank()
            )
            assertTrue(
                "downloadPath must be present for ${model.name}",
                !model.downloadPath.isNullOrBlank()
            )
            assertTrue(
                "downloadPath must end with modelFile for ${model.name}",
                model.downloadPath!!.endsWith("/${model.modelFile}") ||
                        model.downloadPath == model.modelFile
            )
            assertTrue(
                "downloadUrl must include resolve path for ${model.name}",
                model.downloadUrl.contains("/resolve/")
            )
            assertTrue(
                "downloadUrl must end with the model file for ${model.name}",
                model.downloadUrl.contains("/${model.modelFile}?download=true")
            )
        }
    }
}
