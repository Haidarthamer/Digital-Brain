package com.digitallife.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MemoryEntity::class,
        KnowledgeNodeEntity::class,
        KnowledgeEdgeEntity::class,
        TaskEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DigitalLifeDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: DigitalLifeDatabase? = null

        fun getInstance(context: Context): DigitalLifeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DigitalLifeDatabase::class.java,
                    "digital_life_brain.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
