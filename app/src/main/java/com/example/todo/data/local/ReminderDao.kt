package com.example.todo.data.local

import androidx.room.*

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder WHERE todoId = :todoId ORDER BY triggerAt") suspend fun forTodo(todoId: Long): List<ReminderEntity>
    @Query("SELECT * FROM reminder WHERE id = :id") suspend fun find(id: Long): ReminderEntity?
    @Query("SELECT reminder.* FROM reminder INNER JOIN todo ON todo.id = reminder.todoId WHERE reminder.fired = 0 AND todo.status = 0")
    suspend fun pending(): List<ReminderEntity>
    @Insert suspend fun insert(reminder: ReminderEntity): Long
    @Update suspend fun update(reminder: ReminderEntity)
    @Query("DELETE FROM reminder WHERE todoId = :todoId") suspend fun deleteForTodo(todoId: Long)
}
