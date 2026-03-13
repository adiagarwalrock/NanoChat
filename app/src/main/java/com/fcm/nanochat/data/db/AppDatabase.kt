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
    entities = [ChatSessionEntity::class, ChatMessageEntity::class, InstalledModelEntity::class],
    version = 4,
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
                .addMigrations(Migration1To2, Migration2To3, Migration3To4)
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

        private val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
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
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_installed_models_updatedAt` " +
                            "ON `installed_models` (`updatedAt`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_installed_models_installState` " +
                            "ON `installed_models` (`installState`)"
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
}
