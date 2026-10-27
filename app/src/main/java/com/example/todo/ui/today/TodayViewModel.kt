package com.example.todo.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todo.data.repo.*
import com.example.todo.data.local.TodoEntity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.Immutable

data class TodoMessage(val text: String, val undo: UndoChange? = null)
// Room emits new read-only snapshots; callers never mutate this list or its immutable rows.
@Immutable
data class TodoListState(val loading: Boolean = true, val todos: List<TodoEntity> = emptyList(), val error: String? = null)

class TodayViewModel(private val repository: TodoRepository) : ViewModel() {
    val state = repository.all.map { TodoListState(false, it) }
        .catch { emit(TodoListState(false, error = "无法读取待办，请重启应用后重试")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoListState())
    private val events = Channel<TodoMessage>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()
    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (_: Exception) { events.send(TodoMessage("操作未成功，请重试")) }
    }
    fun complete(todo: TodoEntity) = action {
        if (todo.status == 1) repository.reopen(todo.id)
        else repository.complete(todo.id, expectedRevision = todo.revision)?.let { events.send(TodoMessage(if (todo.repeatRule == null) "已完成" else "已推进至下一次", it)) }
    }
    fun delete(id: Long) = action { repository.delete(id)?.let { events.send(TodoMessage("已删除", it)) } }
    fun undo(change: UndoChange) = action {
        if (!repository.undo(change)) events.send(TodoMessage("待办已更改，无法撤销旧操作"))
    }
}
