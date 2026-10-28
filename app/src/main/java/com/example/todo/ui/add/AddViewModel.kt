package com.example.todo.ui.add

import com.example.todo.R
import com.example.todo.i18n.t
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
    val error: String? = null, val saved: Boolean = false, val missing: Boolean = false, val savedWithReminder: Boolean = false, val savedId: Long? = null)

class AddViewModel(private val repository: TodoRepository, private val id: Long?,
    initialSnapshot: TodoSnapshot? = null, initialLoaded: Boolean = false) : ViewModel() {
    private val mutable = MutableStateFlow(EditorState(loading = id != null && !initialLoaded,
        snapshot = initialSnapshot, missing = initialLoaded && initialSnapshot == null))
    val state = mutable.asStateFlow()
    init { if (id != null && !initialLoaded) viewModelScope.launch {
        try { val snapshot = repository.get(id); mutable.value = EditorState(snapshot = snapshot, missing = snapshot == null) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutable.value = EditorState(missing = true, error = t(R.string.could_not_load_go_back_and_try_again)) }
    } }
    fun save(todo: TodoEntity, reminders: List<ReminderEntity>? = null) {
        if (mutable.value.saving) return
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { val savedId = repository.save(todo, reminders); mutable.update { it.copy(saving = false, saved = true, savedId = savedId,
                savedWithReminder = (reminders ?: it.snapshot?.reminders.orEmpty()).isNotEmpty() && todo.status == 0) } }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { mutable.update { it.copy(saving = false, error = e.message ?: t(R.string.check_your_input)) } }
            catch (_: Exception) { mutable.update { it.copy(saving = false, error = t(R.string.could_not_save_try_again)) } }
        }
    }
}
