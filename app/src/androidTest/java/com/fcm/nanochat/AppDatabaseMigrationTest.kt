package com.fcm.nanochat

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fcm.nanochat.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        requireNotNull(AppDatabase::class.java.canonicalName),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate4To5_preservesMessagesAndCreatesPartsTable() {
        val dbName = "migration_4_5.db"

        helper.createDatabase(dbName, 4).apply {
            execSQL(
                "INSERT INTO chat_sessions (`id`, `title`, `createdAt`, `updatedAt`) " +
                        "VALUES (1, 'Session', 1, 1)"
            )
            execSQL(
                "INSERT INTO chat_messages (" +
                        "`id`, `sessionId`, `role`, `content`, `inferenceMode`, `modelName`, `temperature`, `topP`, `contextLength`, `createdAt`, `updatedAt`" +
                        ") VALUES (" +
                        "1, 1, 'USER', 'Hello', 'REMOTE', 'gpt-test', 0.7, 0.9, 4096, 1, 1" +
                        ")"
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            dbName,
            5,
            true,
            AppDatabase.Migration4To5
        )

        migratedDb.query("SELECT COUNT(*) FROM chat_messages").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        migratedDb.query("PRAGMA table_info(chat_message_parts)").use { cursor ->
            assertTrue(cursor.count > 0)
        }
    }
}
