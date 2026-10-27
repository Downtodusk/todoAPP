package com.example.todo

import android.os.Bundle
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.todo.data.settings.UserSettings
import com.example.todo.ui.nav.TodoNavHost
import com.example.todo.ui.theme.TodoTheme

data class SharedNotice(val text: String, val token: Long = System.nanoTime())

class MainActivity : ComponentActivity() {
    private val sharedNotice = MutableStateFlow<SharedNotice?>(null)
    private val openTodo = MutableStateFlow<Long?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as App).container
        if (savedInstanceState == null) readIntent(intent)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(UserSettings())
            val todoId by openTodo.collectAsStateWithLifecycle()
            val shared by sharedNotice.collectAsStateWithLifecycle()
            TodoTheme(settings) { TodoNavHost(container, settings, todoId,
                onIntentConsumed = { openTodo.value = null }, sharedNotice = shared,
                onShareConsumed = { sharedNotice.value = null }) }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readIntent(intent) }
    private fun readIntent(intent: Intent?) {
        openTodo.value = intent?.getLongExtra("todoId", -1)?.takeIf { it > 0 }
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let { sharedNotice.value = SharedNotice(it) }
        }
    }
}
