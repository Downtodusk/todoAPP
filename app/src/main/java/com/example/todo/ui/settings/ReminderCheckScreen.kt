package com.example.todo.ui.settings

import com.example.todo.R
import com.example.todo.i18n.t
import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.todo.AppContainer
import com.example.todo.data.local.*
import com.example.todo.data.settings.ReminderMode
import com.example.todo.data.settings.UserSettings
import com.example.todo.reminder.NotificationHelper
import com.example.todo.ui.components.LineIcons
import kotlinx.coroutines.launch

fun openSettings(context: Context, intent: Intent) {
    try { context.startActivity(intent) }
    catch (_: ActivityNotFoundException) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
    catch (_: SecurityException) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
}

private val vendorComponents = mapOf(
    "xiaomi" to listOf("com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity"),
    "huawei" to listOf("com.huawei.systemmanager/com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
    "honor" to listOf("com.hihonor.systemmanager/com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
    "oppo" to listOf("com.coloros.safecenter/com.coloros.safecenter.permission.startup.StartupAppListActivity"),
    "realme" to listOf("com.coloros.safecenter/com.coloros.safecenter.permission.startup.StartupAppListActivity"),
    "vivo" to listOf("com.vivo.permissionmanager/com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
    "samsung" to listOf("com.samsung.android.lool/com.samsung.android.sm.ui.battery.BatteryActivity"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderCheckScreen(app: AppContainer, onBack: () -> Unit, requestPermission: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var changingMode by remember { mutableStateOf(false) }
    val settings by app.settings.settings.collectAsStateWithLifecycle(UserSettings())
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notifications = remember(tick) { app.notifications.permitted() }
    val exact = remember(tick) { app.alarms.permitted() }
    val channel = remember(tick) { app.notifications.channelEnabled() }
    val battery = remember(tick) { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
    Scaffold(topBar = { TopAppBar(title = { Text(t(R.string.reminder_check)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(LineIcons.Back, t(R.string.back)) } }) }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(t(R.string.reminder_mode), style = MaterialTheme.typography.titleMedium)
            listOf(ReminderMode.STANDARD to t(R.string.standard_reminders), ReminderMode.ALARM_CLOCK to t(R.string.system_alarm_reminders)).forEach { (mode, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = settings.reminderMode == mode, enabled = !changingMode,
                        modifier = Modifier.semantics { contentDescription = label }, onClick = {
                        changingMode = true
                        scope.launch {
                            try { app.settings.reminderMode(mode); app.refreshReminders(); message = t(R.string.mode_saved_rescheduling_pending_reminders) }
                            catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (_: Exception) { message = t(R.string.could_not_save_reminder_mode_try_again) }
                            finally { changingMode = false }
                        }
                    })
                    Text(label)
                }
            }
            Text(t(R.string.standard_reminders_are_the_default_system_alarm_mode_may_help_on), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            fun status(ok: Boolean) = if (ok) t(R.string.on) else t(R.string.off)
            Text(t(R.string.notification_status, status(notifications)))
            Text(t(R.string.exact_alarm_status, status(exact)))
            Text(t(R.string.channel_status, status(channel)))
            val channelInfo = context.getSystemService(NotificationManager::class.java).getNotificationChannel(NotificationHelper.Channel)
            Text(t(R.string.alert_status, t(if (channelInfo?.sound == null) R.string.sound_off else R.string.sound_on), t(if (channelInfo?.shouldVibrate() == true) R.string.vibration_on else R.string.vibration_off)), style = MaterialTheme.typography.bodySmall)
            Text(t(R.string.battery_status, status(battery)))
            Text(t(R.string.manufacturer_background_restrictions_are_separate_also_check_the), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = requestPermission) { Text(t(R.string.check_reminder_permissions)) }
            TextButton(onClick = { openSettings(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text(t(R.string.lock_screen_notifications)) }
            TextButton(onClick = { openSettings(context, Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.Channel)) }) { Text(t(R.string.reminder_sound_and_channel)) }
            TextButton(onClick = { openSettings(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))) }) { Text(t(R.string.allow_background_reminders)) }
            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        val time = System.currentTimeMillis() + 10_000
                        app.todos.save(TodoEntity(title = t(R.string.test_reminder), note = t(R.string.created_by_reminder_check_you_can_delete_it_after_testing), dueAt = time), listOf(ReminderEntity(todoId = 0, triggerAt = time)))
                        message = if (notifications && exact && channel) t(R.string.test_task_created_reminder_in_10_seconds) else t(R.string.test_task_created_enable_reminder_permissions_and_the_notificatio)
                        if (!notifications || !exact) requestPermission()
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { message = t(R.string.could_not_create_test_reminder_try_again) }
                    finally { busy = false }
                }
            }) { Text(t(R.string.send_test_reminder_10_seconds)) }
            TextButton(onClick = { app.refreshReminders(); tick++; message = t(R.string.reminder_rescheduling_requested) }) { Text(t(R.string.reschedule_reminders)) }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            HorizontalDivider()
            Text(t(R.string.vendor_settings, Build.MANUFACTURER), style = MaterialTheme.typography.titleMedium)
            val manufacturer = Build.MANUFACTURER.lowercase(java.util.Locale.ROOT)
            Text(when (manufacturer) {
                "xiaomi" -> t(R.string.enable_auto_start_in_app_management_set_battery_usage_to_unrestri)
                "huawei", "honor" -> t(R.string.turn_off_automatic_app_launch_management_allow_auto_start_seconda)
                "oppo", "realme" -> t(R.string.open_app_info_battery_usage_and_allow_background_activity_then_se)
                "vivo" -> t(R.string.allow_background_activity_and_auto_start_remove_background_batter)
                "samsung" -> t(R.string.add_todo_to_never_sleeping_apps_in_battery_settings_and_allow_bac)
                else -> t(R.string.choose_unrestricted_in_app_battery_settings_allow_auto_start_or_b)
            })
            TextButton(onClick = {
                val intent = vendorComponents[manufacturer].orEmpty().mapNotNull(ComponentName::unflattenFromString)
                    .map { Intent().setComponent(it) }.firstOrNull { it.resolveActivity(context.packageManager) != null }
                openSettings(context, intent ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }) { Text(t(R.string.open_device_background_settings)) }
            Text(t(R.string.force_stop_blocks_reminders_until_you_open_todo_again_removing_it), style = MaterialTheme.typography.bodySmall)
        }
    }
}
