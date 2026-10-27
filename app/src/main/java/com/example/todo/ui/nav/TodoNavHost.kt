package com.example.todo.ui.nav

import android.content.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.todo.AppContainer
import com.example.todo.data.settings.UserSettings
import com.example.todo.ui.add.*
import com.example.todo.ui.components.LineIcons
import com.example.todo.ui.settings.*
import com.example.todo.ui.today.*
import java.time.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoNavHost(container: AppContainer, settings: UserSettings, openTodoId: Long? = null, onIntentConsumed: () -> Unit = {}) {
    val nav = rememberNavController()
    val vm: TodayViewModel = viewModel(factory = viewModelFactory { initializer { TodayViewModel(container.todos) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var permissionRequest by remember { mutableIntStateOf(0) }
    var add by rememberSaveable { mutableStateOf(false) }
    var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
    var today by remember { mutableStateOf(LocalDate.now(zone)) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(context, lifecycleOwner) {
        fun refresh() { zone = ZoneId.systemDefault(); today = LocalDate.now(zone) }
        val receiver = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) { refresh() } }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { refresh(); container.refreshReminders() } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { context.unregisterReceiver(receiver); lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(openTodoId) { if (openTodoId != null) { nav.navigate("detail/$openTodoId") { launchSingleTop = true }; onIntentConsumed() } }
    ReminderPermissionHost(container, permissionRequest) { message -> scope.launch {
        if (snackbar.showSnackbar(message, "去设置", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) nav.navigate("reminder")
    } }
    val reminderIssue by container.reminderIssue.collectAsStateWithLifecycle()
    LaunchedEffect(reminderIssue) { reminderIssue?.let { snackbar.showSnackbar(it, duration = SnackbarDuration.Long) } }
    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            val result = withTimeoutOrNull(5_000) { snackbar.showSnackbar(message.text, if (message.undo != null) "撤销" else null, duration = SnackbarDuration.Indefinite) }
            if (result == SnackbarResult.ActionPerformed) message.undo?.let(vm::undo)
        }
    }
    Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = "today") {
            listOf("today", "all").forEach { route -> composable(route) {
                TodayScreen(state, today, zone, route == "all", onDetail = { nav.navigate("detail/$it") }, onComplete = vm::complete,
                    onAdd = { add = true }, onAll = { nav.navigate("all") }, onSettings = { nav.navigate("settings") }, onBack = { nav.popBackStack() })
            } }
            composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments!!.getLong("id")
                val editor: AddViewModel = viewModel(factory = viewModelFactory { initializer { AddViewModel(container.todos, id) } })
                val edit by editor.state.collectAsStateWithLifecycle()
                LaunchedEffect(edit.saved) { if (edit.saved) { if (edit.savedWithReminder) permissionRequest++; nav.popBackStack() } }
                Scaffold(topBar = { TopAppBar(title = { Text("待办详情") }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(LineIcons.Back, "返回") } },
                    actions = {
                        TextButton(onClick = { scope.launch {
                            try { container.todos.copy(id)?.let { snackbar.showSnackbar("已复制") } }
                            catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (_: Exception) { snackbar.showSnackbar("复制失败，请重试") }
                        } }) { Text("复制") }
                        IconButton(onClick = { vm.delete(id); nav.popBackStack() }) { Icon(LineIcons.Delete, "删除") }
                    }) }) { padding ->
                    when {
                        edit.loading -> Text("正在读取…", Modifier.padding(padding).padding(20.dp))
                        edit.missing -> Text(edit.error ?: "待办已删除", Modifier.padding(padding).padding(20.dp))
                        else -> Column(Modifier.padding(padding)) {
                            if (edit.snapshot?.todo?.status == 0) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                listOf("推迟10分钟" to 600_000L, "1小时" to 3_600_000L, "明天此时" to -1L).forEach { (label, delay) -> TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            val trigger = if (delay < 0) ZonedDateTime.now().plusDays(1).toInstant().toEpochMilli() else System.currentTimeMillis() + delay
                                            if (container.todos.snooze(id, trigger)) { permissionRequest++; nav.popBackStack(); snackbar.showSnackbar("已推迟提醒") }
                                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                        catch (_: Exception) { snackbar.showSnackbar("推迟失败，请重试") }
                                    }
                                }) { Text(label) } }
                            }
                            TodoEditor(edit.snapshot?.todo, edit.saving, edit.error, editor::save, {},
                                reminders = edit.snapshot?.reminders.orEmpty(), defaultMinute = settings.allDayMinute)
                        }
                    }
                }
            }
            composable("settings") { SettingsScreen(settings, container.settings, { nav.popBackStack() }, { nav.navigate("ai") }, { nav.navigate("reminder") }) }
            composable("reminder") { ReminderCheckScreen(container, { nav.popBackStack() }, { permissionRequest++ }) }
            composable("ai") {
                Scaffold(topBar = { TopAppBar(title = { Text("通知导入") }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(LineIcons.Back, "返回") } }) }) { padding ->
                    Column(Modifier.padding(padding).padding(20.dp)) {
                        Text("AI 导入将在 M4 接入。当前可离线手动添加待办。")
                        TextButton(onClick = { nav.popBackStack(); add = true }) { Text("手动创建") }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).navigationBarsPadding())
    }
    if (add) {
        // A unique owner prevents a saved editor state from being reused on the next sheet opening.
        ModalBottomSheet(onDismissRequest = { add = false }, shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
            DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
            val editor: AddViewModel = viewModel(viewModelStoreOwner = owner, factory = viewModelFactory { initializer { AddViewModel(container.todos, null) } })
            val edit by editor.state.collectAsStateWithLifecycle()
            LaunchedEffect(edit.saved) { if (edit.saved) { if (edit.savedWithReminder) permissionRequest++; add = false } }
            TodoEditor(null, edit.saving, edit.error, editor::save, { add = false; nav.navigate("ai") }, quick = true, defaultMinute = settings.allDayMinute)
        }
    }
}
