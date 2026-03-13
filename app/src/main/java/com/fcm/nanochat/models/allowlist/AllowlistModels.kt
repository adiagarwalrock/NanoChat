package com.fcm.nanochat.models.allowlist

import org.json.JSONArray
import org.json.JSONObject

enum class AllowlistSourceType {
    BUNDLED,
    CACHED,
    REMOTE
}

data class AllowlistVersion(
    val value: String,
    val refreshedAtEpochMs: Long
)

data class AllowlistDefaultConfig(
    val topK: Int,
    val topP: Double,
    val temperature: Double,
    val maxTokens: Int,
    val accelerators: String
) {
    val acceleratorHints: List<String>
        get() = accelerators
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
}

data class AllowlistedModel(
    val id: String,
    val displayName: String,
    val name: String,
    val modelId: String,
    val modelFile: String,
    val description: String,
    val sizeInBytes: Long,
    val minDeviceMemoryInGb: Int,
    val commitHash: String,
    val defaultConfig: AllowlistDefaultConfig,
    val taskTypes: List<String>,
    val bestForTaskTypes: List<String>,
    val llmSupportImage: Boolean,
    val llmSupportAudio: Boolean,
    val backendType: String,
    val sourceRepo: String,
    val requiresHfToken: Boolean,
    val isExperimental: Boolean,
    val supportedUseCases: List<String>,
    val recommendedForChat: Boolean,
    val memoryTier: String,
    val acceleratorHints: List<String>,
    val downloadUrl: String,
    val fileType: String,
    val supportedAbis: List<String>
)

data class AllowlistSnapshot(
    val version: AllowlistVersion,
    val sourceType: AllowlistSourceType,
    val models: List<AllowlistedModel>,
    val lastRefreshError: String? = null
)

internal data class AllowlistPayload(
    val version: String,
    val models: List<AllowlistedModel>,
    val sourceType: AllowlistSourceType,
    val rawJson: String,
    val refreshedAtEpochMs: Long
)

internal object AllowlistParser {
    fun parse(
        rawJson: String,
        fallbackVersion: String,
        sourceType: AllowlistSourceType,
        refreshedAtEpochMs: Long = System.currentTimeMillis()
    ): AllowlistPayload {
        val root = JSONObject(rawJson)
        val version = root.optString("version").ifBlank { fallbackVersion }
        val modelsArray = root.optJSONArray("models") ?: JSONArray()

        val models = buildList {
            for (index in 0 until modelsArray.length()) {
                val modelJson = modelsArray.optJSONObject(index) ?: continue
                val model = modelJson.toDomainModelOrNull() ?: continue
                add(model)
            }
        }.sortedBy { it.displayName.lowercase() }

        return AllowlistPayload(
            version = version,
            models = models,
            sourceType = sourceType,
            rawJson = rawJson,
            refreshedAtEpochMs = refreshedAtEpochMs
        )
    }

    private fun JSONObject.toDomainModelOrNull(): AllowlistedModel? {
        val name = optString("name").trim()
        val modelId = optString("modelId").trim()
        val modelFile = optString("modelFile").trim()
        if (name.isBlank() || modelId.isBlank() || modelFile.isBlank()) {
            return null
        }

        val description = optString("description").trim()
        val sizeInBytes = optLong("sizeInBytes")
        val minDeviceMemoryInGb = optInt("minDeviceMemoryInGb")
        val commitHash = optString("commitHash").ifBlank { "main" }
        val configJson = optJSONObject("defaultConfig") ?: JSONObject()
        val defaultConfig = AllowlistDefaultConfig(
            topK = configJson.optInt("topK", 40),
            topP = configJson.optDouble("topP", 0.9),
            temperature = configJson.optDouble("temperature", 0.7),
            maxTokens = configJson.optInt("maxTokens", 1024),
            accelerators = configJson.optString("accelerators").ifBlank { "cpu" }
        )

        val taskTypes = optStringList("taskTypes").ifEmpty { listOf("llm_chat") }
        val bestForTaskTypes = optStringList("bestForTaskTypes").ifEmpty { taskTypes }
        val llmSupportImage = optBoolean("llmSupportImage", false)
        val llmSupportAudio = optBoolean("llmSupportAudio", false)
        val backendType = optString("backendType").ifBlank { "litert-lm" }
        val sourceRepo = optString("sourceRepo").ifBlank { modelId }

        val requiresHfToken = if (has("requiresHfToken")) {
            optBoolean("requiresHfToken", false)
        } else {
            modelId.startsWith("google/", ignoreCase = true)
        }

        val isExperimental = optBoolean("isExperimental", false)
        val supportedUseCases = optStringList("supportedUseCases").ifEmpty { taskTypes }
        val recommendedForChat = if (has("recommendedForChat")) {
            optBoolean("recommendedForChat", false)
        } else {
            taskTypes.any { it.contains("chat", ignoreCase = true) }
        }
        val memoryTier = optString("memoryTier").ifBlank {
            when {
                minDeviceMemoryInGb <= 6 -> "entry"
                minDeviceMemoryInGb <= 8 -> "mid"
                else -> "high"
            }
        }
        val acceleratorHints = optStringList("acceleratorHints").ifEmpty {
            defaultConfig.acceleratorHints
        }
        val downloadUrl = optString("downloadUrl").ifBlank {
            "https://huggingface.co/$sourceRepo/resolve/$commitHash/$modelFile?download=true"
        }
        val fileType = modelFile.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        val supportedAbis = optStringList("supportedAbis")

        return AllowlistedModel(
            id = modelId.lowercase(),
            displayName = name,
            name = name,
            modelId = modelId,
            modelFile = modelFile,
            description = description,
            sizeInBytes = sizeInBytes,
            minDeviceMemoryInGb = minDeviceMemoryInGb,
            commitHash = commitHash,
            defaultConfig = defaultConfig,
            taskTypes = taskTypes,
            bestForTaskTypes = bestForTaskTypes,
            llmSupportImage = llmSupportImage,
            llmSupportAudio = llmSupportAudio,
            backendType = backendType,
            sourceRepo = sourceRepo,
            requiresHfToken = requiresHfToken,
            isExperimental = isExperimental,
            supportedUseCases = supportedUseCases,
            recommendedForChat = recommendedForChat,
            memoryTier = memoryTier,
            acceleratorHints = acceleratorHints,
            downloadUrl = downloadUrl,
            fileType = fileType,
            supportedAbis = supportedAbis
        )
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) {
                    add(value)
                }
            }
        }
    }
}
