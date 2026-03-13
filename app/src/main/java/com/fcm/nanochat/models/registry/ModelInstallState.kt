package com.fcm.nanochat.models.registry

enum class ModelInstallState {
    NOT_INSTALLED,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    FAILED,
    VALIDATING,
    INSTALLED,
    BROKEN,
    DELETING,
    MOVING
}

enum class ModelStorageLocation {
    INTERNAL,
    EXTERNAL
}
