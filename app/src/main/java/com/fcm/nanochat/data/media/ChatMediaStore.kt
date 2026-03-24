package com.fcm.nanochat.data.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.fcm.nanochat.model.ComposerAttachmentType
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

data class CameraCaptureTarget(
    val uri: Uri,
    val absolutePath: String
)

data class ImportedMediaAsset(
    val type: ComposerAttachmentType,
    val relativePath: String,
    val absolutePath: String,
    val mimeType: String,
    val displayName: String,
    val sizeBytes: Long,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val durationMs: Long? = null
)

class ChatMediaStore(
    private val appContext: Context
) {
    fun createCameraCaptureTarget(): CameraCaptureTarget {
        val directory = File(appContext.cacheDir, CAPTURE_DIRECTORY).apply { mkdirs() }
        val file = File(directory, "capture_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(appContext, authority, file)
        return CameraCaptureTarget(uri = uri, absolutePath = file.absolutePath)
    }

    fun importCapturedImage(tempAbsolutePath: String): ImportedMediaAsset {
        val source = File(tempAbsolutePath)
        if (!source.exists() || source.length() <= 0L) {
            throw IOException("Captured photo file is missing.")
        }
        val imported = source.inputStream().use { input ->
            importStream(
                input = input,
                type = ComposerAttachmentType.IMAGE,
                suggestedName = source.name,
                mimeTypeOverride = "image/jpeg"
            )
        }
        runCatching { source.delete() }
        return imported
    }

    fun importImage(uri: Uri): ImportedMediaAsset {
        return importUri(uri = uri, type = ComposerAttachmentType.IMAGE)
    }

    fun importAudio(uri: Uri): ImportedMediaAsset {
        return importUri(uri = uri, type = ComposerAttachmentType.AUDIO)
    }

    fun resolveAbsolutePath(relativePath: String): String {
        return File(mediaRootDirectory(), relativePath).absolutePath
    }

    fun resolveFile(relativePath: String): File? {
        val file = File(mediaRootDirectory(), relativePath)
        return file.takeIf { it.exists() && it.isFile }
    }

    fun toDataUrl(relativePath: String, mimeType: String): String {
        val file = resolveFile(relativePath) ?: throw IOException("Attachment file is missing.")
        val bytes = FileInputStream(file).use { it.readBytes() }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:$mimeType;base64,$encoded"
    }

    fun deleteRelativePath(relativePath: String) {
        runCatching { resolveFile(relativePath)?.delete() }
    }

    fun deleteAbsolutePath(absolutePath: String) {
        runCatching { File(absolutePath).delete() }
    }

    fun clearAll() {
        runCatching { mediaRootDirectory().deleteRecursively() }
    }

    private fun importUri(
        uri: Uri,
        type: ComposerAttachmentType
    ): ImportedMediaAsset {
        val resolver = appContext.contentResolver
        val displayName = resolver.queryDisplayName(uri) ?: defaultFileNameFor(type)
        val mimeType = resolveMimeType(resolver, uri, type)
        val stream =
            resolver.openInputStream(uri) ?: throw IOException("Unable to read selected file.")
        stream.use { input ->
            return importStream(
                input = input,
                type = type,
                suggestedName = displayName,
                mimeTypeOverride = mimeType
            )
        }
    }

    private fun importStream(
        input: java.io.InputStream,
        type: ComposerAttachmentType,
        suggestedName: String,
        mimeTypeOverride: String
    ): ImportedMediaAsset {
        val extension = extensionFor(mimeTypeOverride, suggestedName, type)
        val subdirectory = when (type) {
            ComposerAttachmentType.IMAGE -> IMAGE_DIRECTORY
            ComposerAttachmentType.AUDIO -> AUDIO_DIRECTORY
        }
        val relativePath = "$subdirectory/${UUID.randomUUID()}.$extension"
        val destination = File(mediaRootDirectory(), relativePath).apply {
            parentFile?.mkdirs()
        }

        FileOutputStream(destination).use { output ->
            input.copyTo(output)
        }

        val metadata = extractMetadata(type = type, file = destination)
        return ImportedMediaAsset(
            type = type,
            relativePath = relativePath,
            absolutePath = destination.absolutePath,
            mimeType = mimeTypeOverride,
            displayName = suggestedName,
            sizeBytes = destination.length(),
            widthPx = metadata.widthPx,
            heightPx = metadata.heightPx,
            durationMs = metadata.durationMs
        )
    }

    private fun extractMetadata(type: ComposerAttachmentType, file: File): MediaMetadata {
        return when (type) {
            ComposerAttachmentType.IMAGE -> {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
                MediaMetadata(
                    widthPx = options.outWidth.takeIf { it > 0 },
                    heightPx = options.outHeight.takeIf { it > 0 },
                    durationMs = null
                )
            }

            ComposerAttachmentType.AUDIO -> {
                val duration = runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.absolutePath)
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull()
                    } finally {
                        runCatching { retriever.release() }
                    }
                }.getOrNull()
                MediaMetadata(widthPx = null, heightPx = null, durationMs = duration)
            }
        }
    }

    private fun mediaRootDirectory(): File {
        return File(appContext.filesDir, MEDIA_ROOT_DIRECTORY).apply { mkdirs() }
    }

    private fun resolveMimeType(
        resolver: ContentResolver,
        uri: Uri,
        type: ComposerAttachmentType
    ): String {
        val detected = resolver.getType(uri)?.trim().orEmpty()
        if (detected.isNotBlank()) return detected
        return when (type) {
            ComposerAttachmentType.IMAGE -> "image/jpeg"
            ComposerAttachmentType.AUDIO -> "audio/mpeg"
        }
    }

    private fun extensionFor(
        mimeType: String,
        fileName: String,
        type: ComposerAttachmentType
    ): String {
        val fromMime = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?.trim()
            .orEmpty()
        if (fromMime.isNotBlank()) return fromMime

        val fromName = fileName.substringAfterLast('.', "").trim()
        if (fromName.isNotBlank()) return fromName

        return when (type) {
            ComposerAttachmentType.IMAGE -> "jpg"
            ComposerAttachmentType.AUDIO -> "mp3"
        }
    }

    private fun ContentResolver.queryDisplayName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0 || !cursor.moveToFirst()) null else cursor.getString(index)
        }
    }

    private fun defaultFileNameFor(type: ComposerAttachmentType): String {
        return when (type) {
            ComposerAttachmentType.IMAGE -> "image.jpg"
            ComposerAttachmentType.AUDIO -> "audio.mp3"
        }
    }

    private val authority: String
        get() = "${appContext.packageName}.fileprovider"

    private data class MediaMetadata(
        val widthPx: Int?,
        val heightPx: Int?,
        val durationMs: Long?
    )

    private companion object {
        const val MEDIA_ROOT_DIRECTORY = "chat_media"
        const val IMAGE_DIRECTORY = "images"
        const val AUDIO_DIRECTORY = "audio"
        const val CAPTURE_DIRECTORY = "chat_capture"
    }
}
