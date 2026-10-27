package com.example.todo.ui.add

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.luminance
import com.example.todo.domain.calendarDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(container: AppContainer, defaultMinute: Int, onDismiss: () -> Unit,
    onSaved: (Boolean) -> Unit, onAi: () -> Unit, initialNote: String = "") {
    val reduced = LocalReducedMotion.current
    val todos by container.todos.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val markedDates = remember(todos) { todos.filter { it.status == 0 }.mapNotNull { it.calendarDate(ZoneId.systemDefault()) }.toSet() }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .93f).dp
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val editor: AddViewModel = viewModel(viewModelStoreOwner = owner,
        factory = viewModelFactory { initializer { AddViewModel(container.todos, null) } })
    val edit by editor.state.collectAsStateWithLifecycle()
    suspend fun close() {
        closing = true
        if (reduced) { visible = false; delay(Motion.DurationShort.toLong()) } else sheet.hide()
        onDismiss()
    }
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(edit.saved) { if (edit.saved) { close(); onSaved(edit.savedWithReminder) } }
    val content: @Composable () -> Unit = {
        ApplySystemBarAppearance(MaterialTheme.colorScheme.surface.luminance() < .5f)
        TodoEditor(null, edit.saving || closing, edit.error, editor::save,
            { if (!closing) scope.launch { close(); onAi() } }, quick = true, defaultMinute = defaultMinute, initialNote = initialNote, markedDates = markedDates, modifier = Modifier.heightIn(max = maxHeight))
    }
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    if (!reduced) {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, shape = shape, containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) { content() }
    } else {
        // Material 3 1.4 does not publicly expose sheet motion specs. Keep the same modal
        // editor with an opacity-only presentation instead of reaching into internal APIs.
        Dialog(onDismissRequest = { if (!closing) scope.launch { close() } },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()) {
                Box(Modifier.matchParentSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (!closing) scope.launch { close() }
                })
                AnimatedVisibility(visible, modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(tween(Motion.DurationShort)), exit = fadeOut(tween(Motion.DurationShort))) {
                    Surface(Modifier.fillMaxWidth().heightIn(max = maxHeight), shape = shape, color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) { content() }
                }
            }
        }
    }
}
