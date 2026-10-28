package com.example.todo.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import java.util.regex.Pattern
import java.io.File
import android.os.SystemClock
import android.graphics.Rect

internal const val Target = "com.example.todo.benchmark"
private fun stableTask(ui: UiDevice): UiObject2 {
    val deadline = SystemClock.uptimeMillis() + 5_000
    var previousTitle: String? = null
    var previousBounds: Rect? = null
    var stableSince = SystemClock.uptimeMillis()
    while (SystemClock.uptimeMillis() < deadline) {
        val row = ui.findObjects(By.text(Pattern.compile("性能样例 [0-9]{2}"))).firstOrNull {
            val bounds = it.visibleBounds
            bounds.height() > 20 && bounds.top > ui.displayHeight * .4f && bounds.bottom < ui.displayHeight * .8f
        }
        val bounds = row?.visibleBounds
        val title = row?.text
        if (row != null && title == previousTitle && bounds == previousBounds) {
            if (SystemClock.uptimeMillis() - stableSince >= 300) return row
        } else {
            previousTitle = title; previousBounds = bounds
            stableSince = SystemClock.uptimeMillis()
        }
        SystemClock.sleep(100)
    }
    error("No stable, fully visible synthetic task after scrolling")
}
internal fun seed() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val launch = device.executeShellCommand("am start -W -S -n $Target/com.example.todo.benchmark.BenchmarkSeedActivity")
    check(device.wait(Until.hasObject(By.text("性能测试数据已就绪")), 10_000)) { "Fixture did not open: $launch" }
    device.pressHome()
}
internal fun MacrobenchmarkScope.journey() {
    val ui = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    check(ui.wait(Until.hasObject(By.desc(Pattern.compile("添加待办|Add task"))), 5_000))
    ui.findObject(By.desc(Pattern.compile("添加待办|Add task"))).click()
    check(ui.wait(Until.hasObject(By.text(Pattern.compile("记下一件事|A new task"))), 5_000))
    ui.pressBack() // Keyboard.
    ui.waitForIdle()
    if (ui.hasObject(By.text(Pattern.compile("记下一件事|A new task")))) ui.pressBack() // Sheet.
    ui.waitForIdle()
    check(ui.wait(Until.gone(By.text(Pattern.compile("记下一件事|A new task"))), 5_000))
    repeat(3) { ui.swipe(ui.displayWidth / 2, ui.displayHeight * 4 / 5, ui.displayWidth / 2, ui.displayHeight / 3, 20) }
    repeat(3) { ui.swipe(ui.displayWidth / 2, ui.displayHeight / 3, ui.displayWidth / 2, ui.displayHeight * 4 / 5, 20) }
    // A visible text node can still be moving during the final fling. Settle the list
    // before resolving its click coordinates; otherwise the tap can miss the row.
    ui.waitForIdle()
    // Accessibility-idle alone can precede Compose's final fling placement. Observe
    // fresh bounds before one normal-duration tap; do not retry failed navigation.
    stableTask(ui).click(80)
    if (!ui.wait(Until.hasObject(By.text(Pattern.compile("待办详情|Task details"))), 5_000)) {
        val evidence = File(InstrumentationRegistry.getInstrumentation().context.getExternalFilesDir(null), "journey-failure").apply { mkdirs() }
        ui.takeScreenshot(File(evidence, "screen.png"))
        ui.dumpWindowHierarchy(File(evidence, "window.xml"))
        error("Detail did not open; retained synthetic journey screenshot and hierarchy at $evidence")
    }
    ui.pressBack()
    ui.waitForIdle()
}
