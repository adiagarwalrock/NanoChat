package com.fcm.nanochat.models.registry

import com.fcm.nanochat.models.allowlist.AllowlistedModel
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState

data class InstalledModelRecord(
    val modelId: String,
    val displayName: String,
    val allowlistedModel: AllowlistedModel?,
    val installState: ModelInstallState,
    val storageLocation: ModelStorageLocation,
    val localPath: String?,
    val sizeBytes: Long,
    val downloadedBytes: Long,
    val errorMessage: String?,
    val allowlistVersion: String,
    val compatibility: LocalModelCompatibilityState,
    val isLegacy: Boolean,
    val isActive: Boolean
)

data class ActiveModelStatus(
    val modelId: String?,
    val displayName: String?,
    val ready: Boolean,
    val message: String?
)
