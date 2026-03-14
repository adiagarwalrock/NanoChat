package com.fcm.nanochat.models.download

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempFile

class DownloadIntegrityValidatorTest {
    private val validator = DownloadIntegrityValidator()

    @Test
    fun `validate succeeds for matching non-empty file`() {
        withTempDownloadFile(ByteArray(16) { 1 }) { file ->
            val result = validator.validate(
                tempFile = file,
                expectedFileName = "model.litertlm",
                expectedSizeBytes = 16
            )

            assertTrue(result is ValidationResult.Success)
        }
    }

    @Test
    fun `validate fails on size mismatch`() {
        withTempDownloadFile(ByteArray(8) { 1 }) { file ->
            val result = validator.validate(
                tempFile = file,
                expectedFileName = "model.litertlm",
                expectedSizeBytes = 16
            )

            assertTrue(result is ValidationResult.Failure)
        }
    }

    @Test
    fun `validate fails on empty file`() {
        withTempDownloadFile(ByteArray(0)) { file ->
            val result = validator.validate(
                tempFile = file,
                expectedFileName = "model.litertlm",
                expectedSizeBytes = 0
            )

            assertTrue(result is ValidationResult.Failure)
        }
    }

    private fun withTempDownloadFile(
        bytes: ByteArray,
        block: (java.io.File) -> Unit
    ) {
        val file = createTempFile(prefix = "nanochat", suffix = ".part").toFile()
        file.writeBytes(bytes)
        try {
            block(file)
        } finally {
            file.delete()
        }
    }
}
