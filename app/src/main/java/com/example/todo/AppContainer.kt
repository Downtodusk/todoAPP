package com.example.todo

import android.content.Context
import com.example.todo.data.local.TodoDatabase
import com.example.todo.data.repo.TodoRepository
import com.example.todo.data.settings.SettingsStore

class AppContainer(context: Context) {
    val database by lazy { TodoDatabase.create(context) }
    val todos by lazy { TodoRepository(database) }
    val settings = SettingsStore(context)
}
