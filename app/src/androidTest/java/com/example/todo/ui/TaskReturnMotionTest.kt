package com.example.todo.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.todo.App
import com.example.todo.MainActivity
import com.example.todo.data.local.TodoEntity
import com.example.todo.data.settings.UserSettings
import com.example.todo.ui.components.ReturnMotionStateKey
import com.example.todo.ui.components.TaskReturnState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class TaskReturnMotionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = (compose.activity.application as App).container
    private lateinit var original: UserSettings
    private val ids = mutableListOf<Long>()
    private val title = "返回落点测试 / Return destination"
    @Before fun prepare() = runBlocking {
        original = app.settings.settings.first()
        app.settings.motion(false); app.settings.theme("LIGHT"); app.settings.dynamic(false)
        ids += app.todos.save(TodoEntity(title = title), emptyList())
    }
    @After fun cleanup() = runBlocking {
        compose.mainClock.autoAdvance = true
        ids.forEach { app.todos.delete(it) }
        app.todos.all.first().filter { it.title.startsWith("返回新建测试") }.forEach { app.todos.delete(it.id) }
        app.settings.motion(original.reduceMotion); app.settings.theme(original.theme); app.settings.dynamic(original.dynamicColor)
    }
    private fun ready() = compose.waitUntil(5_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
    private fun overlay(): TaskReturnState {
        try {
            compose.waitUntil(8_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithTag("task-return-overlay").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Exception) {
            sample("overlay-missing")
            compose.onAllNodes(isRoot()).fetchSemanticsNodes().forEach { android.util.Log.e("ReturnTest", it.toString()) }
            throw e
        }
        val motion = compose.onNodeWithTag("task-return-overlay").fetchSemanticsNode().config[ReturnMotionStateKey]
        compose.waitUntil(5_000) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle(); motion.animationStarted }
        return motion
    }
    private fun advance(milliseconds: Long) {
        repeat(((milliseconds + 15) / 16).toInt()) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
    }
    private fun sample(name: String) {
        compose.waitForIdle(); InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(80)
        val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(compose.activity.getExternalFilesDir(null), "ui-043/$name.png").apply { parentFile!!.mkdirs() }
            .outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    private fun finish() {
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("task-return-overlay").assertDoesNotExist()
        compose.onNodeWithContentDescription("添加待办").assertIsDisplayed()
    }
    private fun assertSameRect(expected: Rect, actual: Rect) {
        Assert.assertEquals(expected.left, actual.left, 1.5f); Assert.assertEquals(expected.top, actual.top, 1.5f)
        Assert.assertEquals(expected.width, actual.width, 1.5f); Assert.assertEquals(expected.height, actual.height, 1.5f)
    }
    @Test fun buttonBackShrinksThenFadesAndLandsOnActualRow() {
        ready()
        val rowTag = "todo-row-${ids.single()}"
        val expected = compose.onNodeWithTag(rowTag).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText(title).performClick(); compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("返回").performClick()
        val motion = overlay()
        compose.waitUntil(5_000) { compose.mainClock.advanceTimeByFrame(); motion.targetBounds != null }
        assertSameRect(expected, motion.targetBounds!!)
        advance(112)
        val initial = motion.request!!.from
        Assert.assertTrue("Button Back must visibly shrink without progress events", motion.drawnBounds!!.width <= initial.width * .92f)
        sample("button-shrunk")
        var sawDetail = false; var sawRow = false
        repeat(18) {
            advance(16)
            compose.waitForIdle()
            Assert.assertTrue("Details and row must never overlap", motion.detailOpacity == 0f || motion.rowOpacity == 0f)
            sawDetail = sawDetail || motion.detailOpacity > 0f
            sawRow = sawRow || motion.rowOpacity > 0f
            if (it == 4) sample("detail-fading-down")
            if (it == 13) sample("row-fading-in")
        }
        Assert.assertTrue(sawDetail); Assert.assertTrue(sawRow)
        finish()
        assertSameRect(expected, compose.onNodeWithTag(rowTag).fetchSemanticsNode().boundsInRoot)
        sample("landed-on-row")
    }
    @Test fun changedDateNotVisibleFallsBackToCenter() {
        ready(); compose.onNodeWithText(title).performClick(); compose.waitForIdle()
        runBlocking {
            val todo = app.todos.get(ids.single())!!.todo
            val date = LocalDate.now().plusDays(10)
            app.todos.save(todo.copy(allDay = true, allDayDate = date.toString(), dueAt = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()), null)
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("返回").performClick()
        val motion = overlay()
        advance(240)
        Assert.assertNull(motion.targetBounds)
        sample("invisible-target-center")
        finish()
        compose.onNodeWithText(title).assertDoesNotExist()
    }
    @Test fun cancelledCreationCentersAndSavedCreationTargetsItsNewRow() {
        ready()
        compose.onNodeWithContentDescription("添加待办").performClick(); compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        androidx.test.espresso.Espresso.pressBack()
        val cancel = overlay()
        advance(250)
        Assert.assertNull(cancel.request!!.todoId); Assert.assertNull(cancel.targetBounds)
        sample("cancel-create-center")
        finish()
        compose.onNodeWithContentDescription("添加待办").performClick()
        compose.onNodeWithTag("todo-title").performTextReplacement("返回新建测试")
        compose.onNodeWithTag("todo-title").assertTextContains("返回新建测试")
        compose.runOnUiThread { compose.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        val saved = overlay()
        compose.waitUntil(5_000) { compose.mainClock.advanceTimeByFrame(); saved.targetBounds != null }
        val id = saved.request!!.todoId!!
        val destination = saved.targetBounds!!
        advance(320)
        sample("new-task-row-fading-in")
        finish()
        compose.onNodeWithText("返回新建测试").assertIsDisplayed()
        assertSameRect(destination, compose.onNodeWithTag("todo-row-$id").fetchSemanticsNode().boundsInRoot)
    }
}
