package com.example.todo.ui

import android.graphics.Bitmap
import androidx.activity.BackEventCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.todo.App
import com.example.todo.BuildConfig
import com.example.todo.MainActivity
import com.example.todo.data.local.TodoEntity
import com.example.todo.data.settings.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File

class DetailBackGestureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = (compose.activity.application as App).container
    private lateinit var original: UserSettings
    private var id = 0L
    private val title = "手势测试 / Gesture draft"
    @Before fun prepare() = runBlocking {
        original = app.settings.settings.first()
        app.settings.motion(false); app.settings.theme("LIGHT"); app.settings.dynamic(false)
        id = app.todos.save(TodoEntity(title = title), emptyList())
    }
    @After fun cleanup() = runBlocking {
        compose.mainClock.autoAdvance = true
        app.todos.delete(id)
        app.settings.motion(original.reduceMotion); app.settings.theme(original.theme); app.settings.dynamic(original.dynamicColor)
    }
    private fun open() {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("todo-title").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun start(edge: Int, fraction: Float) {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 600f, 0f, edge)) }
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(160f, 600f, fraction, edge)) }
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(100)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val file = File(compose.activity.getExternalFilesDir(null), "ui-042/${BuildConfig.VERSION_NAME}-$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun bothEdgesCancelPreservesDraftThenCommitReturns() {
        for (edge in listOf(BackEventCompat.EDGE_LEFT, BackEventCompat.EDGE_RIGHT)) {
            open()
            compose.onNodeWithTag("todo-title").performTextReplacement("$title unsaved")
            compose.runOnUiThread {
                compose.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
                compose.activity.window.decorView.clearFocus()
            }
            compose.waitForIdle()
            val fullWidth = if (!BuildConfig.CLASSIC_DETAIL_MOTION) compose.onNodeWithTag("detail-motion-frame").fetchSemanticsNode().boundsInRoot.width else 0f
            start(edge, .8f)
            if (!BuildConfig.CLASSIC_DETAIL_MOTION) {
                val width = compose.onNodeWithTag("detail-motion-frame").fetchSemanticsNode().boundsInRoot.width
                capture("edge-$edge-before-assert")
                Assert.assertTrue("Card follows gesture, bounded at 90%: $width / $fullWidth", width in (fullWidth * .90f)..(fullWidth * .94f))
            }
            capture("edge-$edge-drag")
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
            compose.waitForIdle()
            compose.onNodeWithTag("todo-title").assertTextContains("$title unsaved")
            if (!BuildConfig.CLASSIC_DETAIL_MOTION) Assert.assertEquals(fullWidth, compose.onNodeWithTag("detail-motion-frame").fetchSemanticsNode().boundsInRoot.width, 1f)
            capture("edge-$edge-cancel")
            start(edge, .7f)
            compose.mainClock.autoAdvance = false
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.mainClock.advanceTimeBy(64)
            capture("edge-$edge-retract")
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.onNodeWithContentDescription("添加待办").assertIsDisplayed()
            Assert.assertEquals(title, runBlocking { app.todos.get(id)!!.todo.title })
        }
    }
    @Test fun incomingDetailDuringReturnIsNotPoppedByTheOldAnimation() {
        Assume.assumeFalse("Only B defers the pop until its own animation completes", BuildConfig.CLASSIC_DETAIL_MOTION)
        val launch = android.content.Intent(compose.activity.intent)
        val second = runBlocking { app.todos.save(TodoEntity(title = "incoming task"), emptyList()) }
        try {
            open()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithContentDescription("返回").performClick()
            compose.runOnUiThread {
                compose.activity.startActivity(android.content.Intent(compose.activity, MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("todoId", second))
            }
            compose.waitUntil(5_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodes(hasSetTextAction() and hasText("incoming task")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.onNode(hasSetTextAction() and hasText("incoming task")).assertIsDisplayed()
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnUiThread { compose.activity.intent = launch }
            runBlocking { app.todos.delete(second) }
        }
    }
    @Test fun dialogBackDoesNotLeaveDetailAndSaveReturnsOnce() {
        open()
        compose.onNodeWithText("时间", substring = false).performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("todo-title").assertIsDisplayed()
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("添加待办").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("添加待办").assertIsDisplayed()
    }
}
