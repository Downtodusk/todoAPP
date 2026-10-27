package com.example.todo.reminder

import androidx.test.platform.app.InstrumentationRegistry
import com.example.todo.App
import com.example.todo.data.local.ReminderEntity
import com.example.todo.data.local.TodoEntity
import com.example.todo.data.settings.ReminderMode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in fixture preparation, never a claim that a background scenario passed. */
class DeviceScenarioFixtureTest {
    @Test fun manageFixture(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val action = args.getString("todoScenarioAction")
        assumeTrue("Opt-in manual scenario fixture", action in listOf("prepare", "cleanup", "configure"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = (context.applicationContext as App).container
        val file = File(context.cacheDir, "reminder-device-scenario.json")
        context.deleteSharedPreferences("device_alarm_experiment")
        fun parseMode(value: String) = when (value) {
            "exact_idle" -> ReminderMode.STANDARD
            "alarm_clock" -> ReminderMode.ALARM_CLOCK
            else -> error("Unknown scenario mode")
        }
        if (action == "configure") {
            app.settings.reminderMode(parseMode(requireNotNull(args.getString("todoScenarioMode"))))
            return@runBlocking
        }
        if (action == "cleanup") {
            if (file.exists()) {
                val fixture = JSONObject(file.readText())
                fixture.optString("previousMode").takeIf { it.isNotEmpty() }?.let {
                    app.settings.reminderMode(ReminderMode.valueOf(it))
                }
                val id = fixture.getLong("todoId")
                val stored = app.todos.get(id)
                check(stored == null || stored.todo.title == fixture.getString("title"))
                app.todos.delete(id)
                app.notifications.cancel(id)
                check(file.delete())
            }
        } else {
            check(!file.exists()) { "Clean up the previous scenario before preparing another" }
            check(app.notifications.permitted() && app.alarms.permitted() && app.notifications.channelEnabled())
            val seconds = (args.getString("todoScenarioSeconds")?.toLong() ?: 90L).also { require(it in 60..600) }
            val label = args.getString("todoScenarioLabel") ?: "后台提醒"
            val previousMode = app.settings.settings.first().reminderMode
            val mode = args.getString("todoScenarioMode") ?: if (previousMode == ReminderMode.ALARM_CLOCK) "alarm_clock" else "exact_idle"
            app.settings.reminderMode(parseMode(mode))
            val title = "设备验收：$label"
            val trigger = System.currentTimeMillis() + seconds * 1_000
            val id = app.todos.save(TodoEntity(title = title, note = "自动准备的设备验收事项；测试后清理。", dueAt = trigger),
                listOf(ReminderEntity(todoId = 0, triggerAt = trigger)))
            val reminder = app.todos.get(id)!!.reminders.single()
            file.writeText(JSONObject().put("todoId", id).put("reminderId", reminder.id)
                .put("triggerAt", trigger).put("title", title).put("mode", mode).put("previousMode", previousMode.name).toString())
        }
    }
}
