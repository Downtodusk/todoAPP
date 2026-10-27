package com.example.todo.ui.settings

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.todo.AppContainer
import com.example.todo.data.local.*
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
    Scaffold(topBar = { TopAppBar(title = { Text("提醒自检") }, navigationIcon = { IconButton(onClick = onBack) { Icon(LineIcons.Back, "返回") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            fun status(ok: Boolean) = if (ok) "已开启" else "未开启"
            Text("通知权限：${status(notifications)}")
            Text("精确闹钟：${status(exact)}")
            Text("通知渠道：${if (channel) "可发送" else "已关闭"}")
            val channelInfo = context.getSystemService(NotificationManager::class.java).getNotificationChannel(NotificationHelper.Channel)
            Text("提示方式：${if (channelInfo?.sound == null) "无铃声" else "有铃声"} · ${if (channelInfo?.shouldVibrate() == true) "有振动" else "无振动"}", style = MaterialTheme.typography.bodySmall)
            Text("电池优化豁免：${status(battery)}")
            TextButton(onClick = requestPermission) { Text("检查并申请提醒权限") }
            TextButton(onClick = { openSettings(context, Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.Channel)) }) { Text("通知铃声与渠道设置") }
            TextButton(onClick = { openSettings(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))) }) { Text("允许后台提醒（电池优化）") }
            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        val time = System.currentTimeMillis() + 10_000
                        app.todos.save(TodoEntity(title = "测试提醒", note = "提醒自检创建，测试完成后可删除。", dueAt = time), listOf(ReminderEntity(todoId = 0, triggerAt = time)))
                        message = if (notifications && exact && channel) "已创建测试待办，10 秒后提醒" else "已创建测试待办，请先开启提醒权限与通知渠道"
                        if (!notifications || !exact) requestPermission()
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { message = "测试提醒创建失败，请重试" }
                    finally { busy = false }
                }
            }) { Text("发送测试提醒（10秒）") }
            TextButton(onClick = { app.refreshReminders(); tick++; message = "已请求重新注册提醒" }) { Text("重新注册提醒") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            HorizontalDivider()
            Text("${Build.MANUFACTURER} 后台运行设置", style = MaterialTheme.typography.titleMedium)
            val manufacturer = Build.MANUFACTURER.lowercase(java.util.Locale.ROOT)
            Text(when (manufacturer) {
                "xiaomi" -> "在应用管理中开启自启动，将省电策略设为无限制，并在最近任务中锁定待办。"
                "huawei", "honor" -> "在应用启动管理中关闭自动管理，允许自启动、关联启动和后台活动，并锁定最近任务。"
                "oppo", "realme", "vivo" -> "在应用电池管理中允许后台运行与自启动，关闭对待办的后台耗电限制，并锁定最近任务。"
                "samsung" -> "在电池设置中将待办加入从不休眠的应用，允许后台活动。"
                else -> "在应用电池设置中选择不受限制；系统提供自启动或后台运行选项时允许待办运行。"
            })
            TextButton(onClick = {
                val intent = vendorComponents[manufacturer].orEmpty().mapNotNull(ComponentName::unflattenFromString)
                    .map { Intent().setComponent(it) }.firstOrNull { it.resolveActivity(context.packageManager) != null }
                openSettings(context, intent ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }) { Text("打开厂商后台设置") }
            Text("系统“强行停止”会阻止提醒，需再次打开应用恢复。划掉最近任务与强行停止不同；厂商策略仍需在你的手机上验证。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
