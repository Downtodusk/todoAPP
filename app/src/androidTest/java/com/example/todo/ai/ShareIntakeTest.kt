package com.example.todo.ai

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.todo.App
import com.example.todo.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class ShareIntakeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun sharedTextSurvivesUnconfiguredSettingsRedirectWithoutBeingSaved() {
        val activity = compose.activity
        val original = Intent(activity.intent)
        val app = (activity.application as App).container
        val configured = runBlocking { app.settings.ai.first().configured }
        val text = "分享入口合成通知_${System.nanoTime()}"
        try {
            compose.runOnUiThread {
                activity.startActivity(Intent(activity, MainActivity::class.java).apply {
                    action = Intent.ACTION_SEND; type = "text/plain"
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(Intent.EXTRA_TEXT, text)
                })
            }
            if (!configured) {
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Base URL").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithContentDescription("返回").performClick()
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("notice-input").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("notice-input").assertTextContains(text)
            runBlocking { org.junit.Assert.assertFalse(app.todos.all.first().any { it.title == text || it.sourceText == text }) }
        } finally { compose.runOnUiThread { activity.intent = original } }
    }
}
