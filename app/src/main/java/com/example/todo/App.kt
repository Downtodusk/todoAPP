package com.example.todo

import android.app.Application

class App : Application() {
    val container by lazy { AppContainer(this) }
    override fun onCreate() {
        super.onCreate()
        container.notifications.createChannel()
        container.refreshReminders()
    }
}
