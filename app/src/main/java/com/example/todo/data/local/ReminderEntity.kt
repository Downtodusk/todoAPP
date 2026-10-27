package com.example.todo.data.local

import androidx.room.*

@Entity(tableName = "reminder", foreignKeys = [ForeignKey(
    entity = TodoEntity::class, parentColumns = ["id"], childColumns = ["todoId"], onDelete = ForeignKey.CASCADE
)], indices = [Index("todoId")])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val todoId: Long,
    val triggerAt: Long,
    val fired: Boolean = false,
    val allDayMinute: Int? = null,
)
