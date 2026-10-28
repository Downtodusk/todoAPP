package com.example.todo.baselineprofile

import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.*
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** Isolated list/detail comparison. Full AOT controls JIT variance; this is not a baseline-profile run. */
@RunWith(AndroidJUnit4::class)
class DetailTransitionBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()
    @Before fun prepare() = seed()
    @Test fun openAndReturn() = rule.measureRepeated(
        Target, listOf(FrameTimingMetric()), compilationMode = CompilationMode.Full(), iterations = 10,
        setupBlock = { pressHome(); startActivityAndWait() },
    ) {
        val ui = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        repeat(3) {
            checkNotNull(ui.wait(Until.findObject(By.text(Pattern.compile("性能样例 [0-9]{2}"))), 5_000)).click()
            check(ui.wait(Until.hasObject(By.text(Pattern.compile("待办详情|Task details"))), 5_000))
            ui.waitForIdle()
            ui.pressBack()
            check(ui.wait(Until.hasObject(By.text(Pattern.compile("性能样例 [0-9]{2}"))), 5_000))
            ui.waitForIdle()
        }
    }
}
