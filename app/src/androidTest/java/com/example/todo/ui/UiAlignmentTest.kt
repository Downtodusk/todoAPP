package com.example.todo.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.todo.App
import com.example.todo.MainActivity
import com.example.todo.data.local.TodoEntity
import com.example.todo.data.settings.UserSettings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import java.time.*
import java.io.File

/** Synthetic fixtures only; captures real production UI. Never clears an existing database. */
class UiAlignmentTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val ids = mutableListOf<Long>()
    private lateinit var oldSettings: UserSettings
    private val app get() = (compose.activity.application as App).container
    private val today get() = LocalDate.now()
    private val zone get() = ZoneId.systemDefault()
    @Before fun seed() = runBlocking {
        oldSettings = app.settings.settings.first()
        app.settings.dynamic(false); app.settings.theme("LIGHT"); app.settings.motion(false)
        val samples = listOf(
            TodoEntity(title = "整理本周阅读笔记", dueAt = today.atStartOfDay(zone).toInstant().toEpochMilli(), allDay = true, allDayDate = today.toString()),
            TodoEntity(title = "Review proposal", dueAt = today.atTime(9, 30).atZone(zone).toInstant().toEpochMilli()),
            TodoEntity(title = "讨论下周的项目计划", location = "会议室 A", dueAt = today.atTime(14, 0).atZone(zone).toInstant().toEpochMilli(), note = "带上初稿与会议记录"),
            TodoEntity(title = "给自己留一点阅读时间"),
            TodoEntity(title = "完成今天的工作清单", status = 1, completedAt = System.currentTimeMillis(), dueAt = today.atTime(8, 0).atZone(zone).toInstant().toEpochMilli())
        )
        samples.forEach { ids += app.todos.save(it, emptyList()) }
    }
    @After fun cleanup() = runBlocking {
        ids.forEach { app.todos.delete(it) }
        if (::oldSettings.isInitialized) { app.settings.dynamic(oldSettings.dynamicColor); app.settings.theme(oldSettings.theme); app.settings.motion(oldSettings.reduceMotion) }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(350) // Let SurfaceFlinger present the last Compose frame.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val file = File(compose.activity.getExternalFilesDir(null), "ui-previews/$name.png").apply { parentFile!!.mkdirs() }
        val bitmap = requireNotNull(automation.takeScreenshot())
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun homeAndEditorRenderWithFixturesAndEditsPreserveFields() = recordFailure {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Review proposal").fetchSemanticsNodes().isNotEmpty() }
        screenshot("home-light")
        compose.onNodeWithText("讨论下周的项目计划").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("editor-calendar").fetchSemanticsNodes().isNotEmpty() }
        screenshot("editor-light")
        compose.onNodeWithTag("todo-title").performClick().performTextReplacement("讨论新的项目计划")
        screenshot("editor-keyboard")
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("讨论新的项目计划").fetchSemanticsNodes().isNotEmpty() }
        runBlocking {
            val value = app.todos.get(ids[2])!!.todo
            Assert.assertEquals("会议室 A", value.location)
            Assert.assertEquals("带上初稿与会议记录", value.note)
            Assert.assertEquals(today.atTime(14, 0).atZone(zone).toInstant().toEpochMilli(), value.dueAt)
        }
        compose.onNodeWithContentDescription("添加待办").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("记下一件事").fetchSemanticsNodes().isNotEmpty() }
        screenshot("quick-add-light")
    }
    @Test fun darkAndReducedMotionRender() = recordFailure {
        runBlocking { app.settings.theme("DARK"); app.settings.motion(true) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Review proposal").fetchSemanticsNodes().isNotEmpty() }
        screenshot("home-dark")
        compose.onNodeWithContentDescription("添加待办").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("记下一件事").fetchSemanticsNodes().isNotEmpty() }
        screenshot("quick-add-dark-reduced")
    }
    @Test fun calendarAndFieldDialogsPersistChanges() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("讨论下周的项目计划").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("讨论下周的项目计划").performClick()
        val selectedDate = today.withDayOfMonth(15)
        compose.onNodeWithContentDescription("选择日期 $selectedDate").performClick()
        compose.onNodeWithText("地点", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("地点（可选）").performTextReplacement("图书馆")
        compose.onNodeWithText("确定").performClick()
        compose.waitUntil(5_000) { androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) != true }
        compose.onNodeWithText("备注", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("备注（可选）").performTextReplacement("更新后的会议记录")
        compose.onNodeWithText("确定").performClick()
        compose.waitUntil(5_000) { androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) != true }
        compose.onNodeWithText("提醒", substring = false).performScrollTo().performClick()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("自定义").performClick()
        compose.onNodeWithText("分钟，最多 525600").performTextReplacement("17")
        compose.onNodeWithText("确定").performClick()
        compose.waitUntil(5_000) { androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) != true }
        compose.onNodeWithText("完成", substring = false).performClick()
        compose.onNodeWithText("重复", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("每月", substring = false).performClick()
        compose.onNodeWithText("完成", substring = false).performClick()
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { runBlocking { app.todos.get(ids[2])?.todo?.location == "图书馆" } }
        runBlocking {
            val saved = app.todos.get(ids[2])!!
            val due = selectedDate.atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
            Assert.assertEquals(due, saved.todo.dueAt)
            Assert.assertEquals("更新后的会议记录", saved.todo.note)
            Assert.assertEquals("MONTHLY", saved.todo.repeatRule)
            Assert.assertEquals(due - 17 * 60_000, saved.reminders.single().triggerAt)
        }
    }
    private fun recordFailure(block: () -> Unit) {
        try { block() } catch (error: Throwable) {
            File(compose.activity.getExternalFilesDir(null), "ui-previews/first-failure.txt").apply { parentFile!!.mkdirs() }.writeText(error.stackTraceToString())
            throw error
        }
    }
}
