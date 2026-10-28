package com.example.todo.ui

import android.app.NotificationManager
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.example.todo.App
import com.example.todo.MainActivity
import com.example.todo.data.local.ReminderEntity
import com.example.todo.data.local.TodoEntity
import com.example.todo.data.settings.ReminderMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*

class TodoFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val title = "UI测试_${System.nanoTime()}"
    private val fixtureIds = mutableListOf<Long>()
    @After fun cleanup() = runBlocking {
        val app = (compose.activity.application as App).container
        val repository = app.todos
        fixtureIds.forEach { repository.delete(it); app.notifications.cancel(it) }
        repository.all.first().filter { it.title == title }.forEach { repository.delete(it.id) }
    }
    @Test fun distinctNotificationsOpenTheirOwnDetailsWhileAppIsRunning() {
        val activity = compose.activity
        val launchIntent = Intent(activity.intent)
        val app = (compose.activity.application as App).container
        val manager = compose.activity.getSystemService(NotificationManager::class.java)
        val titles = listOf("${title}_第一项", "${title}_第二项")
        runBlocking {
            titles.forEach { itemTitle ->
                val trigger = System.currentTimeMillis() - 1_000
                val id = app.todos.save(TodoEntity(title = itemTitle, dueAt = trigger),
                    listOf(ReminderEntity(todoId = 0, triggerAt = trigger, fired = true)))
                fixtureIds.add(id)
                val snapshot = app.todos.get(id)!!
                Assert.assertTrue(app.notifications.show(snapshot.todo, snapshot.reminders.single()))
            }
        }
        compose.waitUntil(5_000) { fixtureIds.all { id -> manager.activeNotifications.any { it.tag == "todo-$id" } } }
        // Both use request code 0: opening the first after posting the second detects identity collisions.
        try {
            fixtureIds.zip(titles).forEach { (id, itemTitle) ->
                manager.activeNotifications.single { it.tag == "todo-$id" }.notification.contentIntent.send()
                compose.waitUntil(5_000) {
                    compose.onAllNodes(hasSetTextAction() and hasText(itemTitle)).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("待办详情").assertIsDisplayed()
                compose.onNode(hasSetTextAction() and hasText(itemTitle)).assertIsDisplayed()
            }
        } finally {
            // MainActivity updates its intent on delivery; restore the scenario's launch identity for teardown.
            compose.runOnUiThread { activity.intent = launchIntent }
        }
    }
    @Test fun addCompleteUndo() {
        compose.onNodeWithContentDescription("添加待办").performClick()
        compose.onNodeWithText("标题").performTextInput(title)
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("完成 $title").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("撤销").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("撤销").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("完成 $title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).assertIsDisplayed()
    }
    @Test fun swipeRevealsActionsWithoutMutatingUntilConfirmation() {
        val app = (compose.activity.application as App).container
        val id = runBlocking { app.todos.save(TodoEntity(title = title), emptyList()) }
        fixtureIds.add(id)
        val tag = "todo-row-$id"
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performScrollTo().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        Assert.assertEquals(0, runBlocking { app.todos.get(id)!!.todo.status })
        compose.onNodeWithContentDescription("确认完成 $title").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { runBlocking { app.todos.get(id)?.todo?.status == 1 } }
        compose.onNodeWithText("撤销").performClick()
        compose.waitUntil(5_000) { runBlocking { app.todos.get(id)?.todo?.status == 0 } }
        compose.onNodeWithTag(tag).performScrollTo().performTouchInput { swipeRight() }
        compose.waitForIdle()
        Assert.assertNotNull(runBlocking { app.todos.get(id) })
        // Tapping the body closes the revealed menu without opening detail or deleting.
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithContentDescription("确认删除 $title").assertDoesNotExist()
        compose.onNodeWithTag(tag).performTouchInput { swipeRight() }
        compose.onNodeWithContentDescription("确认删除 $title").performClick()
        compose.waitUntil(5_000) { runBlocking { app.todos.get(id) == null } }
        compose.onNodeWithText("撤销").performClick()
        compose.waitUntil(5_000) { runBlocking { app.todos.get(id)?.todo?.title == title } }
        compose.onNodeWithText(title).assertIsDisplayed()
    }
    @Test fun reminderModePersistsAcrossNavigation() {
        val app = (compose.activity.application as App).container
        val original = runBlocking { app.settings.settings.first().reminderMode }
        try {
            compose.onNodeWithContentDescription("设置").performClick()
            compose.onNodeWithText("提醒自检").performScrollTo().performClick()
            compose.onNodeWithContentDescription("系统闹钟提醒").performClick()
            compose.waitUntil(5_000) { runBlocking { app.settings.settings.first().reminderMode == ReminderMode.ALARM_CLOCK } }
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithText("提醒自检").performScrollTo().performClick()
            compose.onNodeWithContentDescription("系统闹钟提醒").assertIsSelected()
            compose.onNodeWithContentDescription("普通提醒").performClick()
            compose.waitUntil(5_000) { runBlocking { app.settings.settings.first().reminderMode == ReminderMode.STANDARD } }
            compose.onNodeWithContentDescription("普通提醒").assertIsSelected()
        } finally {
            runBlocking { app.settings.reminderMode(original) }
            app.refreshReminders()
        }
    }
}
