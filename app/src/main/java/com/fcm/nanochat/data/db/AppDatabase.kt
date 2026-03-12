package com.fcm.nanochat.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.model.ChatRole

@Database(
    entities = [ChatSessionEntity::class, ChatMessageEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): ChatSessionDao
    abstract fun messageDao(): ChatMessageDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "nanochat.db")
                .addMigrations(Migration1To2, Migration2To3)
                .build()

        private val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `chat_messages` " +
                            "ADD COLUMN `inferenceMode` TEXT NOT NULL DEFAULT 'REMOTE'"
                )
                database.execSQL(
                    "ALTER TABLE `chat_messages` " +
                            "ADD COLUMN `modelName` TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "ALTER TABLE `chat_messages` " +
                            "ADD COLUMN `temperature` REAL NOT NULL DEFAULT 0.7"
                )
                database.execSQL(
                    "ALTER TABLE `chat_messages` " +
                            "ADD COLUMN `topP` REAL NOT NULL DEFAULT 0.9"
                )
                database.execSQL(
                    "ALTER TABLE `chat_messages` " +
                            "ADD COLUMN `contextLength` INTEGER NOT NULL DEFAULT 4096"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` " +
                            "ON `chat_messages` (`sessionId`)"
                )
            }
        }

        private val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId_createdAt_id` " +
                            "ON `chat_messages` (`sessionId`, `createdAt`, `id`)"
                )
            }
        }
    }
}

class RoomConverters {
    @TypeConverter
    fun fromRole(value: ChatRole): String = value.name

    @TypeConverter
    fun toRole(value: String): ChatRole =
        runCatching { ChatRole.valueOf(value) }
            .getOrDefault(ChatRole.USER)

    @TypeConverter
    fun fromMode(value: InferenceMode): String = value.name

    @TypeConverter
    fun toMode(value: String): InferenceMode =
        runCatching { InferenceMode.valueOf(value) }
            .getOrDefault(InferenceMode.REMOTE)
}
