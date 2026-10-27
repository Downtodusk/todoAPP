package com.example.todo.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*

internal const val Target = "com.example.todo.benchmark"
internal fun seed() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    device.executeShellCommand("am start -W -n $Target/com.example.todo.benchmark.BenchmarkSeedActivity")
    check(device.wait(Until.hasObject(By.text("性能测试数据已就绪")), 10_000))
    device.pressHome()
}
internal fun MacrobenchmarkScope.journey() {
    val ui = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    check(ui.wait(Until.hasObject(By.desc("添加待办")), 5_000))
    ui.findObject(By.desc("添加待办")).click()
    check(ui.wait(Until.hasObject(By.text("记下一件事")), 5_000))
    ui.pressBack() // Keyboard.
    ui.waitForIdle()
    if (ui.hasObject(By.text("记下一件事"))) ui.pressBack() // Sheet.
    ui.waitForIdle()
    check(ui.wait(Until.gone(By.text("记下一件事")), 5_000))
    repeat(3) { ui.swipe(ui.displayWidth / 2, ui.displayHeight * 4 / 5, ui.displayWidth / 2, ui.displayHeight / 3, 20) }
    repeat(3) { ui.swipe(ui.displayWidth / 2, ui.displayHeight / 3, ui.displayWidth / 2, ui.displayHeight * 4 / 5, 20) }
    val row = ui.wait(Until.findObject(By.text("性能样例 01")), 5_000)
    checkNotNull(row).click()
    check(ui.wait(Until.hasObject(By.text("待办详情")), 5_000))
    ui.pressBack()
    ui.waitForIdle()
}
