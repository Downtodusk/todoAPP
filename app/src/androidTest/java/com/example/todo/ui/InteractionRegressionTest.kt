package com.example.todo.ui

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.todo.App
import com.example.todo.MainActivity
import com.example.todo.data.local.TodoEntity
import com.example.todo.data.settings.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File
import java.time.*

class InteractionRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = (compose.activity.application as App).container
    private lateinit var originalSettings: UserSettings
    private val ids = mutableListOf<Long>()
    private val prefix = "交互修复_${System.nanoTime()}"
    private val title = "检查详情标题尺寸"
    @Before fun prepare() = runBlocking {
        originalSettings = app.settings.settings.first()
        app.settings.motion(false); app.settings.theme("LIGHT"); app.settings.dynamic(false)
        ids += app.todos.save(TodoEntity(title = title), emptyList())
    }
    @After fun cleanup() = runBlocking {
        compose.mainClock.autoAdvance = true
        ids.forEach { app.todos.delete(it) }
        app.todos.all.first().filter { it.title.startsWith(prefix) }.forEach { app.todos.delete(it.id) }
        app.settings.motion(originalSettings.reduceMotion); app.settings.theme(originalSettings.theme); app.settings.dynamic(originalSettings.dynamicColor)
    }
    private fun back() { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK) }
    private fun capture(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(150)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val file = File(compose.activity.getExternalFilesDir(null), "ui-031/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun addCancelAndImeSaveWorkInBothMotionModes() {
        for (reduced in listOf(false, true)) {
            runBlocking { app.settings.motion(reduced) }
            compose.onNodeWithContentDescription("添加待办").performClick()
            compose.onNodeWithText("记下一件事").assertIsDisplayed()
            back() // Empty sheet, no keyboard and no save.
            compose.waitUntil(5_000) { compose.onAllNodesWithText("记下一件事").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithContentDescription("添加待办").performClick()
            compose.onNodeWithTag("todo-title").performClick().performTextInput("${prefix}_cancel_$reduced")
            compose.waitForIdle()
            back() // Android first dismisses the keyboard when it is visible.
            compose.waitForIdle()
            if (compose.onAllNodesWithText("记下一件事").fetchSemanticsNodes().isNotEmpty()) back()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("记下一件事").fetchSemanticsNodes().isEmpty() }
            Assert.assertFalse(runBlocking { app.todos.all.first().any { it.title == "${prefix}_cancel_$reduced" } })
            compose.onNodeWithContentDescription("添加待办").performClick()
            compose.onNodeWithTag("todo-title").performClick().performTextInput("${prefix}_save_$reduced")
            compose.onNodeWithTag("todo-title").performImeAction()
            compose.waitUntil(8_000) { compose.onAllNodesWithText("记下一件事").fetchSemanticsNodes().isEmpty() }
            Assert.assertEquals(1, runBlocking { app.todos.all.first().count { it.title == "${prefix}_save_$reduced" } })
            compose.onNodeWithContentDescription("添加待办").assertIsDisplayed()
        }
    }
    @Test fun calendarIsTheOnlyDateEditorAndCanClearDate() {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText("日期", substring = false).assertDoesNotExist()
        compose.onNodeWithContentDescription("选择日期 ${LocalDate.now()}").performClick()
        compose.onNodeWithText("清除日期", substring = false).performClick()
        compose.onNodeWithText("清除日期", substring = false).assertDoesNotExist()
        compose.onNodeWithContentDescription("选择日期 ${LocalDate.now()}").performClick()
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { runBlocking { app.todos.get(ids.single())?.todo?.allDayDate == LocalDate.now().toString() } }
    }
    @Test fun revealMenusAndSampleDetailTransitions() {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        val currentDay = compose.onNode(hasContentDescription(LocalDate.now().toString(), substring = true))
        compose.mainClock.autoAdvance = false
        currentDay.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(250)
        capture("week-pressed")
        currentDay.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        val row = compose.onNodeWithTag("todo-row-${ids.single()}")
        row.performScrollTo().performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("确认完成 $title").assertIsDisplayed()
        capture("swipe-left")
        compose.onNodeWithText(title).performClick()
        row.performTouchInput { swipeRight() }
        compose.onNodeWithContentDescription("确认删除 $title").assertIsDisplayed()
        capture("swipe-right")
        compose.onNodeWithText(title).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("确认删除 $title").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(title).performClick()
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("待办详情").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeBy(80)
        capture("detail-enter-80ms")
        compose.mainClock.advanceTimeBy(100)
        capture("detail-enter-180ms")
        compose.mainClock.advanceTimeBy(350)
        capture("detail-settled")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.mainClock.advanceTimeBy(80)
        capture("detail-return-80ms")
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("添加待办").assertIsDisplayed()
    }
}
