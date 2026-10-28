package com.example.todo.ui

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
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

class DetailTransitionRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = (compose.activity.application as App).container
    private lateinit var original: UserSettings
    private val ids = mutableListOf<Long>()
    private val title = "复盘项目进展并整理下一阶段工作计划 / Review the next steps"
    private val completedTitles = listOf("已完成的全天事项", "已完成的定时事项")
    @Before fun prepare() = runBlocking {
        original = app.settings.settings.first()
        app.settings.motion(false); app.settings.theme("LIGHT"); app.settings.dynamic(false)
        val today = LocalDate.now(); val zone = ZoneId.systemDefault()
        ids += app.todos.save(TodoEntity(title = title), emptyList())
        completedTitles.forEachIndexed { index, text ->
            ids += app.todos.save(TodoEntity(title = text, status = 1, completedAt = System.currentTimeMillis(),
                dueAt = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), allDay = index == 0,
                allDayDate = if (index == 0) today.toString() else null), emptyList())
        }
    }
    @After fun cleanup() = runBlocking {
        compose.mainClock.autoAdvance = true
        ids.forEach { app.todos.delete(it) }
        app.settings.motion(original.reduceMotion); app.settings.theme(original.theme); app.settings.dynamic(original.dynamicColor)
    }
    private fun ready() = compose.waitUntil(5_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
    private fun capture(name: String) {
        compose.waitForIdle(); InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(150)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val file = File(compose.activity.getExternalFilesDir(null), "ui-041/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    private fun titleLayout(): TextLayoutResult {
        val values = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("todo-title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { Assert.assertTrue(it(values)) }
        return values.first { it.layoutInput.text.text == title }
    }
    @Test fun completedRowsStartAtListEdgeWithoutThePendingTimeGutter() {
        ready()
        val pendingLeft = compose.onNodeWithContentDescription("完成 $title").performScrollTo().fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithContentDescription("展开已完成", substring = true).performScrollTo().performClick()
        val lefts = completedTitles.map { text ->
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("重新打开 $text"))
            compose.onNodeWithContentDescription("重新打开 $text").fetchSemanticsNode().boundsInRoot.left
        }
        Assert.assertEquals(lefts[0], lefts[1], .5f)
        Assert.assertTrue("Completed items should reclaim the leading column", pendingLeft - lefts[0] >= 48 * compose.activity.resources.displayMetrics.density)
        compose.mainClock.advanceTimeBy(500)
        capture("completed-no-gutter")
        compose.onNodeWithContentDescription("更多操作 ${completedTitles[1]}").performClick()
        compose.onNodeWithText("重新打开", substring = false).assertIsDisplayed()
    }
    @Test fun longTitleKeepsItsLayoutThroughoutEntryAndReturn() {
        ready()
        for (reduced in listOf(false, true)) {
            runBlocking { app.settings.motion(reduced) }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText(title).performClick()
            compose.waitUntil(5_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithTag("todo-title").fetchSemanticsNodes().isNotEmpty()
            }
            compose.mainClock.advanceTimeBy(48)
            val early = titleLayout()
            capture("detail-enter-48ms-$reduced")
            compose.mainClock.advanceTimeBy(96)
            capture("detail-enter-144ms-$reduced")
            compose.mainClock.advanceTimeBy(500)
            val settled = titleLayout()
            Assert.assertEquals(settled.size, early.size)
            Assert.assertEquals(settled.lineCount, early.lineCount)
            Assert.assertEquals(settled.layoutInput.style.fontSize, early.layoutInput.style.fontSize)
            capture("detail-settled-$reduced")
            compose.onNodeWithContentDescription("返回").performClick()
            compose.mainClock.advanceTimeBy(48)
            capture("detail-return-48ms-$reduced")
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.onNodeWithContentDescription("添加待办").assertIsDisplayed()
        }
    }
}
