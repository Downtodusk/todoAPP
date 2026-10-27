package com.example.todo.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.todo.AppContainer

@Composable
fun ReminderPermissionHost(app: AppContainer, request: Int, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    var exactDialog by remember { mutableStateOf(false) }
    val currentMessage by rememberUpdatedState(onMessage)
    fun checkExact() { if (!app.alarms.permitted()) exactDialog = true else app.refreshReminders() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) currentMessage("未开启通知权限，提醒不会生效")
        checkExact()
    }
    LaunchedEffect(request) {
        if (request > 0) {
            if (!app.notifications.permitted()) {
                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else currentMessage("未开启通知权限，提醒不会生效")
            }
            if (!app.alarms.permitted()) exactDialog = true
            else if (app.notifications.permitted()) {
                if (!app.notifications.channelEnabled()) currentMessage("提醒渠道已关闭，请在系统设置中开启")
                app.refreshReminders()
            }
        }
    }
    if (exactDialog) AlertDialog(onDismissRequest = { exactDialog = false }, title = { Text("允许准时提醒") },
        text = { Text("待办已保存。请在系统设置中允许精确闹钟，否则无法准时提醒。") },
        confirmButton = { TextButton(onClick = {
            exactDialog = false
            if (Build.VERSION.SDK_INT >= 31) openSettings(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }) { Text("去设置") } }, dismissButton = { TextButton(onClick = { exactDialog = false }) { Text("稍后") } })
}
