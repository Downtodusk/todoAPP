package com.example.todo.ui

import android.app.NotificationManager
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.todo.App
import com.example.todo.MainActivity
import com.example.todo.data.local.TodoEntity
import com.example.todo.data.local.ReminderEntity
import com.example.todo.data.settings.UserSettings
import com.example.todo.reminder.NotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File
import java.time.*

class PersonalizationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = (compose.activity.application as App).container
    private lateinit var original: UserSettings
    private val ids = mutableListOf<Long>()
    private val allDayTitle = "阅读与思考 Reading time"
    private val timedTitle = "Project review"
    @Before fun prepare() = runBlocking {
        original = app.settings.settings.first()
        app.settings.theme("LIGHT"); app.settings.motion(false); app.settings.accent(0xFFA8583D)
        val today = LocalDate.now(); val zone = ZoneId.systemDefault()
        ids += app.todos.save(TodoEntity(title = allDayTitle, dueAt = today.atStartOfDay(zone).toInstant().toEpochMilli(), allDay = true, allDayDate = today.toString()), emptyList())
        ids += app.todos.save(TodoEntity(title = timedTitle, dueAt = today.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()), emptyList())
    }
    @After fun cleanup() {
        compose.mainClock.autoAdvance = true
        runBlocking {
            ids.forEach { app.notifications.cancel(it); app.todos.delete(it) }
            app.settings.accent(original.accentColor); app.settings.dynamic(original.dynamicColor)
            app.settings.theme(original.theme); app.settings.motion(original.reduceMotion)
        }
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN")) }
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        compose.waitForIdle(); InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(250)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val path = File(compose.activity.getExternalFilesDir(null), "ui-040/$name.png").apply { parentFile!!.mkdirs() }
        path.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    private fun homeReady() = compose.waitUntil(8_000) { compose.onAllNodesWithText(allDayTitle).fetchSemanticsNodes().isNotEmpty() }
    private fun back() { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); compose.waitForIdle() }
    private fun setWheel(tag: String, value: Float) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(value) } }
    @Test fun allDayAndTimedTasksShareTitleAndCheckboxColumns() {
        homeReady()
        val allDay = compose.onNodeWithContentDescription("完成 $allDayTitle").performScrollTo().fetchSemanticsNode().boundsInRoot.left
        val timed = compose.onNodeWithContentDescription("完成 $timedTitle").performScrollTo().fetchSemanticsNode().boundsInRoot.left
        Assert.assertEquals(allDay, timed, .5f)
        val a = compose.onNodeWithText(allDayTitle).performScrollTo().fetchSemanticsNode().boundsInRoot.left
        val b = compose.onNodeWithText(timedTitle).performScrollTo().fetchSemanticsNode().boundsInRoot.left
        Assert.assertEquals(a, b, .5f)
        compose.onNodeWithText("23:59").assertIsDisplayed()
        capture("home-aligned-zh")
    }
    @Test fun wheelSupportsMidnightLastMinuteAndCancelWithoutKeyboard() {
        homeReady(); compose.onNodeWithText(allDayTitle).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("editor-calendar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("时间", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("time-minute").performTouchInput { swipeUp(durationMillis = 450) }
        compose.waitUntil(3_000) { compose.onNodeWithTag("time-minute").fetchSemanticsNode().config[SemanticsProperties.StateDescription] != "00" }
        Assert.assertFalse(compose.activity.window.decorView.let { ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime()) } == true)
        setWheel("time-hour", 23f); setWheel("time-minute", 59f)
        capture("time-wheel-zh")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("全天", substring = false).assertIsDisplayed()
        compose.onNodeWithText("时间", substring = false).performClick()
        setWheel("time-hour", 0f); setWheel("time-minute", 0f)
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText("00:00").assertIsDisplayed()
        compose.onNodeWithText("时间", substring = false).performClick()
        setWheel("time-hour", 23f); setWheel("time-minute", 59f)
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { runBlocking { app.todos.get(ids[0])!!.todo.allDay.not() } }
        val saved = runBlocking { app.todos.get(ids[0])!!.todo }
        Assert.assertEquals(LocalTime.of(23, 59), Instant.ofEpochMilli(saved.dueAt!!).atZone(ZoneId.systemDefault()).toLocalTime())
    }
    @Test fun languageSwitchRecreatesScreenAndLocalizesNotificationsWithoutChangingContent() {
        homeReady(); compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithTag("language-setting").performClick()
        compose.onNodeWithTag("language-en").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Appearance").fetchSemanticsNodes().isNotEmpty() }
        capture("settings-en")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithText(allDayTitle).assertIsDisplayed()
        capture("home-en")
        compose.onNodeWithText(allDayTitle).performClick()
        compose.onNodeWithText("Task details").assertIsDisplayed()
        compose.onNodeWithText("Time", substring = false).performScrollTo().performClick()
        capture("time-wheel-en")
        compose.onNodeWithText("Cancel").performClick()
        capture("editor-en")
        val todo = runBlocking { app.todos.get(ids[0])!!.todo }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("pm grant com.example.todo android.permission.POST_NOTIFICATIONS").close()
        Assert.assertTrue(app.notifications.show(todo, ReminderEntity(todoId = todo.id, triggerAt = System.currentTimeMillis())))
        val manager = compose.activity.getSystemService(NotificationManager::class.java)
        val notification = manager.activeNotifications.first { it.tag == "todo-${todo.id}" }.notification
        Assert.assertEquals("Complete", notification.actions[0].title.toString())
        Assert.assertEquals("Snooze 10 minutes", notification.actions[1].title.toString())
        Assert.assertEquals(allDayTitle, notification.extras.getString("android.title"))
        Assert.assertEquals("Task reminders", manager.getNotificationChannel(NotificationHelper.Channel).name.toString())
    }
    @Test fun customPaletteValidatesInputAndSurvivesRecreationInDarkMode() {
        homeReady(); compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithTag("accent-${0xFF3D6F9E}").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { app.settings.settings.first().accentColor == 0xFF3D6F9E } }
        capture("palette-presets-zh")
        compose.onNodeWithTag("custom-accent").performScrollTo().performClick()
        compose.onNodeWithTag("accent-hex").performTextReplacement("#XYZ")
        compose.onNodeWithText("应用", substring = false).assertIsNotEnabled()
        compose.onNodeWithTag("accent-hex").performTextReplacement("#008866")
        back() // Dismiss the hex input keyboard.
        capture("custom-color-zh")
        compose.onNodeWithText("应用", substring = false).performClick()
        compose.waitUntil(5_000) { runBlocking { app.settings.settings.first().accentColor == 0xFF008866 } }
        Assert.assertFalse(runBlocking { app.settings.settings.first().dynamicColor })
        compose.onNodeWithTag("theme-DARK").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { app.settings.settings.first().theme == "DARK" } }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("返回").performClick()
        homeReady(); capture("home-custom-dark-zh")
        Assert.assertEquals(0xFF008866, runBlocking { app.settings.settings.first().accentColor })
    }
}
