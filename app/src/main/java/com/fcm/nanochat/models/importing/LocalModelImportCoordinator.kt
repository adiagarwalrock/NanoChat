package com.fcm.nanochat.models.importing

interface LocalModelImportCoordinator {
    suspend fun validateImport(path: String): ImportValidationResult
}

sealed interface ImportValidationResult {
    data object UnsupportedForNow : ImportValidationResult
    data class Invalid(val reason: String) : ImportValidationResult
    data class Valid(val normalizedFileName: String) : ImportValidationResult
}

class StubLocalModelImportCoordinator : LocalModelImportCoordinator {
    override suspend fun validateImport(path: String): ImportValidationResult {
        val normalizedPath = path.trim()
        if (normalizedPath.isBlank()) {
            return ImportValidationResult.Invalid("No file was selected.")
        }

        return if (
            normalizedPath.endsWith(".litertlm", ignoreCase = true) ||
            normalizedPath.endsWith(".task", ignoreCase = true)
        ) {
            ImportValidationResult.UnsupportedForNow
        } else {
            ImportValidationResult.Invalid("Only .litertlm and .task files are supported.")
        }
    }
}
