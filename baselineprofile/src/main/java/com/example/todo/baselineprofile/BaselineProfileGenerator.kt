package com.example.todo.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()
    @Before fun prepare() = seed()
    @Test fun startup() = rule.collect(Target, includeInStartupProfile = true) { pressHome(); startActivityAndWait() }
    @Test fun interactions() = rule.collect(Target) { pressHome(); startActivityAndWait(); journey() }
}
