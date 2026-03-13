package com.fcm.nanochat.models.download

import java.io.File

class DownloadIntegrityValidator {
    fun validate(
        tempFile: File,
        expectedFileName: String,
        expectedSizeBytes: Long
    ): ValidationResult {
        if (!tempFile.exists()) {
            return ValidationResult.Failure("Downloaded file is missing.")
        }

        val finalName = expectedFileName.trim()
        if (finalName.isBlank()) {
            return ValidationResult.Failure("Model filename is invalid.")
        }
        if (finalName.contains('/') || finalName.contains('\\')) {
            return ValidationResult.Failure("Model filename must not include directories.")
        }
        if (!finalName.endsWith(".litertlm")) {
            return ValidationResult.Failure("Model filename has an unsupported extension.")
        }

        val size = tempFile.length()
        if (size <= 0L) {
            return ValidationResult.Failure("Downloaded file is empty.")
        }

        if (expectedSizeBytes > 0L && size != expectedSizeBytes) {
            return ValidationResult.Failure(
                "Downloaded file size mismatch. Expected $expectedSizeBytes bytes, got $size bytes."
            )
        }

        return ValidationResult.Success
    }
}

sealed interface ValidationResult {
    data object Success : ValidationResult
    data class Failure(val message: String) : ValidationResult
}
