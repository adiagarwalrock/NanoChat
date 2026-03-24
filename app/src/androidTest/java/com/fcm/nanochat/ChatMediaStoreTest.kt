package com.fcm.nanochat

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fcm.nanochat.data.media.ChatMediaStore
import com.fcm.nanochat.model.ComposerAttachmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ChatMediaStoreTest {
    @Test
    fun importImage_fromFileUriCopiesIntoAppPrivateStorage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mediaStore = ChatMediaStore(context)
        val source = File(context.cacheDir, "chat_media_store_test.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        val imported = mediaStore.importImage(Uri.fromFile(source))

        assertEquals(ComposerAttachmentType.IMAGE, imported.type)
        assertTrue(imported.relativePath.startsWith("images/"))
        assertTrue(File(imported.absolutePath).exists())
        assertNotNull(mediaStore.resolveFile(imported.relativePath))
    }

    @Test
    fun importCapturedImage_missingFileThrowsIOException() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mediaStore = ChatMediaStore(context)
        val missingPath = File(context.cacheDir, "missing_capture.jpg").absolutePath

        val error = runCatching {
            mediaStore.importCapturedImage(missingPath)
        }.exceptionOrNull()

        assertTrue(error is IOException)
    }
}
