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
import com.fcm.nanochat.models.registry.ModelInstallState
import com.fcm.nanochat.models.registry.ModelStorageLocation

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        ChatMessagePartEntity::class,
        InstalledModelEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): ChatSessionDao
    abstract fun messageDao(): ChatMessageDao
    abstract fun installedModelDao(): InstalledModelDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "nanochat.db")
                .addMigrations(*ALL_MIGRATIONS)
                .build()

        internal val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `chat_messages` " +
                            "ADD COLUMN `inferenceMode` TEXT NOT NULL DEFAULT 'REMOTE'"
                )
                db.execSQL(
                    "ALTER TABLE `chat_messages` " +
                            "ADD COLUMN `modelName` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `chat_messages` " +
                            "ADD COLUMN `temperature` REAL NOT NULL DEFAULT 0.7"
                )
                db.execSQL(
                    "ALTER TABLE `chat_messages` " +
                            "ADD COLUMN `topP` REAL NOT NULL DEFAULT 0.9"
                )
                db.execSQL(
                    "ALTER TABLE `chat_messages` " +
                            "ADD COLUMN `contextLength` INTEGER NOT NULL DEFAULT 4096"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` " +
                            "ON `chat_messages` (`sessionId`)"
                )
            }
        }

        internal val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId_createdAt_id` " +
                            "ON `chat_messages` (`sessionId`, `createdAt`, `id`)"
                )
            }
        }

        internal val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `installed_models` (" +
                            "`modelId` TEXT NOT NULL, " +
                            "`displayName` TEXT NOT NULL, " +
                            "`modelFileName` TEXT NOT NULL, " +
                            "`localPath` TEXT NOT NULL, " +
                            "`sizeBytes` INTEGER NOT NULL, " +
                            "`downloadedBytes` INTEGER NOT NULL, " +
                            "`installState` TEXT NOT NULL, " +
                            "`storageLocation` TEXT NOT NULL, " +
                            "`allowlistVersion` TEXT NOT NULL, " +
                            "`errorMessage` TEXT, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`modelId`)" +
                            ")"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_installed_models_updatedAt` " +
                            "ON `installed_models` (`updatedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_installed_models_installState` " +
                            "ON `installed_models` (`installState`)"
                )
            }
        }

        internal val Migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_message_parts` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`messageId` INTEGER NOT NULL, " +
                            "`partIndex` INTEGER NOT NULL, " +
                            "`partType` TEXT NOT NULL, " +
                            "`relativePath` TEXT, " +
                            "`mimeType` TEXT, " +
                            "`displayName` TEXT, " +
                            "`sizeBytes` INTEGER, " +
                            "`widthPx` INTEGER, " +
                            "`heightPx` INTEGER, " +
                            "`durationMs` INTEGER, " +
                            "`sourceMessageId` INTEGER, " +
                            "`state` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`messageId`) REFERENCES `chat_messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
                            ")"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_message_parts_messageId` " +
                            "ON `chat_message_parts` (`messageId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_message_parts_messageId_partIndex` " +
                            "ON `chat_message_parts` (`messageId`, `partIndex`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_message_parts_partType` " +
                            "ON `chat_message_parts` (`partType`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_message_parts_sourceMessageId` " +
                            "ON `chat_message_parts` (`sourceMessageId`)"
                )
            }
        }

        private val Migration5To4 = object : Migration(5, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_chat_message_parts_messageId`")
                db.execSQL("DROP INDEX IF EXISTS `index_chat_message_parts_messageId_partIndex`")
                db.execSQL("DROP INDEX IF EXISTS `index_chat_message_parts_partType`")
                db.execSQL("DROP INDEX IF EXISTS `index_chat_message_parts_sourceMessageId`")
                db.execSQL("DROP TABLE IF EXISTS `chat_message_parts`")
            }
        }

        internal val ALL_MIGRATIONS = arrayOf(
            Migration1To2,
            Migration2To3,
            Migration3To4,
            Migration4To5,
            Migration5To4
        )
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

    @TypeConverter
    fun fromInstallState(value: ModelInstallState): String = value.name

    @TypeConverter
    fun toInstallState(value: String): ModelInstallState =
        runCatching { ModelInstallState.valueOf(value) }
            .getOrDefault(ModelInstallState.NOT_INSTALLED)

    @TypeConverter
    fun fromStorageLocation(value: ModelStorageLocation): String = value.name

    @TypeConverter
    fun toStorageLocation(value: String): ModelStorageLocation =
        runCatching { ModelStorageLocation.valueOf(value) }
            .getOrDefault(ModelStorageLocation.INTERNAL)

    @TypeConverter
    fun fromPartType(value: ChatMessagePartType): String = value.name

    @TypeConverter
    fun toPartType(value: String): ChatMessagePartType =
        runCatching { ChatMessagePartType.valueOf(value) }
            .getOrDefault(ChatMessagePartType.IMAGE)

    @TypeConverter
    fun fromPartState(value: ChatMessagePartState): String = value.name

    @TypeConverter
    fun toPartState(value: String): ChatMessagePartState =
        runCatching { ChatMessagePartState.valueOf(value) }
            .getOrDefault(ChatMessagePartState.READY)
}
