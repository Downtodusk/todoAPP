package com.example.todo

import android.app.Application

class App : Application() {
    val container by lazy { AppContainer(this) }
    override fun onCreate() {
        super.onCreate()
        com.example.todo.i18n.AppText.refresh(this)
        container.crashLogs.install()
        container.notifications.createChannel()
        container.refreshReminders()
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        com.example.todo.i18n.AppText.refresh(this)
        container.notifications.createChannel()
    }
}
