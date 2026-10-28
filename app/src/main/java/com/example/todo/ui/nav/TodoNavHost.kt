package com.example.todo.ui.nav

import com.example.todo.BuildConfig
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.example.todo.R
import com.example.todo.i18n.t
import android.content.*
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.example.todo.ui.ai.*
import com.example.todo.SharedNotice
import com.example.todo.domain.calendarDate
import kotlinx.coroutines.flow.first
import com.example.todo.ui.components.*
import com.example.todo.ui.theme.*
import com.example.todo.data.repo.TodoSnapshot
import kotlinx.coroutines.delay
import com.example.todo.ui.settings.*
import com.example.todo.ui.today.*
import java.time.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoNavHost(container: AppContainer, settings: UserSettings, openTodoId: Long? = null, onIntentConsumed: () -> Unit = {},
    sharedNotice: SharedNotice? = null, onShareConsumed: () -> Unit = {}) {
    val nav = rememberNavController()
    val vm: TodayViewModel = viewModel(factory = viewModelFactory { initializer { TodayViewModel(container.todos) } })
    val state by vm.state.collectAsStateWithLifecycle()
    ReportDrawnWhen { !state.loading }
    val snackbar = remember { SnackbarHostState() }
    val importer: AiImportViewModel = viewModel(factory = viewModelFactory { initializer { AiImportViewModel(container.noticeParser, container.todos, container.settings) } })
    val importState by importer.state.collectAsStateWithLifecycle()
    val aiConfig by container.settings.ai.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    val classic = BuildConfig.CLASSIC_DETAIL_MOTION
    val returning = remember { TaskReturnState() }
    val listLayer = rememberGraphicsLayer()
    var listPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var sharedTodoId by remember { mutableStateOf<Long?>(null) }
    var sharedOrigin by remember { mutableStateOf<String?>(null) }
    val snapshots = remember { mutableStateMapOf<Long, TodoSnapshot?>() }
    var opening by remember { mutableStateOf(false) }
    suspend fun openDetail(id: Long, origin: String? = null) {
        if (opening) return
        opening = true
        try {
            snapshots[id] = container.todos.get(id)
            sharedTodoId = id
            sharedOrigin = origin
            // Capture once, never read pixels during a gesture. A preview is decorative, not interactive.
            listPreview = if (!classic && !reduced && origin != null && listLayer.size.width > 0)
                try { listLayer.toImageBitmap() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { null } else null
            returning.cancel()
            nav.navigate("detail/$id")
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { snackbar.showSnackbar(t(R.string.could_not_load_try_again)) }
        finally { opening = false }
    }
    var permissionRequest by remember { mutableIntStateOf(0) }
    var add by rememberSaveable { mutableStateOf(false) }
    // Hold the visible list still until the add sheet has finished leaving.
    val visibleListState = if (add) remember { state } else state
    var manualNote by remember { mutableStateOf("") }
    LaunchedEffect(sharedNotice) {
        sharedNotice?.let { shared ->
            val configured = container.settings.ai.first().configured
            importer.input(shared.text)
            add = false
            nav.navigate("ai") { launchSingleTop = true }
            if (!configured) {
                nav.navigate("ai-settings")
                scope.launch { snackbar.showSnackbar(t(R.string.text_is_ready_configure_ai_or_go_back_to_create_a_task_manually)) }
            }
            // Consume only after the suspending configuration read; consuming cancels this effect.
            onShareConsumed()
        }
    }
    LaunchedEffect(importState.added) {
        if (importState.added > 0) {
            val count = importState.added
            if (importState.addedWithReminder) permissionRequest++
            nav.popBackStack("today", false)
            importer.consumed()
            scope.launch { snackbar.showSnackbar(t(R.string.added_task_count, count)) }
        }
    }
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
    LaunchedEffect(openTodoId) {
        if (openTodoId != null) {
            val current = nav.currentBackStackEntry
            // Different todos need distinct entries, including editor saveable state.
            if (current?.destination?.route != "detail/{id}" || current.arguments?.getLong("id") != openTodoId) {
                openDetail(openTodoId)
            }
            onIntentConsumed()
        }
    }
    ReminderPermissionHost(container, permissionRequest) { message -> scope.launch {
        if (snackbar.showSnackbar(message, t(R.string.open_settings), duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) nav.navigate("reminder")
    } }
    val reminderIssue by container.reminderIssue.collectAsStateWithLifecycle()
    LaunchedEffect(reminderIssue) { reminderIssue?.let { snackbar.showSnackbar(it, duration = SnackbarDuration.Long) } }
    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            val result = withTimeoutOrNull(5_000) { snackbar.showSnackbar(message.text, if (message.undo != null) t(R.string.undo) else null, duration = SnackbarDuration.Indefinite) }
            if (result == SnackbarResult.ActionPerformed) message.undo?.let(vm::undo)
        }
    }
    // Fixed-size pages: animate placement/alpha, never remeasure the editor into a list-row rectangle.
    val detailTravel = with(LocalDensity.current) { 24.dp.roundToPx() }
    val listTravel = with(LocalDensity.current) { 8.dp.roundToPx() }
    val markedDates = remember(state.todos, zone) { state.todos.filter { it.status == 0 }.mapNotNull { it.calendarDate(zone) }.toSet() }
    DetailSharedRoot(classic) {
    CompositionLocalProvider(LocalSharedTodoId provides sharedTodoId, LocalTaskReturn provides if (!classic && !reduced) returning else null) {
    Box(Modifier.fillMaxSize().onGloballyPositioned { returning.origin = it.positionInWindow(); returning.viewport = it.size.toSize() }.background(MaterialTheme.colorScheme.surface)) {
        NavHost(nav, startDestination = "today", modifier = Modifier.semantics { if (returning.request != null) hideFromAccessibility() },
            enterTransition = { fadeIn(tween(Motion.DurationMedium, easing = Motion.Enter)) },
            exitTransition = { fadeOut(tween(Motion.DurationShort, easing = Motion.Exit)) },
            popEnterTransition = { fadeIn(tween(Motion.DurationMedium)) },
            popExitTransition = { if (reduced) fadeOut(tween(Motion.DurationShort)) else
                fadeOut(tween(Motion.DurationMedium)) + scaleOut(tween(Motion.DurationMedium), targetScale = .94f) }) {
            listOf("today", "all").forEach { route -> composable(route,
                exitTransition = {
                    if (targetState.destination.route != "detail/{id}") null
                    else if (reduced) fadeOut(tween(Motion.DurationShort))
                    else if (classic) fadeOut(tween(Motion.DurationShort, easing = Motion.Exit))
                    else fadeOut(tween(Motion.DurationDetailHandoff)) + slideOutHorizontally(tween(Motion.DurationDetailEnter, easing = Motion.Enter)) { -listTravel }
                },
                popEnterTransition = {
                    if (initialState.destination.route != "detail/{id}") null
                    else if (reduced) fadeIn(tween(Motion.DurationShort))
                    else if (classic) fadeIn(tween(Motion.DurationMedium))
                    else EnterTransition.None
                },
            ) {
                CompositionLocalProvider(LocalTodoVisibilityScope provides this, LocalSharedTodoId provides if (sharedOrigin == route) sharedTodoId else null) {
                Box(if (classic) Modifier else Modifier.drawWithContent { listLayer.record { this@drawWithContent.drawContent() }; drawLayer(listLayer) }) {
                TodayScreen(visibleListState, today, zone, route == "all", onDetail = { scope.launch { openDetail(it, route) } }, onComplete = vm::complete, onDelete = vm::delete,
                    onAdd = { manualNote = ""; add = true }, onAll = { nav.navigate("all") }, onSettings = { nav.navigate("settings") }, onBack = { nav.popBackStack() }, adding = add)
                }
                }
            } }
            composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType }),
                enterTransition = {
                    if (reduced) fadeIn(tween(Motion.DurationShort))
                    else if (classic) fadeIn(tween(Motion.DurationMedium, easing = Motion.Enter))
                    else fadeIn(tween(Motion.DurationDetailEnter - Motion.DurationDetailHandoff, delayMillis = Motion.DurationDetailHandoff)) + slideInHorizontally(tween(Motion.DurationDetailEnter, easing = Motion.Enter)) { detailTravel }
                },
                popExitTransition = {
                    if (reduced) fadeOut(tween(Motion.DurationShort))
                    else if (classic) fadeOut(tween(Motion.DurationMedium)) + scaleOut(tween(Motion.DurationMedium), targetScale = .94f)
                    else ExitTransition.None
                },
            ) { entry ->
                val id = entry.arguments!!.getLong("id")
                val editor: AddViewModel = viewModel(factory = viewModelFactory { initializer { AddViewModel(container.todos, id, snapshots[id], snapshots.containsKey(id)) } })
                val edit by editor.state.collectAsStateWithLifecycle()
                var returningDeleted by remember { mutableStateOf(false) }
                CompositionLocalProvider(LocalTodoVisibilityScope provides this) {
                GmailDetailFrame(enabled = !classic && !reduced, preview = listPreview, targetId = { id.takeUnless { edit.missing || returningDeleted } }, onBack = {
                    // A notification may open another entry while this return is still animating.
                    nav.currentBackStackEntry == entry && nav.popBackStack()
                }) { close ->
                LaunchedEffect(edit.saved) { if (edit.saved) { if (edit.savedWithReminder) permissionRequest++; close() } }
                var detailMenu by remember { mutableStateOf(false) }
                Scaffold(modifier = Modifier.todoSharedBounds(id), topBar = { CenterAlignedTopAppBar(title = { Text(t(R.string.task_details), style = MaterialTheme.typography.titleLarge) }, navigationIcon = { IconButton(onClick = close) { Icon(LineIcons.Back, t(R.string.back)) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface), actions = {
                        Box {
                            IconButton(onClick = { detailMenu = true }) { Icon(LineIcons.More, t(R.string.task_actions)) }
                            DropdownMenu(expanded = detailMenu, onDismissRequest = { detailMenu = false }) {
                                DropdownMenuItem(text = { Text(t(R.string.duplicate)) }, onClick = { detailMenu = false; scope.launch {
                                    try { container.todos.copy(id)?.let { snackbar.showSnackbar(t(R.string.task_duplicated)) } }
                                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                    catch (_: Exception) { snackbar.showSnackbar(t(R.string.could_not_duplicate_try_again)) }
                                } })
                                DropdownMenuItem(text = { Text(t(R.string.delete)) }, onClick = { detailMenu = false; returningDeleted = true; close(); scope.launch { delay(if (reduced) Motion.DurationShort.toLong() else 550L); vm.delete(id) } })
                                if (edit.snapshot?.todo?.status == 0) {
                                    HorizontalDivider()
                                    listOf(t(R.string.snooze_10_minutes) to 600_000L, t(R.string.snooze_1_hour) to 3_600_000L, t(R.string.snooze_until_tomorrow) to -1L).forEach { (label, duration) ->
                                        DropdownMenuItem(text = { Text(label) }, onClick = { detailMenu = false; scope.launch {
                                            try {
                                                val trigger = if (duration < 0) ZonedDateTime.now().plusDays(1).toInstant().toEpochMilli() else System.currentTimeMillis() + duration
                                                if (container.todos.snooze(id, trigger)) { permissionRequest++; close(); snackbar.showSnackbar(t(R.string.reminder_snoozed)) }
                                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                            catch (_: Exception) { snackbar.showSnackbar(t(R.string.could_not_snooze_try_again)) }
                                        } })
                                    }
                                }
                            }
                        }
                    }) }) { padding ->
                    when {
                        edit.loading -> NoticeSkeleton(Modifier.padding(padding).padding(20.dp))
                        edit.missing -> Text(edit.error ?: t(R.string.task_deleted), Modifier.padding(padding).padding(20.dp))
                        else -> Column(Modifier.padding(padding)) {
                            TodoEditor(edit.snapshot?.todo, edit.saving, edit.error, editor::save, {},
                                reminders = edit.snapshot?.reminders.orEmpty(), defaultMinute = settings.allDayMinute,
                                markedDates = markedDates)
                        }
                    }
                }
                }
                }
            }
            composable("settings") { SettingsScreen(settings, container.settings, { nav.popBackStack() }, { nav.navigate("ai") }, { nav.navigate("reminder") }, { nav.navigate("ai-settings") }, container.crashLogs) }
            composable("reminder") { ReminderCheckScreen(container, { nav.popBackStack() }, { permissionRequest++ }) }
            composable("ai-settings") { AiSettingsScreen(container, { nav.popBackStack() }) }
            composable("ai") {
                DisposableEffect(importer) { onDispose { importer.cancel() } }
                AiImportScreen(importState, aiConfig, importer::input, importer::recognize, importer::cancel,
                    importer::update, importer::addSelected, onConsent = { scope.launch {
                        try { container.settings.aiConsent(true); importer.recognize() }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { snackbar.showSnackbar(t(R.string.could_not_save_consent_try_again)) }
                    } }, onSettings = { nav.navigate("ai-settings") },
                    onManual = { manualNote = it; add = true }, onBack = { importer.cancel(); nav.popBackStack() })
            }
        }
        TaskReturnOverlay(returning)
        SnackbarHost(snackbar, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).navigationBarsPadding())
    }
    if (add) QuickAddSheet(container, settings.allDayMinute, onDismiss = { add = false },
        onSaved = { withReminder -> if (withReminder) permissionRequest++ }, onAi = { nav.navigate("ai") { launchSingleTop = true } }, initialNote = manualNote)
    }
    }
}
