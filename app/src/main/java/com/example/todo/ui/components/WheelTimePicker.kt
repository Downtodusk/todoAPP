package com.example.todo.ui.components

import com.example.todo.R
import com.example.todo.i18n.t
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

/** Native snapping wheels; text entry is blocked so tapping a number never opens the IME. */
@Composable
fun WheelTimePicker(hour: Int, minute: Int, onHour: (Int) -> Unit, onMinute: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        TimeWheel(hour, 23, t(R.string.hours), "time-hour", onHour)
        Text(":", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 12.dp))
        TimeWheel(minute, 59, t(R.string.minutes), "time-minute", onMinute)
    }
}

@Composable
private fun TimeWheel(value: Int, max: Int, label: String, tag: String, onChange: (Int) -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val ink = MaterialTheme.colorScheme.onSurface.toArgb()
    val latestChange by rememberUpdatedState(onChange)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        key(dark) {
            AndroidView(modifier = Modifier.width(88.dp).height(180.dp).testTag(tag).semantics {
                contentDescription = label
                stateDescription = "%02d".format(value)
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..max.toFloat(), max - 1)
                setProgress { latestChange(it.roundToInt().coerceIn(0, max)); true }
            }, factory = { context ->
                NumberPicker(ContextThemeWrapper(context, if (dark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert)).apply {
                    minValue = 0; maxValue = max
                    displayedValues = Array(max + 1) { "%02d".format(it) }
                    wrapSelectorWheel = true
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setOnValueChangedListener { _, _, selected -> latestChange(selected) }
                }
            }, update = { wheel ->
                if (wheel.value != value) wheel.value = value
                if (Build.VERSION.SDK_INT >= 29) {
                    wheel.textColor = ink
                    wheel.textSize = 24 * wheel.resources.displayMetrics.scaledDensity
                    wheel.selectionDividerHeight = (wheel.resources.displayMetrics.density).roundToInt()
                }
            })
        }
    }
}
