package com.example.todo.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.todo.data.local.*
import com.example.todo.data.repo.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class TodoRepositoryTest {
    private lateinit var db: TodoDatabase
    private lateinit var repository: TodoRepository
    private val zone = ZoneId.of("UTC")
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TodoDatabase::class.java).build()
        repository = TodoRepository(db, Clock.fixed(Instant.parse("2026-02-28T10:00:00Z"), zone))
    }
    @After fun close() { db.close() }
    @Test fun deleteCascadesAndUndoRestoresMultipleReminders() = runBlocking {
        val id = repository.save(TodoEntity(title = "测试"), listOf(ReminderEntity(todoId = 0, triggerAt = 100), ReminderEntity(todoId = 0, triggerAt = 200)))
        val old = repository.get(id)!!
        val undo = repository.delete(id)!!
        assertNull(repository.get(id))
        assertTrue(db.reminders().forTodo(id).isEmpty())
        assertTrue(repository.undo(undo))
        assertEquals(old.reminders, repository.get(id)!!.reminders)
        assertFalse(repository.undo(undo))
    }
    @Test fun repeatRebuildsRemindersAndUndoRestoresAnchor() = runBlocking {
        val due = Instant.parse("2026-01-31T09:00:00Z").toEpochMilli()
        val id = repository.save(TodoEntity(title = "月末", dueAt = due, repeatRule = "MONTHLY"), listOf(ReminderEntity(todoId = 0, triggerAt = due - 600_000)), zone)
        val undo = repository.complete(id, zone)!!
        val next = repository.get(id)!!
        assertEquals(Instant.parse("2026-03-31T09:00:00Z").toEpochMilli(), next.todo.dueAt)
        assertEquals(31, next.todo.repeatAnchorDay)
        assertEquals(next.todo.dueAt!! - 600_000, next.reminders.single().triggerAt)
        assertTrue(repository.undo(undo))
        assertEquals(due, repository.get(id)!!.todo.dueAt)
    }
    @Test fun staleUndoCannotOverwriteNewEdit() = runBlocking {
        val id = repository.save(TodoEntity(title = "初始"))
        val undo = repository.complete(id)!!
        repository.save(repository.get(id)!!.todo.copy(title = "新标题"))
        assertFalse(repository.undo(undo))
        assertEquals("新标题", repository.get(id)!!.todo.title)
    }
    @Test fun staleSaveIsRejected() = runBlocking {
        val id = repository.save(TodoEntity(title = "初始"))
        val stale = repository.get(id)!!.todo
        repository.save(stale.copy(title = "已更新"))
        try { repository.save(stale.copy(title = "过期编辑")); fail("must reject stale edit") } catch (_: IllegalArgumentException) { }
    }
    @Test fun editingOnlyTimeRetainsMonthlyAnchor() = runBlocking {
        val due = Instant.parse("2026-02-28T09:00:00Z").toEpochMilli()
        val id = db.todos().insert(TodoEntity(title = "月末", dueAt = due, repeatRule = "MONTHLY", repeatAnchorDay = 31))
        val original = repository.get(id)!!.todo
        repository.save(original.copy(dueAt = due + 3_600_000), zone = zone)
        assertEquals(31, repository.get(id)!!.todo.repeatAnchorDay)
        repository.complete(id, zone)
        assertEquals(Instant.parse("2026-03-31T10:00:00Z").toEpochMilli(), repository.get(id)!!.todo.dueAt)
    }
    @Test fun importBatchCommitsAllRowsBeforeSchedulingOnce() = runBlocking {
        var callbacks = 0
        val repo = TodoRepository(db, onChanged = {
            callbacks++
            assertEquals(2, db.todos().observeAll().first().size)
            assertEquals(2, db.reminders().pending().size)
        })
        val items = listOf("第一项", "第二项").map { TodoEntity(title = it) to listOf(ReminderEntity(todoId = 0, triggerAt = 100)) }
        val ids = repo.importBatch(items, zone)
        assertEquals(2, ids.distinct().size)
        assertEquals(1, callbacks)
    }
    @Test fun invalidSecondImportRollsBackEntireBatchWithoutScheduling() = runBlocking {
        var callbacks = 0
        val repo = TodoRepository(db, onChanged = { callbacks++ })
        try { repo.importBatch(listOf(TodoEntity(title = "有效") to emptyList(), TodoEntity(title = " ") to emptyList()), zone); fail("must reject") }
        catch (_: IllegalArgumentException) { }
        assertTrue(db.todos().observeAll().first().isEmpty())
        assertTrue(db.reminders().pending().isEmpty())
        assertEquals(0, callbacks)
    }
}
