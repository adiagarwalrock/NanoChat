package com.fcm.nanochat.models.download

import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadIntegrityValidatorTest {
    private val validator = DownloadIntegrityValidator()

    @Test
    fun `validate succeeds for matching non-empty file`() {
        val file = createTempFile(prefix = "nanochat", suffix = ".part")
        file.writeBytes(ByteArray(16) { 1 })

        val result = validator.validate(
            tempFile = file,
            expectedFileName = "model.litertlm",
            expectedSizeBytes = 16
        )

        assertTrue(result is ValidationResult.Success)
        file.delete()
    }

    @Test
    fun `validate fails on size mismatch`() {
        val file = createTempFile(prefix = "nanochat", suffix = ".part")
        file.writeBytes(ByteArray(8) { 1 })

        val result = validator.validate(
            tempFile = file,
            expectedFileName = "model.litertlm",
            expectedSizeBytes = 16
        )

        assertTrue(result is ValidationResult.Failure)
        file.delete()
    }

    @Test
    fun `validate fails on empty file`() {
        val file = createTempFile(prefix = "nanochat", suffix = ".part")
        file.writeBytes(ByteArray(0))

        val result = validator.validate(
            tempFile = file,
            expectedFileName = "model.litertlm",
            expectedSizeBytes = 0
        )

        assertTrue(result is ValidationResult.Failure)
        file.delete()
    }
}
