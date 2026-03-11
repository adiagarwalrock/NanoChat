package com.fcm.nanochat.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.fcm.nanochat.model.ChatRole

@Database(
    entities = [ChatSessionEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): ChatSessionDao
    abstract fun messageDao(): ChatMessageDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "nanochat.db")
                // TODO: Replace with explicit migrations before any release candidate.
                .fallbackToDestructiveMigration()
                .build()
    }
}

class RoomConverters {
    @TypeConverter
    fun fromRole(value: ChatRole): String = value.name

    @TypeConverter
    fun toRole(value: String): ChatRole = ChatRole.valueOf(value)
}
