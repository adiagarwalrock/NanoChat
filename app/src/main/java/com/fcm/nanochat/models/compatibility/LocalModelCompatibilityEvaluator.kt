package com.fcm.nanochat.models.compatibility

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import com.fcm.nanochat.models.allowlist.AllowlistedModel
import com.fcm.nanochat.models.registry.ModelInstallState
import java.io.File
import kotlin.math.floor

class LocalModelCompatibilityEvaluator(
    context: Context
) {
    private val appContext = context.applicationContext

    fun evaluate(
        model: AllowlistedModel,
        installedPath: String?,
        installState: ModelInstallState,
        tokenPresent: Boolean
    ): LocalModelCompatibilityState {
        if (!isFileTypeSupported(model.fileType)) {
            return LocalModelCompatibilityState.UnsupportedDevice(
                "Unsupported model file type: ${model.fileType.ifBlank { "unknown" }}."
            )
        }

        if (!isAbiSupported(model)) {
            return LocalModelCompatibilityState.UnsupportedDevice(
                "This model does not support your device architecture."
            )
        }

        if (!isChatSuitable(model)) {
            return LocalModelCompatibilityState.UnsupportedForChat
        }

        if (model.requiresHfToken && !tokenPresent) {
            return LocalModelCompatibilityState.TokenRequired
        }

        val availableRamGb = deviceRamInGb()
        if (availableRamGb < model.minDeviceMemoryInGb) {
            return LocalModelCompatibilityState.NeedsMoreRam(
                requiredGb = model.minDeviceMemoryInGb,
                availableGb = availableRamGb
            )
        }

        if (installState == ModelInstallState.INSTALLED) {
            return evaluateInstalledModel(model = model, installedPath = installedPath)
        }

        val availableStorage = availableStorageBytes()
        if (model.sizeInBytes > 0L && availableStorage < model.sizeInBytes) {
            return LocalModelCompatibilityState.NeedsMoreStorage(
                requiredBytes = model.sizeInBytes,
                availableBytes = availableStorage
            )
        }

        return when (installState) {
            ModelInstallState.BROKEN -> LocalModelCompatibilityState.CorruptedModel
            ModelInstallState.FAILED -> LocalModelCompatibilityState.DownloadedButNotActivatable(
                "The last install attempt failed. Retry the download."
            )

            else -> LocalModelCompatibilityState.Downloadable
        }
    }

    private fun evaluateInstalledModel(
        model: AllowlistedModel,
        installedPath: String?
    ): LocalModelCompatibilityState {
        if (installedPath.isNullOrBlank()) {
            return LocalModelCompatibilityState.CorruptedModel
        }

        val file = File(installedPath)
        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            return LocalModelCompatibilityState.CorruptedModel
        }

        if (file.name.endsWith(".part", ignoreCase = true)) {
            return startupValidationFailure(
                "Selected file is a partial download: ${file.absolutePath}"
            )
        }

        if (file.name != model.modelFile) {
            return startupValidationFailure(
                "Expected ${model.modelFile} but found ${file.name}."
            )
        }

        if (model.fileType.isNotBlank() && !file.extension.equals(
                model.fileType,
                ignoreCase = true
            )
        ) {
            return startupValidationFailure(
                "Expected *.${model.fileType} but found *.${file.extension}."
            )
        }

        if (model.sizeInBytes > 0L && file.length() != model.sizeInBytes) {
            return startupValidationFailure(
                "Expected ${model.sizeInBytes} bytes but found ${file.length()}."
            )
        }

        return LocalModelCompatibilityState.Ready
    }

    private fun startupValidationFailure(reason: String): LocalModelCompatibilityState {
        return LocalModelCompatibilityState.DownloadedButNotActivatable(
            "Startup validation failed. $reason"
        )
    }

    private fun availableStorageBytes(): Long {
        val statFs = StatFs(appContext.filesDir.absolutePath)
        return statFs.availableBytes
    }

    private fun deviceRamInGb(): Int {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memoryInfo)
        val totalMemGb = memoryInfo.totalMem.toDouble() / 1_000_000_000.0
        return floor(totalMemGb).toInt().coerceAtLeast(1)
    }

    private fun isFileTypeSupported(fileType: String): Boolean {
        return fileType == "litertlm"
    }

    private fun isAbiSupported(model: AllowlistedModel): Boolean {
        val supported = model.supportedAbis
        if (supported.isEmpty()) return true
        return Build.SUPPORTED_ABIS.any { abi ->
            supported.any {
                it.equals(
                    abi,
                    ignoreCase = true
                )
            }
        }
    }

    private fun isChatSuitable(model: AllowlistedModel): Boolean {
        return model.recommendedForChat ||
                model.taskTypes.any { it.contains("chat", ignoreCase = true) }
    }
}
