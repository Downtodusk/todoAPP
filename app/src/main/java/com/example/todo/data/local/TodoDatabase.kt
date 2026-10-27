package com.example.todo.data.local

import android.content.Context
import androidx.room.*

@Database(entities = [TodoEntity::class, ReminderEntity::class], version = 1, exportSchema = true)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun todos(): TodoDao
    abstract fun reminders(): ReminderDao

    companion object {
        fun create(context: Context): TodoDatabase = Room.databaseBuilder(
            context.applicationContext, TodoDatabase::class.java, "todo.db"
        ).build() // Never use destructive fallback. Every later version needs an explicit migration.
    }
}
