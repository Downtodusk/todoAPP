package com.example.todo.data.local

import androidx.room.*

@Entity(tableName = "todo", indices = [Index("dueAt"), Index("status"), Index("allDayDate")])
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String? = null,
    val dueAt: Long? = null,
    val allDay: Boolean = false,
    // ISO date is authoritative for all-day items when the device timezone changes.
    val allDayDate: String? = null,
    val location: String? = null,
    val repeatRule: String? = null,
    val repeatAnchorDay: Int? = null,
    val status: Int = 0,
    val completedAt: Long? = null,
    val source: Int = 0,
    val sourceText: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val revision: Long = 0,
)
