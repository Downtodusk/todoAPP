package com.example.todo.ui.settings

import com.example.todo.R
import com.example.todo.i18n.t
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
        if (!granted) currentMessage(t(R.string.notifications_are_off_reminders_will_not_appear))
        checkExact()
    }
    LaunchedEffect(request) {
        if (request > 0) {
            if (!app.notifications.permitted()) {
                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else currentMessage(t(R.string.notifications_are_off_reminders_will_not_appear))
            }
            if (!app.alarms.permitted()) exactDialog = true
            else if (app.notifications.permitted()) {
                if (!app.notifications.channelEnabled()) currentMessage(t(R.string.the_reminder_channel_is_off_enable_it_in_system_settings))
                app.refreshReminders()
            }
        }
    }
    if (exactDialog) AlertDialog(onDismissRequest = { exactDialog = false }, title = { Text(t(R.string.allow_on_time_reminders)) },
        text = { Text(t(R.string.your_task_is_saved_allow_exact_alarms_in_system_settings_to_recei)) },
        confirmButton = { TextButton(onClick = {
            exactDialog = false
            if (Build.VERSION.SDK_INT >= 31) openSettings(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }) { Text(t(R.string.open_settings)) } }, dismissButton = { TextButton(onClick = { exactDialog = false }) { Text(t(R.string.later)) } })
}
