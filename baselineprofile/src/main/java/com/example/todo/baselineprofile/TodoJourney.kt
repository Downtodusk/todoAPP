package com.example.todo.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import java.util.regex.Pattern

internal const val Target = "com.example.todo.benchmark"
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
    val row = ui.wait(Until.findObject(By.text(Pattern.compile("性能样例 [0-9]{2}"))), 5_000)
    checkNotNull(row).click()
    check(ui.wait(Until.hasObject(By.text(Pattern.compile("待办详情|Task details"))), 5_000))
    ui.pressBack()
    ui.waitForIdle()
}
