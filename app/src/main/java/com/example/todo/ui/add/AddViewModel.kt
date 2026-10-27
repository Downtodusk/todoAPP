package com.example.todo.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todo.data.local.TodoEntity
import com.example.todo.data.local.ReminderEntity
import com.example.todo.data.repo.TodoRepository
import com.example.todo.data.repo.TodoSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class EditorState(val loading: Boolean = false, val snapshot: TodoSnapshot? = null, val saving: Boolean = false,
    val error: String? = null, val saved: Boolean = false, val missing: Boolean = false, val savedWithReminder: Boolean = false)

class AddViewModel(private val repository: TodoRepository, private val id: Long?) : ViewModel() {
    private val mutable = MutableStateFlow(EditorState(loading = id != null))
    val state = mutable.asStateFlow()
    init { if (id != null) viewModelScope.launch {
        try { val snapshot = repository.get(id); mutable.value = EditorState(snapshot = snapshot, missing = snapshot == null) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutable.value = EditorState(missing = true, error = "读取失败，请返回后重试") }
    } }
    fun save(todo: TodoEntity, reminders: List<ReminderEntity>? = null) {
        if (mutable.value.saving) return
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { repository.save(todo, reminders); mutable.update { it.copy(saving = false, saved = true,
                savedWithReminder = (reminders ?: it.snapshot?.reminders.orEmpty()).isNotEmpty() && todo.status == 0) } }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { mutable.update { it.copy(saving = false, error = e.message ?: "请检查输入") } }
            catch (_: Exception) { mutable.update { it.copy(saving = false, error = "保存失败，请重试") } }
        }
    }
}
