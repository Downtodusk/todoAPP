package com.example.todo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.todo.App
import com.example.todo.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*

class TodoFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val title = "UI测试_${System.nanoTime()}"
    @After fun cleanup() = runBlocking {
        val repository = (compose.activity.application as App).container.todos
        repository.all.first().filter { it.title == title }.forEach { repository.delete(it.id) }
    }
    @Test fun addCompleteUndo() {
        compose.onNodeWithContentDescription("添加待办").performClick()
        compose.onNodeWithText("标题").performTextInput(title)
        compose.onNodeWithText("保存", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("完成 $title").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("撤销").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("撤销").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("完成 $title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).assertIsDisplayed()
    }
}
