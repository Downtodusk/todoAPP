package com.example.todo.reminder

import android.app.NotificationManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.todo.App
import com.example.todo.MainActivity
import com.example.todo.data.local.ReminderEntity
import com.example.todo.data.local.TodoEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real AlarmManager, receiver, notification and action PendingIntents on the target device. */
@RunWith(AndroidJUnit4::class)
class AlarmDeliveryDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = (context.applicationContext as App).container
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val fixtures = mutableListOf<Long>()
    private var activity: ActivityScenario<MainActivity>? = null

    @Before fun checkPermissionsForDeviceTest() {
        assertTrue("Enable notifications in reminder self-check before testing; some OEMs block shell permission grants", app.notifications.permitted())
        assertTrue("Enable exact alarms in reminder self-check before testing", app.alarms.permitted())
        assertTrue("Enable notification channel before testing", app.notifications.channelEnabled())
        // Establish foreground behavior separately from OEM background-policy acceptance.
        activity = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun cleanup(): Unit = runBlocking {
        fixtures.forEach { app.todos.delete(it); app.notifications.cancel(it) }
        activity?.close()
    }

    private suspend fun awaitCondition(block: suspend () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (!block()) {
            assertTrue("Device operation did not finish within 30 seconds", SystemClock.elapsedRealtime() < deadline)
            delay(100)
        }
    }

    @Test fun realExactAlarmPostsNotificationAndCompleteActionUpdatesDatabase() = runBlocking {
        val trigger = System.currentTimeMillis() + 3_000
        val id = app.todos.save(TodoEntity(title = "自动验收：准时提醒", dueAt = trigger),
            listOf(ReminderEntity(todoId = 0, triggerAt = trigger)))
        fixtures.add(id)
        awaitCondition { notifications.activeNotifications.any { it.tag == "todo-$id" } && app.todos.get(id)!!.reminders.single().fired }
        val notification = notifications.activeNotifications.single { it.tag == "todo-$id" }.notification
        assertEquals(2, notification.actions.size)
        notification.actions.single { it.title.toString() == "完成" }.actionIntent.send()
        awaitCondition { app.todos.get(id)!!.todo.status == 1 }
        awaitCondition { notifications.activeNotifications.none { it.tag == "todo-$id" } }
    }

    @Test fun realSnoozeActionCreatesOneFutureReminderAndDismissesNotification() = runBlocking {
        val trigger = System.currentTimeMillis() - 1_000
        val id = app.todos.save(TodoEntity(title = "自动验收：推迟提醒", dueAt = trigger),
            listOf(ReminderEntity(todoId = 0, triggerAt = trigger, fired = true)))
        fixtures.add(id)
        val snapshot = app.todos.get(id)!!
        assertTrue(app.notifications.show(snapshot.todo, snapshot.reminders.single()))
        awaitCondition { notifications.activeNotifications.any { it.tag == "todo-$id" } }
        val notification = notifications.activeNotifications.single { it.tag == "todo-$id" }.notification
        val action = notification.actions.single { it.title.toString() == "推迟10分钟" }.actionIntent
        val start = System.currentTimeMillis()
        action.send()
        awaitCondition { app.todos.get(id)!!.todo.revision > snapshot.todo.revision }
        action.send() // A duplicate tap on the same pending intent must be ignored.
        awaitCondition { notifications.activeNotifications.none { it.tag == "todo-$id" } }
        val latest = app.todos.get(id)!!
        assertEquals(trigger, latest.todo.dueAt)
        val postponed = latest.reminders.filter { it.triggerAt >= start + 600_000 }
        assertEquals(1, postponed.size)
        assertTrue(postponed.single().triggerAt <= System.currentTimeMillis() + 600_000)
    }
}
