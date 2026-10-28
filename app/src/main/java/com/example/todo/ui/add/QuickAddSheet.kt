package com.example.todo.ui.add

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.geometry.Rect
import com.example.todo.ui.components.LocalTaskReturn
import androidx.compose.foundation.background
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todo.AppContainer
import com.example.todo.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.animation.core.MutableTransitionState
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.luminance
import com.example.todo.domain.calendarDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(container: AppContainer, defaultMinute: Int, onDismiss: () -> Unit,
    onSaved: (Boolean) -> Unit, onAi: () -> Unit, initialNote: String = "") {
    val reduced = LocalReducedMotion.current
    val todos by container.todos.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val markedDates = remember(todos) { todos.filter { it.status == 0 }.mapNotNull { it.calendarDate(ZoneId.systemDefault()) }.toSet() }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .93f).dp
    val scope = rememberCoroutineScope()
    val returning = LocalTaskReturn.current
    val cardLayer = rememberGraphicsLayer()
    var cardBounds by remember { mutableStateOf(Rect.Zero) }
    var requestClose by remember { mutableStateOf<() -> Unit>({}) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { value ->
        if (returning != null && value == SheetValue.Hidden) { requestClose(); false } else true
    })
    val visibility = remember { MutableTransitionState(false) }
    var dismissInput by remember { mutableStateOf<() -> Unit>({}) }
    var imeVisible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val editor: AddViewModel = viewModel(viewModelStoreOwner = owner,
        factory = viewModelFactory { initializer { AddViewModel(container.todos, null) } })
    val edit by editor.state.collectAsStateWithLifecycle()
    suspend fun close(afterClose: () -> Unit = {}) {
        if (closing) return
        closing = true
        // Finish the IME resize before moving the sheet; otherwise its anchors change mid-exit.
        dismissInput()
        if (imeVisible) withTimeoutOrNull(700) { snapshotFlow { imeVisible }.first { !it } }
        if (returning != null && cardLayer.size.width > 0) {
            try {
                val image = cardLayer.toImageBitmap()
                if (returning.start(image, cardBounds, edit.savedId,
                    navigate = { onDismiss(); true }, onFinished = afterClose)) return
                onDismiss(); afterClose(); return
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.w("TaskReturn", "Sheet capture unavailable", e); onDismiss(); afterClose(); return }
        }
        if (reduced) {
            visibility.targetState = false
            snapshotFlow { visibility.isIdle && !visibility.currentState }.first { it }
        } else sheet.hide()
        onDismiss()
        afterClose()
    }
    SideEffect { requestClose = { if (!closing) scope.launch { close() } } }
    LaunchedEffect(Unit) { visibility.targetState = true }
    LaunchedEffect(edit.saved) { if (edit.saved) close { onSaved(edit.savedWithReminder) } }
    val content: @Composable () -> Unit = {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val contentImeVisible = WindowInsets.isImeVisible
        SideEffect { imeVisible = contentImeVisible }
        DisposableEffect(focus, keyboard) {
            dismissInput = { focus.clearFocus(); keyboard?.hide() }
            onDispose { dismissInput = {} }
        }
        ApplySystemBarAppearance(MaterialTheme.colorScheme.surface.luminance() < .5f)
        BackHandler(enabled = !closing) { scope.launch { close() } }
        TodoEditor(null, edit.saving, edit.error, { todo, reminders -> if (!closing && !edit.saved) editor.save(todo, reminders) },
            { if (!closing) scope.launch { close(onAi) } }, quick = true, defaultMinute = defaultMinute, initialNote = initialNote, markedDates = markedDates, modifier = Modifier.heightIn(max = maxHeight))
    }
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    if (!reduced) {
        ModalBottomSheet(onDismissRequest = { if (returning != null) requestClose() else if (!closing) { closing = true; onDismiss() } },
            sheetState = sheet, shape = shape, containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp,
            dragHandle = null) {
            Column(Modifier.fillMaxWidth().onGloballyPositioned { cardBounds = Rect(it.positionInWindow(), it.size.toSize()) }
                .drawWithContent { cardLayer.record { this@drawWithContent.drawContent() }; drawLayer(cardLayer) }
                .background(MaterialTheme.colorScheme.surface), horizontalAlignment = Alignment.CenterHorizontally) {
                BottomSheetDefaults.DragHandle()
                content()
            }
        }
    } else {
        // Material 3 1.4 does not publicly expose sheet motion specs. Keep the same modal
        // editor with an opacity-only presentation instead of reaching into internal APIs.
        Dialog(onDismissRequest = { if (!closing) scope.launch { close() } },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()) {
                Box(Modifier.matchParentSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (!closing) scope.launch { close() }
                })
                AnimatedVisibility(visibleState = visibility, modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(tween(Motion.DurationShort)), exit = fadeOut(tween(Motion.DurationShort))) {
                    Surface(Modifier.fillMaxWidth().heightIn(max = maxHeight), shape = shape, color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) { content() }
                }
            }
        }
    }
}
