package com.example.todo.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todo.ai.*
import com.example.todo.data.local.*
import com.example.todo.data.repo.TodoRepository
import com.example.todo.data.settings.SettingsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

data class ImportState(val input: String = "", val loading: Boolean = false, val parsed: Boolean = false,
    val drafts: List<TodoDraft> = emptyList(), val error: String? = null, val local: Boolean = false,
    val saving: Boolean = false, val added: Int = 0, val addedWithReminder: Boolean = false)

class AiImportViewModel(private val parser: NoticeParseRepository, private val todos: TodoRepository,
    private val settings: SettingsStore) : ViewModel() {
    private val mutable = MutableStateFlow(ImportState())
    val state = mutable.asStateFlow()
    private var parsing: Job? = null
    fun input(value: String) { if (mutable.value.saving) return; parsing?.cancel(); mutable.value = ImportState(input = value) }
    fun update(draft: TodoDraft) { if (!mutable.value.saving) mutable.update { state -> state.copy(drafts = state.drafts.map { if (it.id == draft.id) draft else it }) } }
    fun recognize(local: Boolean = false) {
        if (mutable.value.loading || mutable.value.saving || mutable.value.input.isBlank()) return
        val raw = mutable.value.input
        parsing = viewModelScope.launch {
            mutable.update { it.copy(loading = true, error = null, parsed = false, drafts = emptyList()) }
            try {
                val result = if (local) Result.success(withContext(Dispatchers.Default) { parser.local(raw) }) else parser.parse(raw)
                val minute = settings.settings.first().allDayMinute
                val now = ZonedDateTime.now()
                result.fold(onSuccess = { drafts ->
                    mutable.update { it.copy(loading = false, parsed = true, local = local,
                        drafts = drafts.map { draft ->
                            val alarm = draft.due?.let { due -> if (draft.allDay) due.toLocalDate().atTime(minute / 60, minute % 60) else due }
                            draft.copy(reminder = draft.reminder && alarm?.atZone(now.zone)?.toInstant()?.isAfter(now.toInstant()) == true)
                        }) }
                }, onFailure = { error -> mutable.update { it.copy(loading = false, error = error.aiMessage()) } })
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(loading = false, error = "识别未完成，请重试") } }
        }
    }
    fun cancel() { parsing?.cancel(); mutable.update { it.copy(loading = false) } }
    fun addSelected() {
        if (mutable.value.saving || mutable.value.added > 0) return
        val selected = mutable.value.drafts.filter { it.selected }
        if (selected.isEmpty()) return
        if (selected.any { it.title.trim().isEmpty() }) { mutable.update { it.copy(error = "请为选中的待办填写标题") }; return }
        mutable.update { it.copy(saving = true, error = null) }
        val source = prepareNotice(mutable.value.input).text
        viewModelScope.launch {
            try {
                val preferences = settings.settings.first()
                val zone = ZoneId.systemDefault()
                val items = selected.map { draft ->
                    val due = draft.due?.let { if (draft.allDay) it.toLocalDate().atStartOfDay() else it }
                    val todo = TodoEntity(title = draft.title.trim(), note = draft.note.ifBlank { null },
                        location = draft.location.ifBlank { null }, dueAt = due?.atZone(zone)?.toInstant()?.toEpochMilli(),
                        allDay = draft.allDay && due != null, allDayDate = if (draft.allDay) due?.toLocalDate()?.toString() else null,
                        source = 1, sourceText = if (preferences.saveSource) source else null)
                    val reminders = if (draft.reminder && due != null) {
                        val localTime = if (draft.allDay) due.toLocalDate().atTime(preferences.allDayMinute / 60, preferences.allDayMinute % 60) else due
                        listOf(ReminderEntity(todoId = 0, triggerAt = localTime.atZone(zone).toInstant().toEpochMilli(),
                            allDayMinute = if (draft.allDay) preferences.allDayMinute else null))
                    } else emptyList()
                    todo to reminders
                }
                todos.importBatch(items, zone)
                mutable.update { it.copy(saving = false, added = items.size, addedWithReminder = items.any { item -> item.second.isNotEmpty() }) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(saving = false, error = "保存未完成，请返回列表核对后重试") } }
        }
    }
    fun consumed() { mutable.value = ImportState() }
}
