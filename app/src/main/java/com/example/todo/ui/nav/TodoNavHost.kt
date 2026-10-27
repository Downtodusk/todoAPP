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
import com.example.todo.ui.settings.SettingsScreen
import com.example.todo.ui.today.*
import java.time.*
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoNavHost(container: AppContainer, settings: UserSettings) {
    val nav = rememberNavController()
    val vm: TodayViewModel = viewModel(factory = viewModelFactory { initializer { TodayViewModel(container.todos) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
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
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { context.unregisterReceiver(receiver); lifecycleOwner.lifecycle.removeObserver(observer) }
    }
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
                LaunchedEffect(edit.saved) { if (edit.saved) nav.popBackStack() }
                Scaffold(topBar = { TopAppBar(title = { Text("待办详情") }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(LineIcons.Back, "返回") } },
                    actions = { IconButton(onClick = { vm.delete(id); nav.popBackStack() }) { Icon(LineIcons.Delete, "删除") } }) }) { padding ->
                    when {
                        edit.loading -> Text("正在读取…", Modifier.padding(padding).padding(20.dp))
                        edit.missing -> Text(edit.error ?: "待办已删除", Modifier.padding(padding).padding(20.dp))
                        else -> TodoEditor(edit.snapshot?.todo, edit.saving, edit.error, editor::save, {}, Modifier.padding(padding))
                    }
                }
            }
            composable("settings") { SettingsScreen(settings, container.settings, { nav.popBackStack() }, { nav.navigate("ai") }) }
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
            LaunchedEffect(edit.saved) { if (edit.saved) add = false }
            TodoEditor(null, edit.saving, edit.error, editor::save, { add = false; nav.navigate("ai") }, quick = true)
        }
    }
}
