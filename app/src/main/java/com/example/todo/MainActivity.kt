package com.example.todo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.todo.data.settings.UserSettings
import com.example.todo.ui.nav.TodoNavHost
import com.example.todo.ui.theme.TodoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as App).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(UserSettings())
            TodoTheme(settings) { TodoNavHost(container, settings) }
        }
    }
}
