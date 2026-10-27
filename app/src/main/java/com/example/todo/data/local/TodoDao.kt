package com.example.todo.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo ORDER BY dueAt, id") fun observeAll(): Flow<List<TodoEntity>>
    @Query("SELECT * FROM todo WHERE id = :id") suspend fun find(id: Long): TodoEntity?
    @Query("SELECT * FROM todo WHERE allDay = 1") suspend fun allDayItems(): List<TodoEntity>
    @Insert suspend fun insert(todo: TodoEntity): Long
    @Update suspend fun update(todo: TodoEntity)
    @Query("DELETE FROM todo WHERE id = :id") suspend fun delete(id: Long)
}
