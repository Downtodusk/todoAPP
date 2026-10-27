package com.example.todo.ai

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.todo.data.local.TodoDatabase
import com.example.todo.data.repo.TodoRepository
import com.example.todo.data.settings.*
import com.example.todo.ui.ai.*
import com.example.todo.ui.theme.TodoTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class AiImportFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: TodoDatabase
    private lateinit var importer: AiImportViewModel
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, TodoDatabase::class.java).build()
    }
    @After fun cleanup() { if (::importer.isInitialized) compose.runOnIdle { importer.cancel() }; db.close() }
    private fun show(client: LlmClient) {
        importer = AiImportViewModel(NoticeParseRepository({ client }), TodoRepository(db), SettingsStore(ApplicationProvider.getApplicationContext()))
        compose.setContent {
            val state by importer.state.collectAsState()
            TodoTheme(UserSettings(reduceMotion = true)) {
                AiImportScreen(state, AiConfiguration(baseUrl = "https://fixture.example", model = "fake", hasKey = true, consentGranted = true),
                    importer::input, importer::recognize, importer::cancel, importer::update, importer::addSelected,
                    onConsent = {}, onSettings = {}, onManual = {}, onBack = {})
            }
        }
    }
    @Test fun fakeModelPreviewRequiresConfirmationAndEditedTitleIsSaved() {
        show(LlmClient { _, _ -> Result.success("""{"todos":[{"title":"模型标题","dueAt":null}]}""") })
        compose.onNodeWithTag("notice-input").performTextInput("测试通知")
        compose.onNodeWithText("识别通知").performClick()
        compose.waitUntil(5_000) { importer.state.value.parsed }
        runBlocking { assertTrue(db.todos().observeAll().first().isEmpty()) }
        compose.onNodeWithTag("draft-0").performScrollTo()
        compose.onNode(hasSetTextAction() and hasText("模型标题")).performTextReplacement("修改后的标题")
        compose.onNodeWithTag("import-confirm").performClick()
        compose.waitUntil(5_000) { importer.state.value.added == 1 }
        runBlocking { assertEquals("修改后的标题", db.todos().observeAll().first().single().title) }
    }
    @Test fun fakeModelFailureOffersExplicitLocalFallbackWithoutSaving() {
        show(LlmClient { _, _ -> Result.failure(AiException(AiFailure.NETWORK)) })
        compose.onNodeWithTag("notice-input").performTextInput("明天下午三点提交报告")
        compose.onNodeWithText("识别通知").performClick()
        compose.waitUntil(5_000) { importer.state.value.error != null }
        assertFalse(importer.state.value.parsed)
        compose.onNodeWithText("使用本地规则").performScrollTo().performClick()
        compose.waitUntil(5_000) { importer.state.value.parsed }
        assertTrue(importer.state.value.local)
        assertEquals(15, importer.state.value.drafts.single().due!!.hour)
        runBlocking { assertTrue(db.todos().observeAll().first().isEmpty()) }
    }
}
