package com.example.todo.ui.today

import com.example.todo.R
import com.example.todo.i18n.t
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
        .catch { emit(TodoListState(false, error = t(R.string.could_not_load_tasks_restart_the_app_and_try_again))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoListState())
    private val events = Channel<TodoMessage>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()
    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (_: Exception) { events.send(TodoMessage(t(R.string.action_failed_try_again))) }
    }
    fun complete(todo: TodoEntity) = action {
        if (todo.status == 1) repository.reopen(todo.id)
        else repository.complete(todo.id, expectedRevision = todo.revision)?.let { events.send(TodoMessage(if (todo.repeatRule == null) t(R.string.completed) else t(R.string.moved_to_the_next_occurrence), it)) }
    }
    fun delete(id: Long) = action { repository.delete(id)?.let { events.send(TodoMessage(t(R.string.deleted), it)) } }
    fun undo(change: UndoChange) = action {
        if (!repository.undo(change)) events.send(TodoMessage(t(R.string.this_task_changed_the_previous_action_cannot_be_undone)))
    }
}
