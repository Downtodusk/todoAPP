package com.example.todo.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.todo.App
import com.example.todo.MainActivity
import com.example.todo.data.local.TodoEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File

/** Explicit, opt-in system input test. Unlike dispatcher tests, events pass through System UI. */
class NativeBackGestureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun systemEdgeSwipeShrinksCancelsAndReturns() {
        Assume.assumeTrue("Opt in only on the isolated emulator with gesture navigation enabled",
            InstrumentationRegistry.getArguments().getString("nativeGesture") == "true")
        Assert.assertEquals(2, Settings.Secure.getInt(compose.activity.contentResolver, "navigation_mode", -1))
        val app = (compose.activity.application as App).container
        val original = runBlocking { app.settings.settings.first() }
        val title = "原生手势验证 / Native edge"
        val id = runBlocking { app.settings.motion(false); app.todos.all.first().filter { it.title == title }.forEach { app.todos.delete(it.id) }; app.todos.save(TodoEntity(title = title), emptyList()) }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val screen = requireNotNull(automation.takeScreenshot())
        val w = screen.width.toFloat(); val y = screen.height * .6f; screen.recycle()
        var down = 0L
        fun inject(action: Int, x: Float, verify: Boolean = true) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try { val accepted = automation.injectInputEvent(event, true); if (verify) Assert.assertTrue("Input action $action was not accepted", accepted) } finally { event.recycle() }
        }
        fun drag(right: Boolean) {
            down = SystemClock.uptimeMillis()
            val start = if (right) w - 1 else 1f
            val delta = w * .3f * if (right) -1 else 1
            inject(MotionEvent.ACTION_DOWN, start)
            repeat(12) { index -> SystemClock.sleep(20); inject(MotionEvent.ACTION_MOVE, start + delta * (index + 1) / 12) }
            compose.waitForIdle()
        }
        fun capture(name: String) {
            val image = requireNotNull(automation.takeScreenshot())
            File(compose.activity.getExternalFilesDir(null), "ui-043/$name.png").apply { parentFile!!.mkdirs() }
                .outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
        try {
            compose.waitUntil(5_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(title).performClick(); compose.waitForIdle()
            val full = compose.onNodeWithTag("detail-motion-frame").fetchSemanticsNode().boundsInRoot.width
            drag(false)
            val scaled = compose.onNodeWithTag("detail-motion-frame").fetchSemanticsNode().boundsInRoot.width
            capture("native-left-held")
            Assert.assertTrue("Real System UI progress should shrink the card: $scaled / $full", scaled < full * .995f)
            repeat(12) { index -> SystemClock.sleep(20); inject(MotionEvent.ACTION_MOVE, 1f + w * .3f * (11 - index) / 12) }
            inject(MotionEvent.ACTION_UP, 1f)
            compose.waitUntil(5_000) { kotlin.math.abs(full - compose.onNodeWithTag("detail-motion-frame").fetchSemanticsNode().boundsInRoot.width) < 1f }
            drag(true)
            capture("native-right-held")
            inject(MotionEvent.ACTION_UP, w * .7f)
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("detail-motion-frame").fetchSemanticsNodes().isEmpty() }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("添加待办").assertIsDisplayed()
            compose.onNodeWithText(title).assertIsDisplayed()
        } finally {
            inject(MotionEvent.ACTION_CANCEL, 1f, verify = false)
            runBlocking { app.todos.delete(id); app.settings.motion(original.reduceMotion) }
        }
    }
}
