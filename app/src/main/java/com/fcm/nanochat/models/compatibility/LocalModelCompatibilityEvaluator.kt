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
    context: Context,
    private val runtimeProbe: suspend (AllowlistedModel, String) -> RuntimeProbeResult
) {
    private val appContext = context.applicationContext

    suspend fun evaluate(
        model: AllowlistedModel,
        installedPath: String?,
        installState: ModelInstallState,
        tokenPresent: Boolean
    ): LocalModelCompatibilityState {
        if (!isAndroidSupported()) {
            return LocalModelCompatibilityState.UnsupportedDevice(
                "Android 12 or newer is required for local model execution."
            )
        }

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

        if (installState == ModelInstallState.INSTALLED && installedPath.isNullOrBlank()) {
            return LocalModelCompatibilityState.CorruptedModel
        }

        if (installState == ModelInstallState.INSTALLED && !installedPath.isNullOrBlank()) {
            val file = File(installedPath)
            if (!file.exists() || file.length() <= 0L) {
                return LocalModelCompatibilityState.CorruptedModel
            }

            val runtime = runtimeProbe(model, installedPath)
            return when (runtime) {
                RuntimeProbeResult.Ready -> LocalModelCompatibilityState.Ready
                is RuntimeProbeResult.Unavailable -> LocalModelCompatibilityState.RuntimeUnavailable(
                    runtime.reason
                )
            }
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

    private fun isAndroidSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private fun isFileTypeSupported(fileType: String): Boolean {
        return fileType == "litertlm" || fileType == "task"
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
        if (model.recommendedForChat) return true
        return model.taskTypes.any { it.contains("chat", ignoreCase = true) }
    }
}

sealed interface RuntimeProbeResult {
    data object Ready : RuntimeProbeResult
    data class Unavailable(val reason: String) : RuntimeProbeResult
}
