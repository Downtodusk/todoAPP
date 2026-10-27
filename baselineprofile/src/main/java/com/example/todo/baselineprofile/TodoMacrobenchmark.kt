package com.example.todo.baselineprofile

import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoMacrobenchmark {
    @get:Rule val rule = MacrobenchmarkRule()
    @Before fun prepare() = seed()
    @Test fun coldStartup() = rule.measureRepeated(Target, listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
        startupMode = StartupMode.COLD, iterations = 10, setupBlock = { pressHome() }) { startActivityAndWait() }
    @Test fun interactions() = rule.measureRepeated(Target, listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
        iterations = 5, setupBlock = { pressHome(); startActivityAndWait() }) { journey() }
}
